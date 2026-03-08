package com.zxy.jove

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zxy.jove.ui.theme.JoveTheme
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Locale
import java.util.UUID

private const val TAG = "JOVE_PHONE_BLE"
private const val TARGET_NAME_KEYWORD = "星彩"
private const val SCAN_DURATION_MS = 12_000L
private const val BIND_TIMEOUT_MS = 18_000L
private const val SEND_BIND_FALLBACK_DELAY_MS = 700L
private const val READ_REPLY_FALLBACK_DELAY_MS = 900L
private const val READ_RETRY_INTERVAL_MS = 450L
private const val READ_RETRY_MAX_COUNT = 6
private const val DISCOVER_AFTER_REFRESH_DELAY_MS = 350L
private const val RECONNECT_TIMEOUT_MS = 10_000L
private const val RECONNECT_BASE_DELAY_MS = 1_500L
private const val RECONNECT_MAX_DELAY_MS = 30_000L
private const val RECONNECT_MAX_ATTEMPTS = 6
private const val PREFS_NAME = "jove_phone_prefs"
private const val KEY_BOUND_DEVICE_ADDRESS = "bound_device_address"
private const val PKT_TYPE_JSON: Byte = 0x01
private const val PKT_HEADER_SIZE = 5
private const val RX_PACKET_TIMEOUT_MS = 15_000L
private const val LINK_HEARTBEAT_INTERVAL_MS = 8_000L
private const val LINK_HEARTBEAT_TIMEOUT_MS = 20_000L

private val SERVICE_UUID: UUID = UUID.fromString("3f6d8b2e-7f61-4c85-b1d2-6e9c0f4a12d7")
private val NOTIFY_CHAR_UUID: UUID = UUID.fromString("a1c4e5f6-9b32-4d8a-8c7f-2b9d4e6f1a23")
private val WRITE_CHAR_UUID: UUID = UUID.fromString("b7d3c1a9-5e24-4fa1-9d6b-3c8e2f5a7b41")
private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
private val BIND_PAYLOAD = "BIND".toByteArray(Charsets.UTF_8)

private val BrandPurple = Color(0xFF5721D2)
private val BrandPurpleLight = Color(0xFFF1E6FF)
private val BgGradientTop = Color(0xFFEAE0F5)
private val BgGradientBottom = Color(0xFFF6F6F9)
private val TextPrimary = Color(0xFF1A1A1A)
private val TextSecondary = Color(0xFF999999)
private val CardBgColor = Color(0xCCFFFFFF)

data class ScannedBleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
)

enum class ConnectionStatus {
    Disconnected,
    Connected,
    WifiConnected,
}

enum class ControlType {
    BRIGHTNESS,
    VOLUME,
}

enum class AlbumFilter {
    ALL,
    IMAGE,
    VIDEO,
}

enum class AlbumPageState {
    DISCONNECTED,
    CONTENT,
}

data class MediaItemUi(
    val id: String,
    val isVideo: Boolean = false,
)

enum class AppPage {
    DASHBOARD,
    TRANSLATE,
}

data class LanguageOption(
    val displayName: String,
    val code: String,
)

private val TRANSLATE_LANGUAGES = listOf(
    LanguageOption("自动检测", "auto"),
    LanguageOption("中文", "zh-CN"),
    LanguageOption("英文", "en"),
    LanguageOption("日文", "ja"),
    LanguageOption("韩文", "ko"),
    LanguageOption("法文", "fr"),
    LanguageOption("德文", "de"),
    LanguageOption("西班牙文", "es"),
    LanguageOption("俄文", "ru"),
)

data class GlassesUiState(
    val connection: ConnectionStatus = ConnectionStatus.Disconnected,
    val activeControl: ControlType? = null,
    val batteryLevel: Int = 70,
    val wifiName: String = "JUHEDATA_882912",
    val brightness: Float = 0.6f,
    val volume: Float = 0.4f,
)

private data class PendingWrite(
    val value: ByteArray,
    val writeType: Int,
)

private class IncomingPacketAssembler(
    private val timeoutMs: Long,
) {
    private var expectedSeq: Int? = null
    private var updatedAt: Long = 0L
    private val stream = ByteArrayOutputStream()

    fun reset() {
        expectedSeq = null
        updatedAt = 0L
        stream.reset()
    }

    fun append(packet: ByteArray, nowMs: Long): ByteArray? {
        if (packet.size < PKT_HEADER_SIZE) {
            return packet
        }

        val type = packet[0]
        if (type != PKT_TYPE_JSON) {
            return packet
        }

        if (updatedAt > 0 && nowMs - updatedAt > timeoutMs) {
            reset()
        }

        val seq = ByteBuffer.wrap(packet, 1, 4).int
        val body = packet.copyOfRange(PKT_HEADER_SIZE, packet.size)

        if (seq == 0) {
            reset()
            return body
        }

        if (expectedSeq == null || seq == 1) {
            stream.reset()
            expectedSeq = 1
        }

        if (expectedSeq != seq) {
            stream.reset()
            expectedSeq = null
            updatedAt = nowMs
            return null
        }

        stream.write(body)
        expectedSeq = seq + 1
        updatedAt = nowMs

        if (body.size < 505) {
            val merged = stream.toByteArray()
            reset()
            return merged
        }

        return null
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JoveTheme(darkTheme = false, dynamicColor = false) {
                BindScreen()
            }
        }
    }
}

