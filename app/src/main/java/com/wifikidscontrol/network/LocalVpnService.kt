package com.wifikidscontrol.network

import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.FileChannel
import java.util.concurrent.Executors

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class LocalVpnService : VpnService() {

    private val TAG = "LocalVpnService"
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    private var isRunning = false
    private val blockedMacs = mutableSetOf<String>()
    private val blockedIps = mutableSetOf<String>()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val packetChannel = Channel<ByteBuffer>(1000)
    private val executor = Executors.newSingleThreadExecutor()

    companion object {
        const val ACTION_START_VPN = "com.wifikidscontrol.START_VPN"
        const val ACTION_STOP_VPN = "com.wifikidscontrol.STOP_VPN"
        const val ACTION_UPDATE_BLOCKLIST = "com.wifikidscontrol.UPDATE_BLOCKLIST"
        const val EXTRA_BLOCKED_MACS = "blocked_macs"
        const val EXTRA_BLOCKED_IPS = "blocked_ips"
        const val VPN_NETWORK = "10.0.0.0/24"
        const val VPN_DNS = "1.1.1.1"
        const val VPN_ROUTES = "0.0.0.0/0"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START_VPN -> startVpn()
            ACTION_STOP_VPN -> stopVpn()
            ACTION_UPDATE_BLOCKLIST -> {
                val macs = intent.getStringArrayListExtra(EXTRA_BLOCKED_MACS)?.toMutableSet() ?: mutableSetOf()
                val ips = intent.getStringArrayListExtra(EXTRA_BLOCKED_IPS)?.toMutableSet() ?: mutableSetOf()
                updateBlocklist(macs, ips)
            }
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return

        val builder = Builder()
            .setSession("WifiKidsControl_VPN")
            .addAddress("10.0.0.1", 24)
            .addDnsServer(VPN_DNS)
            .addRoute(VPN_ROUTES)
            .setBlocking(true)
            .setMtu(1500)

        // Allow local network traffic (router, LAN)
        val localNetworks = getLocalNetworks()
        for (network in localNetworks) {
            builder.addRoute(network, 32) // Exclude from VPN
        }

        try {
            vpnInterface = builder.establish()
            isRunning = true
            startPacketProcessing()
            Log.i(TAG, "VPN started successfully")
            sendBroadcast(Intent("com.wifikidscontrol.VPN_STATUS").apply {
                putExtra("connected", true)
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            stopVpn()
        }
    }

    private fun getLocalNetworks(): List<String> {
        val networks = mutableListOf<String>()
        try {
            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
            for (route in linkProperties.routes) {
                val addr = route.destination.address
                if (addr is java.net.Inet4Address) {
                    networks.add(addr.hostAddress)
                }
            }
            // Add common gateway IPs
            networks.addAll(listOf("192.168.1.1", "192.168.0.1", "10.0.0.1", "172.16.0.1"))
        } catch (e: Exception) {
            Log.w(TAG, "Could not get local networks", e)
        }
        return networks.distinct()
    }

    private fun startPacketProcessing() {
        vpnThread = Thread {
            val fd = vpnInterface?.fileDescriptor ?: return@Thread
            val inputStream = FileInputStream(fd)
            val outputStream = FileOutputStream(fd)
            val buffer = ByteBuffer.allocateDirect(32767)

            while (isRunning && !Thread.currentThread().isInterrupted) {
                try {
                    val read = inputStream.channel.read(buffer)
                    if (read > 0) {
                        buffer.flip()
                        processPacket(buffer, outputStream.channel)
                        buffer.clear()
                    }
                } catch (e: Exception) {
                    if (isRunning) Log.e(TAG, "Packet processing error", e)
                }
            }
        }.apply { name = "VpnPacketProcessor"; start() }
    }

    private fun processPacket(buffer: ByteBuffer, outputChannel: FileChannel) {
        // Parse IP header to get source/destination IP
        if (buffer.remaining() < 20) return // Minimum IPv4 header

        buffer.mark()
        val versionAndIhl = buffer.get() & 0xFF
        val version = versionAndIhl shr 4
        val ihl = versionAndIhl and 0x0F

        if (version != 4) {
            buffer.reset()
            forwardPacket(buffer, outputChannel)
            return
        }

        val headerLength = ihl * 4
        if (buffer.remaining() < headerLength) {
            buffer.reset()
            forwardPacket(buffer, outputChannel)
            return
        }

        // Skip to source/dest IP (offset 12)
        buffer.position(buffer.position() + 12)
        val srcIp = InetAddress.getByAddress(buffer.array().copyOfRange(buffer.position(), buffer.position() + 4)).hostAddress
        val dstIp = InetAddress.getByAddress(buffer.array().copyOfRange(buffer.position() + 4, buffer.position() + 8)).hostAddress
        buffer.reset()

        // Check if packet should be blocked
        val shouldBlock = blockedIps.contains(srcIp) || blockedIps.contains(dstIp) ||
                         blockedMacs.any { checkMacMatch(it, srcIp) || checkMacMatch(it, dstIp) }

        if (shouldBlock) {
            Log.d(TAG, "Blocking packet: $srcIp -> $dstIp")
            // Drop packet - don't forward
            return
        }

        forwardPacket(buffer, outputChannel)
    }

    private fun checkMacMatch(mac: String, ip: String): Boolean {
        // In a real implementation, you'd maintain an IP->MAC mapping
        // This is a simplified version - would need ARP table parsing
        return false
    }

    private fun forwardPacket(buffer: ByteBuffer, outputChannel: FileChannel) {
        try {
            buffer.flip()
            outputChannel.write(buffer)
            buffer.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Forward packet error", e)
        }
    }

    private fun updateBlocklist(macs: Set<String>, ips: Set<String>) {
        blockedMacs.clear()
        blockedMacs.addAll(macs.map { it.uppercase() })
        blockedIps.clear()
        blockedIps.addAll(ips)
        Log.i(TAG, "Blocklist updated: ${blockedMacs.size} MACs, ${blockedIps.size} IPs")
    }

    private fun stopVpn() {
        isRunning = false
        vpnThread?.interrupt()
        vpnThread = null
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN", e)
        }
        vpnInterface = null
        scope.cancel()
        executor.shutdown()
        sendBroadcast(Intent("com.wifikidscontrol.VPN_STATUS").apply {
            putExtra("connected", false)
        })
        Log.i(TAG, "VPN stopped")
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null
}

// Helper to start/stop VPN from app
object VpnController {
    fun startVpn(context: android.content.Context) {
        val intent = Intent(context, LocalVpnService::class.java).apply {
            action = LocalVpnService.ACTION_START_VPN
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopVpn(context: android.content.Context) {
        val intent = Intent(context, LocalVpnService::class.java).apply {
            action = LocalVpnService.ACTION_STOP_VPN
        }
        context.startService(intent)
    }

    fun updateBlocklist(context: android.content.Context, macs: Set<String>, ips: Set<String>) {
        val intent = Intent(context, LocalVpnService::class.java).apply {
            action = LocalVpnService.ACTION_UPDATE_BLOCKLIST
            putStringArrayListExtra(LocalVpnService.EXTRA_BLOCKED_MACS, ArrayList(macs))
            putStringArrayListExtra(LocalVpnService.EXTRA_BLOCKED_IPS, ArrayList(ips))
        }
        context.startService(intent)
    }

    fun isVpnPrepared(context: android.content.Context): Boolean {
        // Check if VPN permission is granted
        return android.net.VpnService.prepare(context) == null
    }

    fun requestVpnPermission(context: android.content.Context, requestCode: Int): Intent? {
        return android.net.VpnService.prepare(context)
    }
}