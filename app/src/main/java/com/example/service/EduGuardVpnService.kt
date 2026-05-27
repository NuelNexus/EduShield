package com.example.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.ActivityLog
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class EduGuardVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.example.service.START"
        const val ACTION_STOP = "com.example.service.STOP"
        
        @Volatile
        var isRunning = false
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var vpnInterface: ParcelFileDescriptor? = null
    
    private var vpnInput: FileInputStream? = null
    private var vpnOutput: FileOutputStream? = null
    private val vpnOutputLock = Any()

    // Real-time blocked domains cache
    private val blockedDomainCache = HashSet<String>()
    
    // Throttler for resolved domain name logging (avoids log floods)
    private val lastLoggedVisitedSiteTime = ConcurrentHashMap<String, Long>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        
        if (action == ACTION_START && !isRunning) {
            startVpn()
        }
        
        return START_STICKY
    }

    private fun startVpn() {
        isRunning = true
        
        // Start memory cache synchronization with Room database
        startBlockedListSync()

        // Start VPN interface worker thread
        serviceScope.launch(Dispatchers.IO) {
            try {
                setupVpnInterface()
                runVpnLoop()
            } catch (e: Exception) {
                Log.e("EduGuardVpn", "VPN Worker Thread Error", e)
                addSystemLog("Firewall Error", "Virtual interface driver crashed: ${e.localizedMessage}", "WARNING")
            } finally {
                stopVpn()
            }
        }
    }

    private fun stopVpn() {
        if (!isRunning) return
        isRunning = false
        
        serviceScope.cancel()
        
        synchronized(vpnOutputLock) {
            try { vpnInput?.close() } catch (e: Exception) {}
            try { vpnOutput?.close() } catch (e: Exception) {}
            try { vpnInterface?.close() } catch (e: Exception) {}
            
            vpnInput = null
            vpnOutput = null
            vpnInterface = null
        }
        
        addSystemLog("Firewall Standby", "Content filtering Web Filter VPN stopped by administrator.", "INFO")
        stopSelf()
    }

    private fun setupVpnInterface() {
        val builder = Builder()
        builder.setSession("EduGuard School Web Filter")
            .setMtu(1500)
            // Configure a local-loopback gateway and host
            .addAddress("10.0.0.2", 32)
            // Route DNS packets targetting 10.0.0.1 directly to the TUN interface
            .addRoute("10.0.0.1", 32)
            // Point the internal platform DNS server to 10.0.0.1 so all device-wide URL queries trigger via TUN!
            .addDnsServer("10.0.0.1")

        vpnInterface = builder.establish() ?: throw IllegalStateException("Could not establish virtual interface driver")
        
        val pfd = vpnInterface!!
        vpnInput = FileInputStream(pfd.fileDescriptor)
        vpnOutput = FileOutputStream(pfd.fileDescriptor)
        
        addSystemLog("Firewall Active", "Virtual DNS loopback VPN established. Real-time website blocking active.", "INFO")
    }

    private suspend fun runVpnLoop() {
        val input = vpnInput ?: return
        val packetBuffer = ByteArray(16384)
        
        while (isRunning) {
            try {
                val length = input.read(packetBuffer)
                if (length <= 0) {
                    delay(10)
                    continue
                }
                
                processPacket(packetBuffer, length)
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                if (!isRunning) break
            }
        }
    }

    private fun processPacket(packet: ByteArray, length: Int) {
        // Validate IPv4
        if ((packet[0].toInt() ushr 4) != 4) return
        
        // Identify UDP Protocol (17)
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return
        
        // Extract IP header length (IHL)
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl + 8 > length) return // Minimum size for IP + UDP header
        
        // Extract original ports
        val srcPort = ((packet[ihl].toInt() and 0xFF) shl 8) or (packet[ihl + 1].toInt() and 0xFF)
        val destPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)
        val udpLength = ((packet[ihl + 4].toInt() and 0xFF) shl 8) or (packet[ihl + 5].toInt() and 0xFF)
        
        // We only intercept outgoing DNS request packets directed to our loopback server (port 53)
        if (destPort != 53) return
        
        val dnsStart = ihl + 8
        val dnsLength = udpLength - 8
        if (dnsStart + dnsLength > length || dnsLength < 12) return

        // Extract domain name from DNS packet
        val requestedDomain = parseDnsDomain(packet, dnsStart, dnsLength) ?: return
        
        // Check DNS domain blacklist filter
        val isBlocked = verifyIfBlocked(requestedDomain)
        
        val originalIdentification = Pair(packet[4], packet[5])

        if (isBlocked) {
            // Drop client request and build NXDOMAIN (Name Error / Site Not Found) response
            val localResponse = buildNxDomainResponse(packet, srcPort, destPort, dnsStart, dnsLength, originalIdentification)
            
            synchronized(vpnOutputLock) {
                try {
                    vpnOutput?.write(localResponse)
                    vpnOutput?.flush()
                } catch (e: Exception) {
                    // Fail gracefully
                }
            }
            
            // Log block event in background
            serviceScope.launch {
                addBlockLog(requestedDomain)
            }
        } else {
            // Allowed - forward DNS request to public DNS server (Google DNS 8.8.8.8) in background
            val dnsPayload = ByteArray(dnsLength)
            System.arraycopy(packet, dnsStart, dnsPayload, 0, dnsLength)
            
            forwardDnsRequest(dnsPayload, srcPort, destPort, originalIdentification)
            
            // Log visited site with smart duplicates prevention
            serviceScope.launch {
                addVisitedLog(requestedDomain)
            }
        }
    }

    private fun parseDnsDomain(packet: ByteArray, dnsStart: Int, dnsLength: Int): String? {
        var index = dnsStart + 12 // Skip the 12-byte DNS headers
        val sb = StringBuilder()
        try {
            while (index < dnsStart + dnsLength) {
                val labelLength = packet[index].toInt() and 0xFF
                if (labelLength == 0) break
                if (labelLength > 63) return null // Compression pointers or malformed packet
                index++
                if (index + labelLength > dnsStart + dnsLength) return null
                if (sb.isNotEmpty()) {
                    sb.append(".")
                }
                sb.append(String(packet, index, labelLength, Charsets.US_ASCII))
                index += labelLength
            }
            val domain = sb.toString().trim().lowercase()
            return if (domain.isNotEmpty() && domain.contains(".")) domain else null
        } catch (e: Exception) {
            return null
        }
    }

    private fun verifyIfBlocked(domain: String): Boolean {
        synchronized(blockedDomainCache) {
            // Direct matches or wildcard matches (e.g., block sub.facebook.com if facebook.com is blocked)
            return blockedDomainCache.any { blocked ->
                domain == blocked || domain.endsWith("." + blocked)
            }
        }
    }

    private fun buildNxDomainResponse(
        requestPacket: ByteArray,
        srcPort: Int,
        destPort: Int,
        dnsStart: Int,
        dnsLen: Int,
        origId: Pair<Byte, Byte>
    ): ByteArray {
        // Prepare DNS response payload (clone input request queries)
        val dnsResponse = ByteArray(dnsLen)
        System.arraycopy(requestPacket, dnsStart, dnsResponse, 0, dnsLen)
        
        // Override DNS header flags to Standard response, with Name Error RFC code (0x8183 / NXDOMAIN)
        dnsResponse[2] = 0x81.toByte()
        dnsResponse[3] = 0x83.toByte()
        
        // Ensure no actual answer records are falsely labeled as attached
        dnsResponse[6] = 0
        dnsResponse[7] = 0
        dnsResponse[8] = 0
        dnsResponse[9] = 0
        dnsResponse[10] = 0
        dnsResponse[11] = 0
        
        val totalLength = 20 + 8 + dnsResponse.size
        val replyBuffer = ByteArray(totalLength)
        
        // 1. IP Header Configuration
        replyBuffer[0] = 0x45.toByte() // Version 4, IHL 5 (20 bytes)
        replyBuffer[1] = 0x00.toByte()
        replyBuffer[2] = ((totalLength ushr 8) and 0xFF).toByte()
        replyBuffer[3] = (totalLength and 0xFF).toByte()
        replyBuffer[4] = origId.first
        replyBuffer[5] = origId.second
        replyBuffer[6] = 0x40.toByte() // Flags: Don't Fragment
        replyBuffer[7] = 0x00.toByte()
        replyBuffer[8] = 64.toByte() // TTL
        replyBuffer[9] = 17.toByte() // Protocol: UDP
        
        // Swap IPs: Source is DNS Loopback gateway (10.0.0.1), Target is Client (10.0.0.2)
        replyBuffer[12] = 10
        replyBuffer[13] = 0
        replyBuffer[14] = 0
        replyBuffer[15] = 1
        
        replyBuffer[16] = 10
        replyBuffer[17] = 0
        replyBuffer[18] = 0
        replyBuffer[19] = 2
        
        // Calculate dynamic Header Checksum
        val ipChecksum = computeIpChecksum(replyBuffer, 0, 20)
        replyBuffer[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
        replyBuffer[11] = (ipChecksum and 0xFF).toByte()
        
        // 2. UDP Header Configuration
        replyBuffer[20] = ((destPort ushr 8) and 0xFF).toByte() // Source port is client's target port (53)
        replyBuffer[21] = (destPort and 0xFF).toByte()
        replyBuffer[22] = ((srcPort ushr 8) and 0xFF).toByte() // Target port is original source
        replyBuffer[23] = (srcPort and 0xFF).toByte()
        val udpLength = 8 + dnsResponse.size
        replyBuffer[24] = ((udpLength ushr 8) and 0xFF).toByte()
        replyBuffer[25] = (udpLength and 0xFF).toByte()
        replyBuffer[26] = 0 // UDP checksum is optional in IPv4 and set to 0
        replyBuffer[27] = 0
        
        // 3. Attach payload
        System.arraycopy(dnsResponse, 0, replyBuffer, 28, dnsResponse.size)
        
        return replyBuffer
    }

    private fun forwardDnsRequest(
        payload: ByteArray,
        srcPort: Int,
        destPort: Int,
        origId: Pair<Byte, Byte>
    ) {
        serviceScope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.soTimeout = 4000
                
                // Route query upstream to a real Public DNS Server (Google DNS)
                val upstreamDns = InetAddress.getByName("8.8.8.8")
                val sendPacket = DatagramPacket(payload, payload.size, upstreamDns, 53)
                socket.send(sendPacket)
                
                val receiveBuffer = ByteArray(4096)
                val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                socket.receive(receivePacket)
                
                val responseLength = receivePacket.length
                val replyTotalLength = 20 + 8 + responseLength
                val replyBuffer = ByteArray(replyTotalLength)
                
                // IP Header
                replyBuffer[0] = 0x45.toByte()
                replyBuffer[2] = ((replyTotalLength ushr 8) and 0xFF).toByte()
                replyBuffer[3] = (replyTotalLength and 0xFF).toByte()
                replyBuffer[4] = origId.first
                replyBuffer[5] = origId.second
                replyBuffer[6] = 0x40.toByte()
                replyBuffer[8] = 64.toByte()
                replyBuffer[9] = 17.toByte()
                
                // IP: Source = 10.0.0.1, Destination = 10.0.0.2
                replyBuffer[12] = 10
                replyBuffer[13] = 0
                replyBuffer[14] = 0
                replyBuffer[15] = 1
                
                replyBuffer[16] = 10
                replyBuffer[17] = 0
                replyBuffer[18] = 0
                replyBuffer[19] = 2
                
                val ipChecksum = computeIpChecksum(replyBuffer, 0, 20)
                replyBuffer[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
                replyBuffer[11] = (ipChecksum and 0xFF).toByte()
                
                // UDP Header
                replyBuffer[20] = ((destPort ushr 8) and 0xFF).toByte() // 53
                replyBuffer[21] = (destPort and 0xFF).toByte()
                replyBuffer[22] = ((srcPort ushr 8) and 0xFF).toByte()
                replyBuffer[23] = (srcPort and 0xFF).toByte()
                val udpLength = 8 + responseLength
                replyBuffer[24] = ((udpLength ushr 8) and 0xFF).toByte()
                replyBuffer[25] = (udpLength and 0xFF).toByte()
                
                System.arraycopy(receiveBuffer, 0, replyBuffer, 28, responseLength)
                
                synchronized(vpnOutputLock) {
                    if (isRunning) {
                        vpnOutput?.write(replyBuffer)
                        vpnOutput?.flush()
                    }
                }
            } catch (e: Exception) {
                // Ignore transient lookup timeout issues
            } finally {
                try { socket?.close() } catch (e: Exception) {}
            }
        }
    }

    private fun computeIpChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        val end = offset + length
        while (i < end - 1) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < end) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while (sum ushr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return (sum.inv()) and 0xFFFF
    }

    private fun startBlockedListSync() {
        serviceScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getDatabase(this@EduGuardVpnService).mdmDao()
            // Poll blocklist directly from Room every 3 seconds
            while (isRunning) {
                try {
                    dao.getBlockedUrlsFlow().collect { list ->
                        synchronized(blockedDomainCache) {
                            blockedDomainCache.clear()
                            list.forEach { blockedDomainCache.add(it.domain.trim().lowercase()) }
                        }
                    }
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    delay(3000)
                }
            }
        }
    }

    private suspend fun addBlockLog(domain: String) {
        val now = System.currentTimeMillis()
        val lastLogged = lastLoggedVisitedSiteTime[domain] ?: 0L
        if (now - lastLogged < 4000) return // Throttle identical log notifications
        lastLoggedVisitedSiteTime[domain] = now

        try {
            val dao = AppDatabase.getDatabase(this).mdmDao()
            dao.insertLog(
                ActivityLog(
                    target = domain,
                    activityType = "SITE_BLOCKED",
                    details = "Access to locked web domain: '$domain' blocked by security policy.",
                    severity = "BLOCKED"
                )
            )
        } catch (e: Exception) {
            // Ignore background log writing errors
        }
    }

    private suspend fun addVisitedLog(domain: String) {
        // Skip common noisy platform DNS lookups
        if (domain.contains("gstatic.com") || domain.contains("googleapis.com") || domain.contains("android.com") || domain.contains("local")) {
            return
        }

        val now = System.currentTimeMillis()
        val lastLogged = lastLoggedVisitedSiteTime[domain] ?: 0L
        if (now - lastLogged < 15000) return // Throttle repetitive allowed lookups to 15s
        lastLoggedVisitedSiteTime[domain] = now

        try {
            val dao = AppDatabase.getDatabase(this).mdmDao()
            dao.insertLog(
                ActivityLog(
                    target = domain,
                    activityType = "SITE_VISITED",
                    details = "DNS lookup resolution allowed: visit to domain '$domain'.",
                    severity = "INFO"
                )
            )
        } catch (e: Exception) {
            // Ignore database lock collisions
        }
    }

    private fun addSystemLog(title: String, details: String, severity: String) {
        serviceScope.launch {
            try {
                val dao = AppDatabase.getDatabase(this@EduGuardVpnService).mdmDao()
                dao.insertLog(
                    ActivityLog(
                        target = "FIREWALL",
                        activityType = "ADMIN_SETTING",
                        details = "[$title] $details",
                        severity = severity
                    )
                )
            } catch (e: Exception) {}
        }
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