@Composable
private fun BindScreen() {
    val context = LocalContext.current
    val bluetoothAdapter = remember {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    val devices = remember { mutableStateListOf<ScannedBleDevice>() }
    val activeGatt = remember { mutableStateOf<BluetoothGatt?>(null) }
    val notifyCharacteristic = remember { mutableStateOf<BluetoothGattCharacteristic?>(null) }
    val writeCharacteristic = remember { mutableStateOf<BluetoothGattCharacteristic?>(null) }

    var statusMessage by remember { mutableStateOf("点击开始扫描并绑定眼镜") }
    var isScanning by remember { mutableStateOf(false) }
    var isBinding by remember { mutableStateOf(false) }
    var bindingAddress by remember { mutableStateOf<String?>(null) }
    var pendingStartAfterEnable by remember { mutableStateOf(false) }
    var pendingReconnectAfterEnable by remember { mutableStateOf(false) }
    var bindSessionId by remember { mutableStateOf(0) }
    var bindCommandSent by remember { mutableStateOf(false) }
    var bindFinished by remember { mutableStateOf(false) }
    var bindWriteType by remember { mutableStateOf(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) }
    var cccdFallbackTried by remember { mutableStateOf(false) }
    var readInFlight by remember { mutableStateOf(false) }
    var servicesDiscoveredInSession by remember { mutableStateOf(false) }
    var isReconnectInProgress by remember { mutableStateOf(false) }
    var pendingReconnectScan by remember { mutableStateOf(false) }
    var reconnectTargetAddress by remember { mutableStateOf<String?>(null) }
    var reconnectMatchedAddress by remember { mutableStateOf<String?>(null) }
    var reconnectAttempt by remember { mutableStateOf(0) }
    var reconnectJobToken by remember { mutableStateOf(0L) }
    var reconnectCause by remember { mutableStateOf("unknown") }
    var currentTraceId by remember { mutableStateOf("none") }
    var currentMtu by remember { mutableStateOf(247) }
    var nextTxSeq by remember { mutableStateOf(1) }
    var writeInFlight by remember { mutableStateOf(false) }
    var writeRetryCount by remember { mutableStateOf(0) }
    var heartbeatLoopStarted by remember { mutableStateOf(false) }
    var lastRxAt by remember { mutableStateOf(0L) }
    var linkReady by remember { mutableStateOf(false) }
    var serverReadyReceived by remember { mutableStateOf(false) }
    val pendingWrites = remember { ArrayDeque<PendingWrite>() }
    val rxAssembler = remember { IncomingPacketAssembler(RX_PACKET_TIMEOUT_MS) }
    var boundDeviceAddress by remember {
        mutableStateOf(prefs.getString(KEY_BOUND_DEVICE_ADDRESS, null))
    }
    var isGlassesBound by remember {
        mutableStateOf(boundDeviceAddress != null)
    }
    var dashboardState by remember {
        mutableStateOf(
            GlassesUiState(
                connection = ConnectionStatus.Disconnected,
                batteryLevel = 70,
                wifiName = "JUHEDATA_882912",
                brightness = 0.6f,
                volume = 0.4f,
            ),
        )
    }
    var selectedBottomTab by remember { mutableStateOf(0) }
    var appPage by remember { mutableStateOf(AppPage.DASHBOARD) }
    var sourceLang by remember { mutableStateOf("en") }
    var targetLang by remember { mutableStateOf("zh-CN") }
    var isTranslateSessionActive by remember { mutableStateOf(false) }
    var albumFilter by remember { mutableStateOf(AlbumFilter.ALL) }
    var showImportBanner by remember { mutableStateOf(true) }
    val albumMedia by remember {
        mutableStateOf(
            List(18) { index ->
                MediaItemUi(id = index.toString(), isVideo = index % 5 == 0)
            },
        )
    }

    fun newTraceId(prefix: String): String {
        val ts = System.currentTimeMillis().toString(16)
        val rand = ((Math.random() * 0xFFFF).toInt() and 0xFFFF).toString(16)
        return "$prefix-$ts-$rand"
    }

    fun updateTraceId(prefix: String) {
        currentTraceId = newTraceId(prefix)
    }

    fun logLink(level: String, stage: String, message: String) {
        val lv = level.uppercase(Locale.US)
        Log.d(TAG, "[$lv][$stage][trace=$currentTraceId] $message")
    }

    fun log(message: String) {
        logLink("debug", "general", message)
    }

    fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    fun clearGattState() {
        activeGatt.value = null
        notifyCharacteristic.value = null
        writeCharacteristic.value = null
        writeInFlight = false
        pendingWrites.clear()
        rxAssembler.reset()
        linkReady = false
        serverReadyReceived = false
        lastRxAt = 0L
        currentMtu = 247
    }

    fun framePacket(type: Byte, seq: Int, body: ByteArray): ByteArray {
        val out = ByteArray(PKT_HEADER_SIZE + body.size)
        out[0] = type
        out[1] = ((seq ushr 24) and 0xFF).toByte()
        out[2] = ((seq ushr 16) and 0xFF).toByte()
        out[3] = ((seq ushr 8) and 0xFF).toByte()
        out[4] = (seq and 0xFF).toByte()
        System.arraycopy(body, 0, out, PKT_HEADER_SIZE, body.size)
        return out
    }

    fun drainWriteQueue() {
        val gatt = activeGatt.value ?: return
        val writeChar = writeCharacteristic.value ?: return
        if (writeInFlight) return
        val next = pendingWrites.removeFirstOrNull() ?: return
        val ok = writeCharacteristicCompat(
            gatt = gatt,
            characteristic = writeChar,
            value = next.value,
            writeType = next.writeType,
        )
        if (ok) {
            writeInFlight = true
            writeRetryCount = 0
        } else {
            writeInFlight = false
            val retryDelay = 120L * (writeRetryCount + 1)
            if (writeRetryCount < 3 && activeGatt.value != null && writeCharacteristic.value != null) {
                writeRetryCount += 1
                logLink("warn", "protocol-tx", "write queue send failed, retry=$writeRetryCount delayMs=$retryDelay")
                mainHandler.postDelayed({ runOnMain { drainWriteQueue() } }, retryDelay)
                return
            }
            writeRetryCount = 0
            if (isBinding && !bindFinished) {
                statusMessage = "链路忙，绑定失败，请重试"
                logLink("warn", "protocol-tx", "binding write failed")
                activeGatt.value?.let { staleGatt ->
                    runCatching { staleGatt.disconnect() }
                    runCatching { staleGatt.close() }
                }
                clearGattState()
                isBinding = false
                bindingAddress = null
                bindCommandSent = false
                bindFinished = false
                bindWriteType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                cccdFallbackTried = false
                servicesDiscoveredInSession = false
                dashboardState = dashboardState.copy(connection = ConnectionStatus.Disconnected)
            } else {
                statusMessage = "发送失败，稍后重试"
            }
            log("write queue send failed")
        }
    }

    fun enqueuePacketizedWrite(payload: ByteArray): Boolean {
        val mtuPayloadMax = (currentMtu - 3).coerceAtLeast(20)
        val bodyChunkMax = (mtuPayloadMax - PKT_HEADER_SIZE).coerceAtLeast(1)
        if (payload.isEmpty()) {
            pendingWrites.addLast(
                PendingWrite(
                    value = framePacket(PKT_TYPE_JSON, 0, payload),
                    writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                ),
            )
        } else if (payload.size <= bodyChunkMax) {
            pendingWrites.addLast(
                PendingWrite(
                    value = framePacket(PKT_TYPE_JSON, 0, payload),
                    writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                ),
            )
        } else {
            var seq = nextTxSeq
            var offset = 0
            while (offset < payload.size) {
                val end = (offset + bodyChunkMax).coerceAtMost(payload.size)
                val body = payload.copyOfRange(offset, end)
                pendingWrites.addLast(
                    PendingWrite(
                        value = framePacket(PKT_TYPE_JSON, seq, body),
                        writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                    ),
                )
                seq += 1
                offset = end
            }
            nextTxSeq = seq
            if (nextTxSeq > Int.MAX_VALUE - 1024) {
                nextTxSeq = 1
            }
        }
        drainWriteQueue()
        return true
    }

    @SuppressLint("MissingPermission")
    fun sendBleJson(payload: JSONObject): Boolean {
        if (activeGatt.value == null || writeCharacteristic.value == null) {
            statusMessage = "设备未连接"
            return false
        }
        val bytes = payload.toString().toByteArray(Charsets.UTF_8)
        val ok = enqueuePacketizedWrite(bytes)
        if (!ok) {
            statusMessage = "发送失败: ${payload.optString("action")}"
            return false
        }
        logLink("debug", "protocol-tx", payload.toString())
        return true
    }

    fun sendTranslateStart(): Boolean {
        return sendBleJson(JSONObject().put("action", "translate_start"))
    }

    fun sendTranslateStop(): Boolean {
        return sendBleJson(JSONObject().put("action", "translate_stop"))
    }

    fun sendSelectLanguage(source: String, target: String) {
        sendBleJson(
            JSONObject()
                .put("action", "select_language")
                .put("source_lang", source)
                .put("target_lang", target),
        )
    }

    fun sendPing(reason: String = "heartbeat") {
        sendBleJson(
            JSONObject()
                .put("action", "ping")
                .put("reason", reason)
                .put("ts", System.currentTimeMillis()),
        )
    }

    fun sendHandshake(reason: String = "phone_connect") {
        sendBleJson(
            JSONObject()
                .put("action", "handshake")
                .put("client", "jove_phone")
                .put("ts", System.currentTimeMillis())
                .put("reason", reason),
        )
    }

    @SuppressLint("MissingPermission")
    fun closeGatt() {
        activeGatt.value?.let { gatt ->
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
        clearGattState()
    }

    fun resetBindingFlags() {
        isBinding = false
        bindingAddress = null
        bindCommandSent = false
        bindFinished = false
        bindWriteType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        cccdFallbackTried = false
        servicesDiscoveredInSession = false
    }

    fun nextReconnectDelayMs(attempt: Int): Long {
        val exp = (1L shl attempt.coerceAtMost(10))
        val raw = RECONNECT_BASE_DELAY_MS * exp
        return raw.coerceAtMost(RECONNECT_MAX_DELAY_MS)
    }

    fun scheduleReconnect(cause: String) {
        if (!isGlassesBound) return
        if (isReconnectInProgress) return

        reconnectCause = cause
        reconnectAttempt += 1
        if (reconnectAttempt > RECONNECT_MAX_ATTEMPTS) {
            isReconnectInProgress = false
            statusMessage = "重连失败次数过多，请手动重试"
            logLink("error", "reconnect", "abort cause=$cause attempts=$reconnectAttempt")
            return
        }

        val delayMs = nextReconnectDelayMs(reconnectAttempt - 1)
        val token = System.currentTimeMillis()
        reconnectJobToken = token
        isReconnectInProgress = true
        statusMessage = "链路异常(${cause})，${delayMs / 1000.0}s后第${reconnectAttempt}次重连"
        logLink("warn", "reconnect", "scheduled cause=$cause attempt=$reconnectAttempt delayMs=$delayMs")

        mainHandler.postDelayed({
            runOnMain {
                if (!isReconnectInProgress || reconnectJobToken != token) return@runOnMain
                isReconnectInProgress = false
                logLink("info", "reconnect", "start attempt=$reconnectAttempt cause=$reconnectCause")
                val savedAddress = boundDeviceAddress
                val adapter = bluetoothAdapter
                if (savedAddress == null || adapter == null || !adapter.isEnabled || !hasBlePermission(context)) {
                    statusMessage = "重连条件不满足，请手动重试"
                    return@runOnMain
                }
                reconnectTargetAddress = savedAddress
                pendingReconnectScan = true
                statusMessage = "正在查找已绑定设备..."

                activeGatt.value?.let { staleGatt ->
                    runCatching { staleGatt.disconnect() }
                    runCatching { staleGatt.close() }
                }
                clearGattState()
                isBinding = false
                bindingAddress = null
                bindCommandSent = false
                bindFinished = false
                bindWriteType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                cccdFallbackTried = false
                servicesDiscoveredInSession = false
                devices.clear()
                isScanning = true
                log("Start scan")

                val filters = listOf(ScanFilter.Builder().build())
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()
                val reconnectScanCallback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        runOnMain {
                            val address = result.device.address.orEmpty()
                            if (pendingReconnectScan && reconnectTargetAddress == address) {
                                reconnectMatchedAddress = address
                                pendingReconnectScan = false
                                adapter.bluetoothLeScanner?.stopScan(this)
                            }
                        }
                    }

                    override fun onScanFailed(errorCode: Int) {
                        runOnMain {
                            isScanning = false
                            statusMessage = "重连扫描失败: $errorCode"
                        }
                    }
                }
                adapter.bluetoothLeScanner?.startScan(filters, settings, reconnectScanCallback)
                mainHandler.postDelayed({
                    runOnMain {
                        adapter.bluetoothLeScanner?.stopScan(reconnectScanCallback)
                    }
                }, SCAN_DURATION_MS + 300L)
            }
        }, delayMs)
    }

    fun markLinkHealthy(reason: String) {
        if (reconnectAttempt != 0 || isReconnectInProgress) {
            logLink("info", "reconnect", "healthy reason=$reason reset_attempts")
        }
        reconnectAttempt = 0
        isReconnectInProgress = false
    }

    fun completeBinding(message: String) {
        bindFinished = true
        isBinding = false
        isGlassesBound = true
        readInFlight = false

        bindingAddress?.let { address ->
            prefs.edit().putString(KEY_BOUND_DEVICE_ADDRESS, address).apply()
            boundDeviceAddress = address
            log("Saved bound device address: $address")
        }

        dashboardState = dashboardState.copy(connection = ConnectionStatus.Connected)
        statusMessage = message
        log("Binding completed: $message")
    }

    fun failBinding(message: String) {
        log("Binding failed: $message")
        readInFlight = false
        closeGatt()
        resetBindingFlags()
        statusMessage = message
    }

    fun handleIncomingText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val lower = trimmed.lowercase()
        if (lower == "bind" || lower.contains("bind_ok") || lower.contains("bind success")) {
            if (isBinding && !bindFinished) {
                completeBinding("绑定成功")
            }
            return
        }

        val json = runCatching { JSONObject(trimmed) }.getOrNull()
        if (json != null) {
            val action = json.optString("action")
            when (action) {
                "ready" -> {
                    serverReadyReceived = true
                    logLink("info", "protocol-rx", "server ready received")
                    if (linkReady && isBinding && !bindFinished) {
                        val gatt = activeGatt.value
                        if (gatt != null) {
                            sendHandshake("server_ready")
                            if (!bindCommandSent && !bindFinished) {
                                val writeChar = writeCharacteristic.value
                                if (writeChar != null) {
                                    val bindJson = JSONObject()
                                        .put("action", "bind")
                                        .put("client", "jove_phone")
                                        .put("reason", "server-ready")
                                        .put("ts", System.currentTimeMillis())
                                    sendBleJson(bindJson)
                                    pendingWrites.addLast(
                                        PendingWrite(
                                            value = BIND_PAYLOAD,
                                            writeType = bindWriteType,
                                        ),
                                    )
                                    drainWriteQueue()
                                    bindCommandSent = true
                                    statusMessage = "已发送绑定指令，等待设备响应..."
                                }
                            }
                        }
                    }
                }

                "handshake", "ack", "handshake_ack" -> {
                    linkReady = true
                    if (isBinding && !bindFinished) {
                        completeBinding("握手成功，绑定完成")
                    }
                }

                "bind", "bind_success" -> {
                    if (isBinding && !bindFinished) {
                        completeBinding("绑定成功")
                    }
                }

                "pong" -> {
                    // 心跳回包，仅更新时间
                }
            }
            log("BLE IN JSON: $trimmed")
            return
        }

        val display = trimmed.ifEmpty { "<empty>" }
        if (isBinding && !bindFinished) {
            if (display.contains("success", ignoreCase = true) || display.contains("ok", ignoreCase = true)) {
                completeBinding("绑定成功")
            } else {
                completeBinding("收到设备回复: $display")
            }
        }
    }

    fun handleIncomingRaw(bytes: ByteArray) {
        val merged = rxAssembler.append(bytes, System.currentTimeMillis()) ?: return
        lastRxAt = System.currentTimeMillis()
        val text = merged.decodeToString()
        if (text.isNotEmpty()) {
            handleIncomingText(text)
        } else {
            log("BLE IN HEX: ${merged.toHexString()}")
        }
    }

    @SuppressLint("MissingPermission")
    fun requestReplyRead(gatt: BluetoothGatt, reason: String): Boolean {
        if (readInFlight) {
            log("Read fallback($reason): skipped(in-flight)")
            return false
        }
        val notifyChar = notifyCharacteristic.value ?: return false
        readInFlight = true
        val ok = readCharacteristicCompat(gatt, notifyChar)
        if (!ok) {
            readInFlight = false
        }
        log("Read fallback($reason): $ok")
        return ok
    }

    fun scheduleReadRetries(gatt: BluetoothGatt, baseReason: String) {
        val sessionId = bindSessionId
        repeat(READ_RETRY_MAX_COUNT) { index ->
            val delayMs = READ_REPLY_FALLBACK_DELAY_MS + (index + 1) * READ_RETRY_INTERVAL_MS
            mainHandler.postDelayed({
                runOnMain {
                    if (isBinding && !bindFinished && sessionId == bindSessionId && activeGatt.value == gatt) {
                        requestReplyRead(gatt, "$baseReason-retry-${index + 1}")
                    }
                }
            }, delayMs)
        }
    }

    @SuppressLint("MissingPermission")
    fun sendBindCommand(gatt: BluetoothGatt, reason: String) {
        if (bindCommandSent || bindFinished) return
        if (writeCharacteristic.value == null) {
            failBinding("写特征不存在")
            return
        }

        val bindJson = JSONObject()
            .put("action", "bind")
            .put("ts", System.currentTimeMillis())
            .toString()
            .toByteArray(Charsets.UTF_8)

        val ok = enqueuePacketizedWrite(bindJson)
        if (!ok) {
            if (bindWriteType != BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
                bindWriteType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                log("Write default rejected, retry with no-response")
                sendBindCommand(gatt, "$reason-writeTypeFallback")
                return
            }
            failBinding("发送绑定指令失败")
            return
        }

        // 兼容旧固件，补发旧文本命令
        enqueuePacketizedWrite(BIND_PAYLOAD)

        bindCommandSent = true
        statusMessage = "已发送绑定指令，等待设备响应..."
        val mode = if (bindWriteType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) "default" else "no_response"
        log("BIND sent via $reason writeType=$mode")

        val sessionId = bindSessionId
        mainHandler.postDelayed({
            runOnMain {
                if (isBinding && !bindFinished && sessionId == bindSessionId && activeGatt.value == gatt) {
                    requestReplyRead(gatt, "post-bind")
                }
            }
        }, READ_REPLY_FALLBACK_DELAY_MS)
        scheduleReadRetries(gatt, "post-bind")
    }

    val scanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                runOnMain {
                    val name = result.device.name ?: result.scanRecord?.deviceName.orEmpty()
                    if (!name.contains(TARGET_NAME_KEYWORD)) return@runOnMain
                    val device = ScannedBleDevice(name, result.device.address.orEmpty(), result.rssi)
                    val index = devices.indexOfFirst { it.address == device.address }
                    if (index >= 0) devices[index] = device else devices.add(device)

                    if (pendingReconnectScan && reconnectTargetAddress == device.address) {
                        log("Reconnect scan matched target: ${device.address}")
                        reconnectMatchedAddress = device.address
                        pendingReconnectScan = false
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                runOnMain {
                    isScanning = false
                    statusMessage = "扫描失败: $errorCode"
                    log("Scan failed: $errorCode")
                }
            }
        }
    }

    fun stopScan() {
        if (hasBleScanPermission(context)) {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        }
        isScanning = false
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val adapter = bluetoothAdapter ?: run {
            statusMessage = "设备不支持蓝牙"
            return
        }
        if (!adapter.isEnabled) {
            statusMessage = "蓝牙未开启"
            return
        }
        if (!hasBleScanPermission(context)) {
            statusMessage = "缺少蓝牙扫描权限"
            return
        }

        closeGatt()
        resetBindingFlags()
        devices.clear()
        isScanning = true
        statusMessage = "正在扫描..."
        log("Start scan")

        val filters = listOf(ScanFilter.Builder().build())
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        adapter.bluetoothLeScanner?.startScan(filters, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun connectBoundDevice() {
        val savedAddress = boundDeviceAddress ?: run {
            statusMessage = "未找到已绑定设备"
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            statusMessage = "蓝牙不可用"
            return
        }
        if (!hasBlePermission(context)) {
            statusMessage = "缺少蓝牙权限"
            return
        }

        // 走“先扫描命中已绑定地址，再走 bindDevice 完整连接流程”的统一路径，稳定性更高
        reconnectTargetAddress = savedAddress
        pendingReconnectScan = true
        statusMessage = "正在查找已绑定设备..."
        log("Reconnect via scan target=$savedAddress")

        startScan()

        val sessionId = bindSessionId
        mainHandler.postDelayed({
            runOnMain {
                if (pendingReconnectScan && reconnectTargetAddress == savedAddress && bindSessionId == sessionId) {
                    pendingReconnectScan = false
                    statusMessage = "未发现已绑定设备，请确认眼镜已开机并靠近手机"
                    log("Reconnect via scan timeout: $savedAddress")
                }
            }
        }, SCAN_DURATION_MS + 300L)
    }

    @SuppressLint("MissingPermission")
    fun bindDevice(address: String) {
        if (!hasBlePermission(context)) {
            statusMessage = "缺少蓝牙权限"
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            statusMessage = "蓝牙不可用"
            return
        }

        stopScan()
        closeGatt()
        updateTraceId("ble")
        isBinding = true
        bindingAddress = address
        bindSessionId += 1
        bindCommandSent = false
        bindFinished = false
        statusMessage = "正在连接设备..."
        log("Start bind: $address session=$bindSessionId")

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                runOnMain {
                    logLink("info", "gatt", "connection state status=$status state=$newState")
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        runCatching { gatt.close() }
                        clearGattState()
                        resetBindingFlags()
                        statusMessage = "连接失败: $status"
                        scheduleReconnect("connect_status_$status")
                        return@runOnMain
                    }

                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            activeGatt.value = gatt
                            readInFlight = false
                            servicesDiscoveredInSession = false
                            logLink("info", "gatt", "connected address=$address")
                            val cacheRefreshed = refreshGattDeviceCache(gatt)
                            log("Gatt cache refresh: $cacheRefreshed")
                            gatt.requestMtu(247)
                            statusMessage = "连接成功，发现服务中..."
                            mainHandler.postDelayed(
                                { gatt.discoverServices() },
                                if (cacheRefreshed) DISCOVER_AFTER_REFRESH_DELAY_MS else 0L,
                            )

                            val sessionId = bindSessionId
                            mainHandler.postDelayed({
                                runOnMain {
                                    if (
                                        isBinding &&
                                        !bindFinished &&
                                        !servicesDiscoveredInSession &&
                                        sessionId == bindSessionId &&
                                        activeGatt.value == gatt
                                    ) {
                                        log("Service discovery watchdog: retry discoverServices")
                                        gatt.discoverServices()
                                    }
                                }
                            }, 2_500L)
                        }

                        BluetoothProfile.STATE_DISCONNECTED -> {
                            runCatching { gatt.close() }
                            if (bindingAddress == address || boundDeviceAddress == address) {
                                clearGattState()
                                resetBindingFlags()
                                dashboardState = dashboardState.copy(connection = ConnectionStatus.Disconnected)
                                statusMessage = "设备已断开"
                                scheduleReconnect("state_disconnected")
                            }
                        }
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    currentMtu = mtu
                }
                log("MTU changed: mtu=$mtu status=$status currentMtu=$currentMtu")
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                runOnMain {
                    servicesDiscoveredInSession = true
                    val services = gatt.services.orEmpty()
                    log("Services discovered: status=$status count=${services.size}")
                    services.forEachIndexed { index, service ->
                        log("Service[$index]=${service.uuid}")
                    }
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        failBinding("服务发现失败: $status")
                        return@runOnMain
                    }

                    val service = gatt.getService(SERVICE_UUID)
                    if (service == null) {
                        failBinding("未找到目标服务")
                        return@runOnMain
                    }

                    val notifyChar = service.getCharacteristic(NOTIFY_CHAR_UUID)
                    val writeChar = service.getCharacteristic(WRITE_CHAR_UUID)
                    log("Target service found: ${service.uuid}")
                    log("Target characteristics: notify=${notifyChar?.uuid} write=${writeChar?.uuid}")
                    if (notifyChar == null || writeChar == null) {
                        failBinding("未找到目标特征")
                        return@runOnMain
                    }

                    notifyCharacteristic.value = notifyChar
                    writeCharacteristic.value = writeChar

                    if (!gatt.setCharacteristicNotification(notifyChar, true)) {
                        failBinding("开启通知失败")
                        return@runOnMain
                    }

                    val cccd = notifyChar.getDescriptor(CCCD_UUID)
                    if (cccd == null) {
                        failBinding("通知描述符不存在")
                        return@runOnMain
                    }

                    cccdFallbackTried = false
                    if (!writeDescriptorCompat(gatt, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                        failBinding("订阅通知失败")
                        return@runOnMain
                    }

                    val sessionId = bindSessionId
                    mainHandler.postDelayed({
                        runOnMain {
                            if (isBinding && !bindCommandSent && sessionId == bindSessionId && activeGatt.value == gatt) {
                                sendBindCommand(gatt, "cccd-timeout-fallback")
                            }
                        }
                    }, SEND_BIND_FALLBACK_DELAY_MS)
                }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
            ) {
                if (descriptor.uuid != CCCD_UUID) return
                runOnMain {
                    log("Descriptor write: $status")
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        if (!cccdFallbackTried) {
                            cccdFallbackTried = true
                            val fallbackValue = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                            val retryOk = writeDescriptorCompat(gatt, descriptor, fallbackValue)
                            log("Descriptor fallback to indication: $retryOk")
                            if (retryOk) {
                                return@runOnMain
                            }
                        }
                        statusMessage = "通知订阅不可用，切换为主动读取响应..."
                        requestReplyRead(gatt, "descriptor-failed-$status")
                        scheduleReadRetries(gatt, "descriptor-failed-$status")
                        sendBindCommand(gatt, "descriptor-degraded-$status")
                        return@runOnMain
                    }
                    linkReady = true
                    markLinkHealthy("cccd_enabled")
                    if (serverReadyReceived) {
                        sendHandshake("cccd_enabled")
                        sendBindCommand(gatt, "descriptor-callback")
                    } else {
                        logLink("debug", "protocol", "wait server ready before handshake/bind")
                    }
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (characteristic.uuid != WRITE_CHAR_UUID) return
                runOnMain {
                    log("Characteristic write: $status")
                    if (status != BluetoothGatt.GATT_SUCCESS && isBinding && !bindFinished) {
                        if (bindWriteType != BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
                            bindCommandSent = false
                            bindWriteType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                            statusMessage = "写回调异常，切换 no-response 重试..."
                            sendBindCommand(gatt, "write-status-$status-fallback")
                            return@runOnMain
                        }
                        statusMessage = "绑定写回调异常，继续主动读取响应..."
                    }

                    writeInFlight = false
                    drainWriteQueue()

                    val sessionId = bindSessionId
                    mainHandler.postDelayed({
                        runOnMain {
                            if (isBinding && !bindFinished && sessionId == bindSessionId && activeGatt.value == gatt) {
                                requestReplyRead(gatt, "write-callback")
                            }
                        }
                    }, READ_REPLY_FALLBACK_DELAY_MS)
                    scheduleReadRetries(gatt, "write-callback")
                }
            }

            @Suppress("DEPRECATION")
            @Deprecated("Deprecated in Java")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (characteristic.uuid != NOTIFY_CHAR_UUID) return
                runOnMain {
                    readInFlight = false
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        handleIncomingRaw(characteristic.value ?: byteArrayOf())
                    } else {
                        log("Characteristic read failed: $status")
                    }
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) {
                if (characteristic.uuid != NOTIFY_CHAR_UUID) return
                runOnMain {
                    readInFlight = false
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        handleIncomingRaw(value)
                    } else {
                        log("Characteristic read failed: $status")
                    }
                }
            }

            @Suppress("DEPRECATION")
            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                if (characteristic.uuid == NOTIFY_CHAR_UUID) {
                    runOnMain {
                        readInFlight = false
                        handleIncomingRaw(characteristic.value ?: byteArrayOf())
                    }
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                if (characteristic.uuid == NOTIFY_CHAR_UUID) {
                    runOnMain {
                        readInFlight = false
                        handleIncomingRaw(value)
                    }
                }
            }
        }

        activeGatt.value = adapter.getRemoteDevice(address).connectGatt(context, false, callback)
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            when {
                pendingReconnectAfterEnable -> {
                    pendingReconnectAfterEnable = false
                    connectBoundDevice()
                }

                pendingStartAfterEnable -> {
                    pendingStartAfterEnable = false
                    startScan()
                }
            }
        } else {
            pendingStartAfterEnable = false
            pendingReconnectAfterEnable = false
            statusMessage = "你取消了蓝牙开启请求"
        }
    }

    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) {
            if (bluetoothAdapter?.isEnabled == true) {
                startScan()
            } else {
                pendingStartAfterEnable = true
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        } else {
            statusMessage = "请授予蓝牙权限后重试"
        }
    }

    LaunchedEffect(isScanning) {
        if (isScanning) {
            delay(SCAN_DURATION_MS)
            stopScan()
            statusMessage = if (pendingReconnectScan) {
                pendingReconnectScan = false
                "未发现已绑定设备，请确认眼镜已开机并靠近手机"
            } else {
                if (devices.isEmpty()) "未发现可绑定设备" else "扫描完成，点击设备开始绑定"
            }
        }
    }

    LaunchedEffect(reconnectMatchedAddress) {
        val matched = reconnectMatchedAddress ?: return@LaunchedEffect
        reconnectMatchedAddress = null
        stopScan()
        bindDevice(matched)
    }

    LaunchedEffect(isBinding, bindSessionId) {
        if (isBinding) {
            val sessionId = bindSessionId
            delay(BIND_TIMEOUT_MS)
            if (isBinding && !bindFinished && sessionId == bindSessionId) {
                failBinding("绑定超时，请重试")
            }
        }
    }

    LaunchedEffect(activeGatt.value, heartbeatLoopStarted) {
        if (activeGatt.value != null && !heartbeatLoopStarted) {
            heartbeatLoopStarted = true
            lastRxAt = System.currentTimeMillis()
            while (activeGatt.value != null) {
                delay(LINK_HEARTBEAT_INTERVAL_MS)
                if (activeGatt.value == null) break
                sendPing()
                if (lastRxAt > 0L && System.currentTimeMillis() - lastRxAt > LINK_HEARTBEAT_TIMEOUT_MS) {
                    statusMessage = "链路心跳超时，准备重连..."
                    closeGatt()
                    dashboardState = dashboardState.copy(connection = ConnectionStatus.Disconnected)
                    isBinding = false
                    heartbeatLoopStarted = false
                    scheduleReconnect("heartbeat_timeout")
                    break
                }
            }
        } else if (activeGatt.value == null) {
            heartbeatLoopStarted = false
        }
    }

    LaunchedEffect(appPage) {
        if (appPage == AppPage.TRANSLATE) {
            if (!isTranslateSessionActive) {
                val startOk = sendTranslateStart()
                if (startOk) {
                    isTranslateSessionActive = true
                    sendSelectLanguage(sourceLang, targetLang)
                    logLink("info", "translate", "session started")
                }
            }
        } else {
            if (isTranslateSessionActive) {
                sendTranslateStop()
                isTranslateSessionActive = false
                logLink("info", "translate", "session stopped")
            }
        }
    }

    if (isGlassesBound) {
        when (selectedBottomTab) {
            1 -> {
                AlbumScreen(
                    pageState = if (dashboardState.connection == ConnectionStatus.Disconnected) {
                        AlbumPageState.DISCONNECTED
                    } else {
                        AlbumPageState.CONTENT
                    },
                    selectedFilter = albumFilter,
                    mediaList = when (albumFilter) {
                        AlbumFilter.ALL -> albumMedia
                        AlbumFilter.IMAGE -> albumMedia.filter { !it.isVideo }
                        AlbumFilter.VIDEO -> albumMedia.filter { it.isVideo }
                    },
                    showImportBanner = showImportBanner,
                    onConnectClick = {
                        when {
                            bluetoothAdapter == null -> statusMessage = "设备不支持蓝牙"
                            !hasBlePermission(context) -> requestPermissionsLauncher.launch(requiredBlePermissions())
                            bluetoothAdapter.isEnabled -> connectBoundDevice()
                            else -> {
                                pendingReconnectAfterEnable = true
                                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            }
                        }
                    },
                    onFilterChange = { albumFilter = it },
                    onImportClick = { showImportBanner = false },
                    onBottomTabClick = { selectedBottomTab = it },
                )
            }

            else -> {
                if (appPage == AppPage.TRANSLATE) {
                    TranslatePage(
                        sourceLang = sourceLang,
                        targetLang = targetLang,
                        onBack = { appPage = AppPage.DASHBOARD },
                        onSourceChange = {
                            sourceLang = it
                            sendSelectLanguage(sourceLang, targetLang)
                        },
                        onTargetChange = {
                            targetLang = it
                            sendSelectLanguage(sourceLang, targetLang)
                        },
                        onSwap = {
                            val newSource = targetLang
                            val newTarget = sourceLang
                            sourceLang = newSource
                            targetLang = newTarget
                            sendSelectLanguage(sourceLang, targetLang)
                        },
                        onBottomTabClick = { selectedBottomTab = it },
                    )

                    LaunchedEffect(Unit) {
                        if (!isTranslateSessionActive) {
                            val startOk = sendBleJson(JSONObject().put("action", "translate_start"))
                            if (startOk) {
                                isTranslateSessionActive = true
                                sendSelectLanguage(sourceLang, targetLang)
                            }
                        }
                    }
                    DisposableEffect(Unit) {
                        onDispose {
                            if (isTranslateSessionActive) {
                                sendBleJson(JSONObject().put("action", "translate_stop"))
                                isTranslateSessionActive = false
                            }
                        }
                    }
                } else {
                    JoveSmartGlassesDashboard(
                        state = dashboardState,
                        onMainActionClick = {
                            when {
                                bluetoothAdapter == null -> statusMessage = "设备不支持蓝牙"
                                !hasBlePermission(context) -> requestPermissionsLauncher.launch(requiredBlePermissions())
                                bluetoothAdapter.isEnabled -> connectBoundDevice()
                                else -> {
                                    pendingReconnectAfterEnable = true
                                    enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                }
                            }
                        },
                        onToggleControl = { type ->
                            dashboardState = dashboardState.copy(
                                activeControl = if (dashboardState.activeControl == type) null else type,
                            )
                        },
                        onBrightnessChange = { newValue ->
                            dashboardState = dashboardState.copy(brightness = newValue)
                        },
                        onVolumeChange = { newValue ->
                            dashboardState = dashboardState.copy(volume = newValue)
                        },
                        onTranslateClick = { appPage = AppPage.TRANSLATE },
                        selectedBottomTab = selectedBottomTab,
                        onBottomTabClick = { selectedBottomTab = it },
                    )
                }
            }
        }
        return
    }

    DisposableEffect(Unit) {
        onDispose {
            stopScan()
            closeGatt()
        }
    }

    val primaryActionText = when {
        isBinding -> "绑定中..."
        isScanning -> "扫描中..."
        else -> "绑定眼镜"
    }

    val canClickPrimaryAction = !isBinding

    Scaffold(
        bottomBar = {
            FloatingBottomNavigationBar(
                selectedIndex = selectedBottomTab,
                onTabClick = { selectedBottomTab = it },
            )
        },
        containerColor = Color.Transparent,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(BgGradientTop, BgGradientBottom),
                    ),
                )
                .padding(paddingValues),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(64.dp))
                TopLogoHeader()

                Spacer(modifier = Modifier.weight(1f))

                Image(
                    painter = painterResource(id = R.mipmap.glasses),
                    contentDescription = "glasses",
                    modifier = Modifier.size(300.dp, 200.dp),
                    contentScale = ContentScale.Fit,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "JOVE Glasses S1",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        when {
                            bluetoothAdapter == null -> statusMessage = "设备不支持蓝牙"
                            !hasBlePermission(context) -> requestPermissionsLauncher.launch(requiredBlePermissions())
                            bluetoothAdapter.isEnabled -> startScan()
                            else -> {
                                pendingStartAfterEnable = true
                                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            }
                        }
                    },
                    enabled = canClickPrimaryAction,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .width(240.dp)
                        .height(56.dp),
                ) {
                    Text(
                        text = primaryActionText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = statusMessage,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(devices, key = { it.address }) { device ->
                        DeviceRow(
                            device = device,
                            isBinding = isBinding && bindingAddress == device.address,
                            onClick = { bindDevice(device.address) },
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(0.7f))
            }
        }
    }
}

