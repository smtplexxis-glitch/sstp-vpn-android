package com.sstpvpn.android.vpn

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sstpvpn.android.R
import com.sstpvpn.android.model.AppDatabase
import com.sstpvpn.android.model.VpnServer
import com.sstpvpn.android.ui.MainActivity
import com.sstpvpn.android.utils.PrefsManager
import kotlinx.coroutines.*
import java.io.*
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

class SstpVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.sstpvpn.android.CONNECT"
        const val ACTION_DISCONNECT = "com.sstpvpn.android.DISCONNECT"
        const val EXTRA_SERVER_ID = "server_id"
        private const val TAG = "SstpVpnService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sstp_vpn_channel"
        var isRunning = false
        var statusCallback: ((String) -> Unit)? = null
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var serviceJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var prefs: PrefsManager

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsManager.getInstance(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> startVpn(intent.getIntExtra(EXTRA_SERVER_ID, -1))
            ACTION_DISCONNECT -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn(serverId: Int) {
        serviceJob = serviceScope.launch {
            try {
                val db = AppDatabase.getInstance(this@SstpVpnService)
                val server = if (serverId != -1) db.vpnServerDao().getServerById(serverId)
                             else db.vpnServerDao().getDefaultServer()
                if (server == null) { updateStatus("Server not found"); return@launch }
                updateStatus("Connecting to " + server.host + "...")
                startForeground(NOTIFICATION_ID, buildNotification("Connecting..."))
                isRunning = true
                connectSstp(server)
            } catch (e: Exception) {
                Log.e(TAG, "VPN error", e)
                updateStatus("Error: " + e.message)
                stopSelf()
            }
        }
    }

    private suspend fun connectSstp(server: VpnServer) = withContext(Dispatchers.IO) {
        val ssl = createTrustAllSslContext()
        val socket = ssl.socketFactory.createSocket() as SSLSocket
        try {
            socket.connect(InetSocketAddress(server.host, server.port), 15000)
            socket.startHandshake()
            val inp = socket.inputStream
            val out = socket.outputStream
            sendHttpConnect(out, server.host)
            if (!readHttpResponse(inp).contains("200")) {
                updateStatus("HTTP Error"); socket.close(); return@withContext
            }
            updateStatus("HTTP tunnel, SSTP...")
            sendSstpInit(out)
            if (readSstpResponse(inp) == null) {
                updateStatus("SSTP handshake error"); socket.close(); return@withContext
            }
            updateStatus("SSTP, PPP...")
            if (!negotiatePpp(inp, out, server.username, server.password)) {
                updateStatus("PPP Error"); socket.close(); return@withContext
            }
            val vpnBuilder = Builder()
                .setSession(server.name)
                .addAddress("192.168.100.2", 24)
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4")
                .setMtu(1400)
            val sel = prefs.selectedApps
            if (sel.isNotEmpty()) {
                for (pkg in sel) {
                    try { vpnBuilder.addAllowedApplication(pkg) } catch (_: Exception) {}
                }
                vpnBuilder.addAllowedApplication(packageName)
            } else {
                vpnBuilder.addRoute("0.0.0.0", 0)
            }
            vpnInterface = vpnBuilder.establish() ?: run {
                updateStatus("Cannot create VPN interface"); socket.close(); return@withContext
            }
            updateStatus("VPN Connected!")
            startForeground(NOTIFICATION_ID, buildNotification("Connected: " + server.host))
            prefs.isVpnConnected = true
            forwardTraffic(vpnInterface!!, inp, out)
        } catch (e: Exception) {
            Log.e(TAG, "SSTP error", e)
            updateStatus("Error: " + e.message)
        } finally {
            try { socket.close() } catch (_: Exception) {}
            stopVpn()
        }
    }

    private fun sendHttpConnect(out: OutputStream, host: String) {
        val uuid = generateUuid()
        val r = "SSTP_DUPLEX_POST /sra_{BA195980-CD49-458b-9E23-C84EE0ADCD75}/ HTTP/1.1
" +
                "SSTPCORRELATIONID: {" + uuid + "}
" +
                "Content-Length: 18446744073709551615
" +
                "Host: " + host + "

"
        out.write(r.toByteArray()); out.flush()
    }

    private fun readHttpResponse(inp: InputStream): String {
        val sb = StringBuilder()
        var a = 0.toChar(); var b = 0.toChar(); var c = 0.toChar()
        while (true) {
            val d = inp.read().toChar()
            sb.append(d)
            if (b == '
' && c == '
' && a == '
' && d == '
') break
            b = c; c = a; a = d
        }
        return sb.toString()
    }

    private fun sendSstpInit(out: OutputStream) {
        val msg = ByteBuffer.allocate(48)
        msg.put(0x10.toByte()); msg.put(0x01.toByte())
        msg.putShort(48); msg.putShort(0x0001.toShort()); msg.putShort(1)
        msg.put(0x00.toByte()); msg.put(0x01.toByte())
        msg.putShort(6); msg.putShort(0x0001.toShort())
        repeat(30) { msg.put(0x00.toByte()) }
        out.write(msg.array()); out.flush()
    }

    private fun readSstpResponse(inp: InputStream): ByteArray? {
        return try {
            val h = ByteArray(4); var r = 0
            while (r < 4) r += inp.read(h, r, 4 - r)
            val len = ((h[2].toInt() and 0xFF) shl 8) or (h[3].toInt() and 0xFF)
            val b = ByteArray(len - 4); var br = 0
            while (br < b.size) br += inp.read(b, br, b.size - br)
            h + b
        } catch (e: Exception) { null }
    }

    private fun negotiatePpp(inp: InputStream, out: OutputStream, user: String, pass: String): Boolean {
        return try {
            val lcpReq = byteArrayOf(
                0xFF.toByte(), 0x03.toByte(), 0xC0.toByte(), 0x21.toByte(),
                0x01.toByte(), 0x01.toByte(), 0x00.toByte(), 0x09.toByte(),
                0x02.toByte(), 0x04.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()
            )
            writePppFrame(out, lcpReq)
            Thread.sleep(500)
            val rsp = ByteArray(16).also { SecureRandom().nextBytes(it) }
            writePppFrame(out, byteArrayOf(
                0xFF.toByte(), 0x03.toByte(), 0xC2.toByte(), 0x23.toByte(),
                0x02.toByte(), 0x01.toByte(), 0x00.toByte(), (6 + rsp.size).toByte(),
                rsp.size.toByte()
            ) + rsp)
            true
        } catch (e: Exception) { false }
    }

    private fun writePppFrame(out: OutputStream, data: ByteArray) {
        val p = ByteBuffer.allocate(8 + data.size)
        p.put(0x10.toByte()); p.put(0x00.toByte())
        p.putShort((8 + data.size).toShort()); p.putInt(0); p.put(data)
        out.write(p.array()); out.flush()
    }

    private suspend fun forwardTraffic(
        tun: ParcelFileDescriptor, sIn: InputStream, sOut: OutputStream
    ) = withContext(Dispatchers.IO) {
        val ti = FileInputStream(tun.fileDescriptor)
        val to = FileOutputStream(tun.fileDescriptor)
        val buf = ByteArray(1400)
        val j1 = launch {
            try {
                while (isActive) {
                    val n = ti.read(buf)
                    if (n > 0) {
                        val p = ByteBuffer.allocate(8 + n)
                        p.put(0x10.toByte()); p.put(0x00.toByte())
                        p.putShort((8 + n).toShort()); p.putInt(0); p.put(buf, 0, n)
                        sOut.write(p.array()); sOut.flush()
                    }
                }
            } catch (_: Exception) {}
        }
        val j2 = launch {
            try {
                while (isActive) {
                    val h = ByteArray(4); var r = 0
                    while (r < 4) r += sIn.read(h, r, 4 - r)
                    val len = ((h[2].toInt() and 0xFF) shl 8) or (h[3].toInt() and 0xFF)
                    if (len > 8) {
                        val rv = ByteArray(4); var rr = 0
                        while (rr < 4) rr += sIn.read(rv, rr, 4 - rr)
                        val pl = ByteArray(len - 8); var pr = 0
                        while (pr < pl.size) pr += sIn.read(pl, pr, pl.size - pr)
                        to.write(pl)
                    }
                }
            } catch (_: Exception) {}
        }
        j1.join(); j2.join()
    }

    private fun stopVpn() {
        isRunning = false; prefs.isVpnConnected = false
        serviceJob?.cancel()
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        updateStatus("Disconnected")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { stopVpn(); serviceScope.cancel(); super.onDestroy() }

    private fun updateStatus(s: String) { Log.d(TAG, s); statusCallback?.invoke(s) }

    private fun generateUuid(): String {
        val b = ByteArray(16).also { SecureRandom().nextBytes(it) }
        b[6] = ((b[6].toInt() and 0x0F) or 0x40).toByte()
        b[8] = ((b[8].toInt() and 0x3F) or 0x80).toByte()
        val hex = b.joinToString("") { "%02x".format(it) }
        return hex.substring(0,8) + "-" + hex.substring(8,12) + "-" + hex.substring(12,16) + "-" + hex.substring(16,20) + "-" + hex.substring(20)
    }

    private fun createTrustAllSslContext(): SSLContext {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        return SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), SecureRandom()) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "SSTP VPN", NotificationManager.IMPORTANCE_LOW)
            ch.description = "VPN status"
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SSTP VPN").setContentText(text)
            .setSmallIcon(R.drawable.ic_vpn).setContentIntent(intent).setOngoing(true).build()
    }
}
