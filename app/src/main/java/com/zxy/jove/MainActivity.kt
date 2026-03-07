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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zxy.jove.ui.theme.JoveTheme
import kotlinx.coroutines.delay
import java.util.UUID

private const val TAG = "JOVE_PHONE_BLE"
private const val TARGET_NAME_KEYWORD = "星彩"
private const val SCAN_DURATION_MS = 12_000L
private const val BIND_TIMEOUT_MS = 10_000L
private const val SEND_BIND_FALLBACK_DELAY_MS = 700L
private const val READ_REPLY_FALLBACK_DELAY_MS = 900L

private val SERVICE_UUID: UUID = UUID.fromString("3f6d8b2e-7f61-4c85-b1d2-6e9c0f4a12d7")
private val NOTIFY_CHAR_UUID: UUID = UUID.fromString("a1c4e5f6-9b32-4d8a-8c7f-2b9d4e6f1a23")
private val WRITE_CHAR_UUID: UUID = UUID.fromString("b7d3c1a9-5e24-4fa1-9d6b-3c8e2f5a7b41")
private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
private val BIND_PAYLOAD = "BIND".toByteArray(Charsets.UTF_8)

data class ScannedBleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
)

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

    val devices = remember { mutableStateListOf<ScannedBleDevice>() }
    val activeGatt = remember { mutableStateOf<BluetoothGatt?>(null) }
    val notifyCharacteristic = remember { mutableStateOf<BluetoothGattCharacteristic?>(null) }
    val writeCharacteristic = remember { mutableStateOf<BluetoothGattCharacteristic?>(null) }

    var statusMessage by remember { mutableStateOf("点击开始扫描并绑定眼镜") }
    var isScanning by remember { mutableStateOf(false) }
    var isBinding by remember { mutableStateOf(false) }
    var bindingAddress by remember { mutableStateOf<String?>(null) }
    var pendingStartAfterEnable by remember { mutableStateOf(false) }
    var bindSessionId by remember { mutableStateOf(0) }
    var bindCommandSent by remember { mutableStateOf(false) }
    var bindFinished by remember { mutableStateOf(false) }

    fun log(message: String) {
        Log.d(TAG, message)
    }

    fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    fun clearGattState() {
        activeGatt.value = null
        notifyCharacteristic.value = null
        writeCharacteristic.value = null
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
    }

    fun completeBinding(message: String) {
        bindFinished = true
        isBinding = false
        statusMessage = message
        log("Binding completed: $message")
    }

    fun failBinding(message: String) {
        log("Binding failed: $message")
        closeGatt()
        resetBindingFlags()
        statusMessage = message
    }

    fun handleReply(bytes: ByteArray) {
        val text = bytes.decodeToString()
        val display = text.ifEmpty { bytes.toHexString() }
        if (text.contains("success", ignoreCase = true) || text.contains("ok", ignoreCase = true)) {
            completeBinding("绑定成功")
        } else {
            completeBinding("收到设备回复: $display")
        }
    }

    @SuppressLint("MissingPermission")
    fun requestReplyRead(gatt: BluetoothGatt, reason: String) {
        val notifyChar = notifyCharacteristic.value ?: return
        val ok = readCharacteristicCompat(gatt, notifyChar)
        log("Read fallback($reason): $ok")
    }

    @SuppressLint("MissingPermission")
    fun sendBindCommand(gatt: BluetoothGatt, reason: String) {
        if (bindCommandSent || bindFinished) return
        val writeChar = writeCharacteristic.value ?: run {
            failBinding("写特征不存在")
            return
        }

        val ok = writeCharacteristicCompat(gatt, writeChar, BIND_PAYLOAD)
        if (!ok) {
            failBinding("发送绑定指令失败")
            return
        }

        bindCommandSent = true
        statusMessage = "已发送绑定指令，等待设备响应..."
        log("BIND sent via $reason")

        val sessionId = bindSessionId
        mainHandler.postDelayed({
            runOnMain {
                if (isBinding && !bindFinished && sessionId == bindSessionId && activeGatt.value == gatt) {
                    requestReplyRead(gatt, "post-bind")
                }
            }
        }, READ_REPLY_FALLBACK_DELAY_MS)
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
                    log("Connection state: status=$status state=$newState")
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        runCatching { gatt.close() }
                        clearGattState()
                        resetBindingFlags()
                        statusMessage = "连接失败: $status"
                        return@runOnMain
                    }

                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            activeGatt.value = gatt
                            gatt.requestMtu(247)
                            statusMessage = "连接成功，发现服务中..."
                            gatt.discoverServices()
                        }

                        BluetoothProfile.STATE_DISCONNECTED -> {
                            runCatching { gatt.close() }
                            if (bindingAddress == address) {
                                clearGattState()
                                resetBindingFlags()
                                statusMessage = "设备已断开"
                            }
                        }
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                log("MTU changed: mtu=$mtu status=$status")
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                runOnMain {
                    log("Services discovered: $status")
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
                        failBinding("订阅通知失败: $status")
                        return@runOnMain
                    }
                    sendBindCommand(gatt, "descriptor-callback")
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
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        failBinding("绑定写入失败: $status")
                        return@runOnMain
                    }

                    val sessionId = bindSessionId
                    mainHandler.postDelayed({
                        runOnMain {
                            if (isBinding && !bindFinished && sessionId == bindSessionId && activeGatt.value == gatt) {
                                requestReplyRead(gatt, "write-callback")
                            }
                        }
                    }, READ_REPLY_FALLBACK_DELAY_MS)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (characteristic.uuid == NOTIFY_CHAR_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                    runOnMain { handleReply(characteristic.value ?: byteArrayOf()) }
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) {
                if (characteristic.uuid == NOTIFY_CHAR_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                    runOnMain { handleReply(value) }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                if (characteristic.uuid == NOTIFY_CHAR_UUID) {
                    runOnMain { handleReply(characteristic.value ?: byteArrayOf()) }
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                if (characteristic.uuid == NOTIFY_CHAR_UUID) {
                    runOnMain { handleReply(value) }
                }
            }
        }

        activeGatt.value = adapter.getRemoteDevice(address).connectGatt(context, false, callback)
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (pendingStartAfterEnable) {
                pendingStartAfterEnable = false
                startScan()
            }
        } else {
            pendingStartAfterEnable = false
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
            statusMessage = if (devices.isEmpty()) "未发现可绑定设备" else "扫描完成，点击设备开始绑定"
        }
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

    DisposableEffect(Unit) {
        onDispose {
            stopScan()
            closeGatt()
        }
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Image(
                painter = painterResource(id = R.mipmap.glasses),
                contentDescription = "glasses",
                modifier = Modifier.size(240.dp, 150.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("JOVE Glasses S1", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
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
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text(if (isScanning) "扫描中..." else "开始扫描")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(statusMessage, modifier = Modifier.fillMaxWidth())
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
        }
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

@SuppressLint("MissingPermission")
private fun writeCharacteristicCompat(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray,
): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
            BluetoothStatusCodes.SUCCESS
    } else {
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
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

private fun ByteArray.toHexString(): String =
    joinToString(separator = " ") { "%02X".format(it) }

@Preview(showBackground = true)
@Composable
private fun BindScreenPreview() {
    JoveTheme(darkTheme = false, dynamicColor = false) {
        BindScreen()
    }
}