@Composable
private fun JoveSmartGlassesDashboard(
    state: GlassesUiState,
    onMainActionClick: () -> Unit,
    onToggleControl: (ControlType) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onTranslateClick: () -> Unit,
    selectedBottomTab: Int,
    onBottomTabClick: (Int) -> Unit,
) {
    Scaffold(
        bottomBar = {
            FloatingBottomNavigationBar(
                selectedIndex = selectedBottomTab,
                onTabClick = onBottomTabClick,
            )
        },
        containerColor = Color.Transparent,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(BgGradientTop, BgGradientBottom))),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                TopLogoHeader()

                Box(modifier = Modifier.size(280.dp, 160.dp), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.mipmap.glasses),
                        contentDescription = "眼镜产品图",
                        modifier = Modifier.size(280.dp, 160.dp),
                        contentScale = ContentScale.Fit,
                    )
                }

                StatusIndicatorRow(state)

                Spacer(modifier = Modifier.height(12.dp))

                Text("JOVE Glasses S1", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                Spacer(modifier = Modifier.height(12.dp))

                if (state.connection == ConnectionStatus.Disconnected) {
                    MainActionButton(onClick = onMainActionClick)
                    Spacer(modifier = Modifier.height(32.dp))
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                DashboardGrid(
                    state = state,
                    onToggleControl = onToggleControl,
                    onBrightnessChange = onBrightnessChange,
                    onVolumeChange = onVolumeChange,
                    onTranslateClick = onTranslateClick,
                )
            }
        }
    }
}

@Composable
private fun StatusIndicatorRow(state: GlassesUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusPill(
            icon = Icons.Outlined.Wifi,
            text = when (state.connection) {
                ConnectionStatus.WifiConnected -> "已连接"
                else -> "无网络"
            },
            isActive = state.connection == ConnectionStatus.WifiConnected,
            activeColor = Color(0xFF4CAF50),
        )

        StatusPill(
            icon = Icons.Outlined.Bluetooth,
            text = if (state.connection != ConnectionStatus.Disconnected) "已连接" else "未连接",
            isActive = state.connection != ConnectionStatus.Disconnected,
            activeColor = Color(0xFF4CAF50),
        )

        if (state.connection != ConnectionStatus.Disconnected) {
            StatusPill(
                icon = Icons.Outlined.BatteryFull,
                text = "${state.batteryLevel}%",
                isActive = true,
                activeColor = Color(0xFF4CAF50),
            )
        }
    }
}

@Composable
private fun StatusPill(
    icon: ImageVector,
    text: String,
    isActive: Boolean,
    activeColor: Color,
) {
    val tint = if (isActive) activeColor else TextSecondary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.6f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, color = tint, fontSize = 12.sp)
    }
}

@Composable
private fun DashboardGrid(
    state: GlassesUiState,
    onToggleControl: (ControlType) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onTranslateClick: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WifiCard(state, modifier = Modifier.weight(1f).height(176.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ExpandableControlCard(
                    title = "亮度控制",
                    icon = Icons.Rounded.WbSunny,
                    iconColor = Color(0xFFFFB74D),
                    value = state.brightness,
                    isExpanded = state.activeControl == ControlType.BRIGHTNESS,
                    onValueChange = onBrightnessChange,
                    onClick = { onToggleControl(ControlType.BRIGHTNESS) },
                )

                ExpandableControlCard(
                    title = "声音控制",
                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                    iconColor = Color(0xFF64B5F6),
                    value = state.volume,
                    isExpanded = state.activeControl == ControlType.VOLUME,
                    onValueChange = onVolumeChange,
                    onClick = { onToggleControl(ControlType.VOLUME) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppServiceCard("Jenius AI", Icons.Rounded.SmartToy, Color(0xFF00ACC1), Modifier.weight(1f))
            AppServiceCard("通知", Icons.Rounded.Notifications, Color(0xFFE57373), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppServiceCard("提词器", Icons.AutoMirrored.Rounded.Article, Color(0xFFBA68C8), Modifier.weight(1f))
            AppServiceCard(
                title = "翻译",
                icon = Icons.Rounded.Translate,
                iconColor = Color(0xFF81C784),
                modifier = Modifier.weight(1f),
                onClick = onTranslateClick,
            )
        }
        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
private fun WifiCard(state: GlassesUiState, modifier: Modifier) {
    val isWifiOk = state.connection == ConnectionStatus.WifiConnected
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (isWifiOk) Color(0xFFE8F5E9) else Color(0xFFF5F5F5),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Wifi,
                    contentDescription = null,
                    tint = if (isWifiOk) Color(0xFF4CAF50) else Color.LightGray,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text("WIFI 管理", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                text = if (isWifiOk) state.wifiName else "尚未连接网络",
                fontSize = 12.sp,
                color = if (isWifiOk) Color.Gray else Color.LightGray,
            )
            if (!isWifiOk) {
                Text("去连接 »", fontSize = 12.sp, color = BrandPurple, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ExpandableControlCard(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    value: Float,
    isExpanded: Boolean,
    onValueChange: (Float) -> Unit,
    onClick: () -> Unit,
) {
    val cardHeight by animateDpAsState(targetValue = if (isExpanded) 120.dp else 82.dp, label = "controlCardHeight")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .clickable { onClick() }
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)

                if (isExpanded) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text("${(value * 100).toInt()}%", fontSize = 12.sp, color = BrandPurple)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isExpanded) {
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    colors = SliderDefaults.colors(
                        thumbColor = BrandPurple,
                        activeTrackColor = BrandPurple,
                        inactiveTrackColor = BrandPurpleLight,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF0F0F0)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(value.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(BrandPurpleLight),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppServiceCard(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBgColor)
            .then(clickableModifier)
            .padding(horizontal = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = title, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
    }
}

@Composable
private fun MainActionButton(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onClick,
            modifier = Modifier.width(200.dp).height(50.dp),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
        ) {
            Text(
                text = "连接眼镜",
                color = Color.White,
                fontWeight = FontWeight.Medium,
            )
        }

        Text(
            "无法连接",
            modifier = Modifier.padding(top = 8.dp),
            fontSize = 12.sp,
            color = BrandPurple,
            textDecoration = TextDecoration.Underline,
        )
    }
}

@Composable
private fun TranslatePage(
    sourceLang: String,
    targetLang: String,
    onBack: () -> Unit,
    onSourceChange: (String) -> Unit,
    onTargetChange: (String) -> Unit,
    onSwap: () -> Unit,
    onBottomTabClick: (Int) -> Unit,
) {
    BackHandler(onBack = onBack)

    Scaffold(
        bottomBar = {
            FloatingBottomNavigationBar(
                selectedIndex = 0,
                onTabClick = {
                    if (it == 0) {
                        onBack()
                    } else {
                        onBottomTabClick(it)
                    }
                },
            )
        },
        containerColor = Color.Transparent,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(BgGradientTop, BgGradientBottom),
                    ),
                )
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "返回",
                    color = BrandPurple,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable(onClick = onBack),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "翻译",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            LanguageDropdownCard(
                title = "源语言",
                selectedCode = sourceLang,
                onSelect = onSourceChange,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onSwap,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("交换", color = BrandPurple)
            }

            Spacer(modifier = Modifier.height(12.dp))

            LanguageDropdownCard(
                title = "目标语言",
                selectedCode = targetLang,
                onSelect = onTargetChange,
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "进入本页已发送 translate_start，离开会发送 translate_stop",
                fontSize = 12.sp,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun LanguageDropdownCard(
    title: String,
    selectedCode: String,
    onSelect: (String) -> Unit,
) {
    val selectedOption = TRANSLATE_LANGUAGES.firstOrNull { it.code == selectedCode }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(14.dp),
    ) {
        Text(title, color = TextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TRANSLATE_LANGUAGES, key = { it.code }) { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedCode == option.code) BrandPurpleLight else Color(0xFFF8F8FA))
                        .clickable { onSelect(option.code) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = option.displayName,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = option.code,
                        color = if (selectedCode == option.code) BrandPurple else TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "当前：${selectedOption?.displayName ?: selectedCode}",
            color = BrandPurple,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun AlbumScreen(
    pageState: AlbumPageState,
    selectedFilter: AlbumFilter,
    mediaList: List<MediaItemUi>,
    showImportBanner: Boolean,
    onConnectClick: () -> Unit,
    onFilterChange: (AlbumFilter) -> Unit,
    onImportClick: () -> Unit,
    onBottomTabClick: (Int) -> Unit,
) {
    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFE9E0FF),
            Color(0xFFF5F3FA),
            Color(0xFFF3F3F3),
        ),
    )

    val bottomOverlayPadding = if (showImportBanner && pageState == AlbumPageState.CONTENT) 156.dp else 92.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomOverlayPadding),
        ) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            AlbumTopBar(
                title = "照片",
                showAction = pageState == AlbumPageState.CONTENT,
                actionText = "选择",
            )

            Spacer(modifier = Modifier.height(20.dp))

            when (pageState) {
                AlbumPageState.DISCONNECTED -> {
                    DisconnectedContent(
                        onConnectClick = onConnectClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }

                AlbumPageState.CONTENT -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        AlbumFilterTabs(
                            selected = selectedFilter,
                            onSelected = onFilterChange,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        MediaGrid(
                            mediaList = mediaList,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
        ) {
            if (showImportBanner && pageState == AlbumPageState.CONTENT) {
                DiscoverBanner(
                    text = "发现5个新图像",
                    onImportClick = onImportClick,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(14.dp))
            }

            FloatingBottomNavigationBar(
                selectedIndex = 1,
                onTabClick = onBottomTabClick,
            )
        }
    }
}

@Composable
private fun AlbumTopBar(
    title: String,
    showAction: Boolean,
    actionText: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Color(0xFF1F1F1F),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )

        if (showAction) {
            Text(
                text = actionText,
                color = Color(0xFF5D5D66),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        } else {
            Spacer(modifier = Modifier.width(40.dp))
        }
    }
}

@Composable
private fun AlbumFilterTabs(
    selected: AlbumFilter,
    onSelected: (AlbumFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilterChip(
            text = "全部",
            selected = selected == AlbumFilter.ALL,
            onClick = { onSelected(AlbumFilter.ALL) },
        )
        FilterChip(
            text = "图片",
            selected = selected == AlbumFilter.IMAGE,
            onClick = { onSelected(AlbumFilter.IMAGE) },
        )
        FilterChip(
            text = "视频",
            selected = selected == AlbumFilter.VIDEO,
            onClick = { onSelected(AlbumFilter.VIDEO) },
        )
    }
}

@Composable
private fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) {
        Brush.horizontalGradient(listOf(Color(0xFF4C18E8), Color(0xFF6A1BFF)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFFF1F0F5), Color(0xFFF1F0F5)))
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .height(38.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Color(0xFF8D8D98),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun DisconnectedContent(
    onConnectClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(110.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Photo,
                contentDescription = null,
                tint = Color(0xFFC7B8F8),
                modifier = Modifier.size(72.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "未连接眼镜",
            color = Color(0xFF43434A),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "请连接您的眼镜以查看和管理相册文件",
            color = Color(0xFF9A9AA5),
            fontSize = 14.sp,
        )

        Spacer(modifier = Modifier.height(28.dp))

        ConnectButton(text = "前往连接", onClick = onConnectClick)
    }
}

@Composable
private fun ConnectButton(
    text: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        modifier = Modifier
            .height(52.dp)
            .background(
                brush = Brush.horizontalGradient(listOf(Color(0xFF5A18F2), Color(0xFF7A1EFF))),
                shape = RoundedCornerShape(999.dp),
            ),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MediaGrid(
    mediaList: List<MediaItemUi>,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {
        items(mediaList, key = { it.id }) { item ->
            MediaGridItem(item = item)
        }
    }
}

@Composable
private fun MediaGridItem(item: MediaItemUi) {
    Box(
        modifier = Modifier
            .aspectRatio(0.92f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFE8E8EC)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFFE6E0FF),
                            Color(0xFFF1EFF8),
                            Color(0xFFDCE6E9),
                        ),
                    ),
                ),
        )

        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(width = 20.dp, height = 16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Face,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}

@Composable
private fun DiscoverBanner(
    text: String,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF5B18F2), Color(0xFF7A1EFF))))
            .padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White)
                .clickable(onClick = onImportClick)
                .padding(horizontal = 22.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "导入",
                color = Color(0xFF5A18F2),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun TopLogoHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(BrandPurple),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "JOVE | 朱庇特智能",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun FloatingBottomNavigationBar(
    selectedIndex: Int,
    onTabClick: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(40.dp))
                .background(Color.White.copy(alpha = 0.96f))
                .padding(vertical = 12.dp, horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(
                icon = Icons.Outlined.Face,
                text = "眼镜",
                isSelected = selectedIndex == 0,
                onClick = { onTabClick(0) },
            )

            NavItem(
                icon = Icons.Outlined.Photo,
                text = "相册",
                isSelected = selectedIndex == 1,
                onClick = { onTabClick(1) },
            )

            NavItem(
                icon = Icons.Outlined.Person,
                text = "我的",
                isSelected = selectedIndex == 2,
                onClick = { onTabClick(2) },
            )
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (isSelected) BrandPurple else TextPrimary

    val modifier = if (isSelected) {
        Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(BrandPurpleLight)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    } else {
        Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor,
        )
    }
}

@Composable
private fun DeviceRow(
    device: ScannedBleDevice,
    isBinding: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isBinding, onClick = onClick),
        colors = CardDefaults.cardColors(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("${device.address} | RSSI ${device.rssi}")
            }
            Text(if (isBinding) "绑定中" else "绑定")
        }
    }
}

private fun requiredBlePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun hasBlePermission(context: Context): Boolean =
    requiredBlePermissions().all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

private fun hasBleScanPermission(context: Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Manifest.permission.BLUETOOTH_SCAN
    } else {
        Manifest.permission.ACCESS_FINE_LOCATION
    }
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

@Suppress("DEPRECATION")
@SuppressLint("MissingPermission")
private fun writeDescriptorCompat(
    gatt: BluetoothGatt,
    descriptor: BluetoothGattDescriptor,
    value: ByteArray,
): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
    } else {
        descriptor.value = value
        gatt.writeDescriptor(descriptor)
    }
}

@Suppress("DEPRECATION")
@SuppressLint("MissingPermission")
private fun writeCharacteristicCompat(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray,
    writeType: Int,
): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.writeCharacteristic(characteristic, value, writeType) ==
            BluetoothStatusCodes.SUCCESS
    } else {
        characteristic.writeType = writeType
        characteristic.value = value
        gatt.writeCharacteristic(characteristic)
    }
}

@SuppressLint("MissingPermission")
private fun readCharacteristicCompat(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic,
): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.readCharacteristic(characteristic)
    } else {
        gatt.readCharacteristic(characteristic)
    }
}

private fun refreshGattDeviceCache(gatt: BluetoothGatt): Boolean {
    return runCatching {
        val method = gatt.javaClass.getMethod("refresh")
        (method.invoke(gatt) as? Boolean) == true
    }.getOrDefault(false)
}

private fun ByteArray.toHexString(): String =
    joinToString(separator = " ") { "%02X".format(it) }

@Preview(showBackground = true)
@Composable
private fun BindScreenPreview() {
    JoveTheme(darkTheme = false, dynamicColor = false) {
        BindScreen()
    }
}
