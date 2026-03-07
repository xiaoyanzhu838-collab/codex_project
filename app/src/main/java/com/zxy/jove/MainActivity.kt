package com.zxy.jove

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zxy.jove.ui.theme.JoveTheme
import kotlinx.coroutines.delay
import java.util.UUID

private const val TARGET_NAME_KEYWORD = "星彩"
private const val SCAN_DURATION_MS = 12_000L

private val SERVICE_UUID: UUID = UUID.fromString("3f6d8b2e-7f61-4c85-b1d2-6e9c0f4a12d7")
private val CHARACTERISTIC_NOTIFY_UUID: UUID = UUID.fromString("a1c4e5f6-9b32-4d8a-8c7f-2b9d4e6f1a23")
private val CHARACTERISTIC_WRITE_UUID: UUID = UUID.fromString("b7d3c1a9-5e24-4fa1-9d6b-3c8e2f5a7b41")
private val DESCRIPTOR_CCC_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

private val BIND_PAYLOAD = "BIND".toByteArray(Charsets.UTF_8)

val BrandPurple = Color(0xFF5721D2)
val BrandPurpleLight = Color(0xFFF1E6FF)
val TextPrimary = Color(0xFF1A1A1A)
val TextSecondary = Color(0xFF888888)
val BgGradientTop = Color(0xFFE4D3F5)
val BgGradientBottom = Color(0xFFF8F8FA)

data class ScannedBleDevice(
    val name: String,
    val address: String,
    val rssi: Int
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JoveTheme(darkTheme = false, dynamicColor = false) {
                JoveBindGlassesScreen()
            }
        }
    }
}

@Composable
fun JoveBindGlassesScreen() {
    val context = LocalContext.current
    val activity = context as? Activity

    val bluetoothAdapter = remember {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    val devices = remember { mutableStateListOf<ScannedBleDevice>() }
    var isScanning by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("点击“绑定眼镜”开始搜索 $TARGET_NAME_KEYWORD 设备") }
    var pendingStartAfterEnable by remember { mutableStateOf(false) }
    var isBinding by remember { mutableStateOf(false) }
    var connectedAddress by remember { mutableStateOf<String?>(null) }

    val activeGatt = remember { mutableStateOf<BluetoothGatt?>(null) }
    val notifyCharacteristic = remember { mutableStateOf<BluetoothGattCharacteristic?>(null) }
    val writeCharacteristic = remember { mutableStateOf<BluetoothGattCharacteristic?>(null) }

    val scanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val rawName = result.device.name ?: result.scanRecord?.deviceName.orEmpty()
                if (!rawName.contains(TARGET_NAME_KEYWORD)) return

                val found = ScannedBleDevice(
                    name = rawName,
                    address = result.device.address.orEmpty(),
                    rssi = result.rssi
                )
                val index = devices.indexOfFirst { it.address == found.address }
                if (index >= 0) {
                    devices[index] = found
                } else {
                    devices.add(found)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                isScanning = false
                statusMessage = "搜索失败，错误码: $errorCode"
            }
        }
    }

    fun closeGatt() {
        activeGatt.value?.close()
        activeGatt.value = null
        notifyCharacteristic.value = null
        writeCharacteristic.value = null
    }

    fun stopScan() {
        if (hasBleScanPermission(context)) {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        }
        isScanning = false
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            statusMessage = "设备不支持蓝牙"
            return
        }
        if (!adapter.isEnabled) {
            statusMessage = "蓝牙未开启"
            return
        }
        if (!hasBleScanPermission(context)) {
            statusMessage = "缺少蓝牙权限"
            return
        }

        devices.clear()
        isScanning = true
        statusMessage = "正在搜索名称包含“$TARGET_NAME_KEYWORD”的设备..."

        val filters = listOf(
            ScanFilter.Builder().build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        adapter.bluetoothLeScanner?.startScan(filters, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun bindDevice(deviceAddress: String) {
        if (!hasBlePermission(context)) {
            statusMessage = "缺少蓝牙权限"
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            statusMessage = "蓝牙不可用"
            return
        }

        val targetDevice = adapter.getRemoteDevice(deviceAddress)
        stopScan()
        closeGatt()

        isBinding = true
        connectedAddress = deviceAddress
        statusMessage = "正在连接设备..."

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    gatt.close()
                    activeGatt.value = null
                    isBinding = false
                    connectedAddress = null
                    statusMessage = "连接失败，status=$status"
                    return
                }

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        activeGatt.value = gatt
                        statusMessage = "连接成功，正在发现服务..."
                        gatt.discoverServices()
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        gatt.close()
                        if (connectedAddress == deviceAddress) {
                            isBinding = false
                            connectedAddress = null
                            notifyCharacteristic.value = null
                            writeCharacteristic.value = null
                            activeGatt.value = null
                            statusMessage = "设备已断开"
                        }
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    isBinding = false
                    statusMessage = "服务发现失败，status=$status"
                    return
                }

                val service = gatt.getService(SERVICE_UUID)
                if (service == null) {
                    isBinding = false
                    statusMessage = "未找到目标服务"
                    return
                }

                val notifyChar = service.getCharacteristic(CHARACTERISTIC_NOTIFY_UUID)
                val writeChar = service.getCharacteristic(CHARACTERISTIC_WRITE_UUID)
                if (notifyChar == null || writeChar == null) {
                    isBinding = false
                    statusMessage = "未找到目标特征值"
                    return
                }

                notifyCharacteristic.value = notifyChar
                writeCharacteristic.value = writeChar

                val notifyEnabled = gatt.setCharacteristicNotification(notifyChar, true)
                if (!notifyEnabled) {
                    isBinding = false
                    statusMessage = "开启通知失败"
                    return
                }

                val ccc = notifyChar.getDescriptor(DESCRIPTOR_CCC_UUID)
                if (ccc == null) {
                    isBinding = false
                    statusMessage = "通知描述符不存在"
                    return
                }

                ccc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                if (!gatt.writeDescriptor(ccc)) {
                    isBinding = false
                    statusMessage = "写入通知描述符失败"
                }
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (descriptor.uuid != DESCRIPTOR_CCC_UUID) return
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    isBinding = false
                    statusMessage = "通知订阅失败，status=$status"
                    return
                }

                val writeChar = writeCharacteristic.value
                if (writeChar == null) {
                    isBinding = false
                    statusMessage = "写特征值不存在"
                    return
                }

                writeChar.value = BIND_PAYLOAD
                val wrote = gatt.writeCharacteristic(writeChar)
                if (!wrote) {
                    isBinding = false
                    statusMessage = "发送绑定指令失败"
                } else {
                    statusMessage = "已发送绑定指令，等待设备确认..."
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (characteristic.uuid != CHARACTERISTIC_WRITE_UUID) return
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    isBinding = false
                    statusMessage = "绑定写入失败，status=$status"
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (characteristic.uuid != CHARACTERISTIC_NOTIFY_UUID) return
                val value = characteristic.value.orEmpty()
                val text = value.toString(Charsets.UTF_8)
                isBinding = false
                statusMessage = if (text.contains("success", ignoreCase = true) || text.contains("ok", ignoreCase = true)) {
                    "绑定成功"
                } else {
                    "收到设备通知: ${text.ifEmpty { value.toHexString() }}"
                }
            }
        }

        activeGatt.value = targetDevice.connectGatt(context, false, gattCallback)
    }

    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResult ->
        val allGranted = grantResult.values.all { it }
        if (allGranted) {
            if (bluetoothAdapter?.isEnabled == true) {
                startScan()
            } else {
                pendingStartAfterEnable = true
                activity?.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        } else {
            statusMessage = "请授予蓝牙权限后重试"
        }
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
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

    LaunchedEffect(isScanning) {
        if (isScanning) {
            delay(SCAN_DURATION_MS)
            stopScan()
            statusMessage = if (devices.isEmpty()) {
                "未搜索到名称包含“$TARGET_NAME_KEYWORD”的设备"
            } else {
                "搜索完成，共 ${devices.size} 台设备"
            }
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            stopScan()
            closeGatt()
        }
    }

    Scaffold(
        bottomBar = { FloatingBottomNavigationBar() },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(BgGradientTop, BgGradientBottom)
                    )
                )
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(64.dp))
                TopLogoHeader()
                Spacer(modifier = Modifier.weight(0.8f))

                Box(
                    modifier = Modifier.size(300.dp, 200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.glasses),
                        contentDescription = "Glasses S1",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "JOVE Glasses S1",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        when {
                            bluetoothAdapter == null -> {
                                statusMessage = "设备不支持蓝牙"
                            }

                            !hasBlePermission(context) -> {
                                requestPermissionsLauncher.launch(requiredBlePermissions())
                            }

                            bluetoothAdapter.isEnabled -> {
                                startScan()
                            }

                            else -> {
                                pendingStartAfterEnable = true
                                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            }
                        }
                    },
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                    modifier = Modifier
                        .width(240.dp)
                        .height(56.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Text(
                        text = if (isScanning) "搜索中..." else "绑定眼镜",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = statusMessage,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(devices, key = { it.address }) { device ->
                        DeviceRow(
                            device = device,
                            isBinding = isBinding && connectedAddress == device.address,
                            onBindClick = {
                                Toast.makeText(
                                    context,
                                    "开始绑定: ${device.name}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                bindDevice(device.address)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: ScannedBleDevice,
    isBinding: Boolean,
    onBindClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.88f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${device.address}  ·  RSSI ${device.rssi}",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        Button(
            onClick = onBindClick,
            enabled = !isBinding,
            colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            modifier = Modifier
                .width(84.dp)
                .height(40.dp)
        ) {
            Text(
                text = if (isBinding) "绑定中" else "绑定",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun TopLogoHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(BrandPurple),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "JOVE | 朱庇特智能",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun FloatingBottomNavigationBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(40.dp))
                .background(Color.White)
                .padding(vertical = 12.dp, horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            var selectedIndex by remember { mutableStateOf(0) }

            NavItem(
                icon = Icons.Outlined.Face,
                text = "眼镜",
                isSelected = selectedIndex == 0,
                onClick = { selectedIndex = 0 }
            )

            NavItem(
                icon = Icons.Outlined.Photo,
                text = "相册",
                isSelected = selectedIndex == 1,
                onClick = { selectedIndex = 1 }
            )

            NavItem(
                icon = Icons.Outlined.Person,
                text = "我的",
                isSelected = selectedIndex == 2,
                onClick = { selectedIndex = 2 }
            )
        }
    }
}

@Composable
fun NavItem(
    icon: ImageVector,
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
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
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor
        )
    }
}

private fun requiredBlePermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )
} else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

private fun hasBlePermission(context: Context): Boolean {
    return requiredBlePermissions().all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

private fun hasBleScanPermission(context: Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Manifest.permission.BLUETOOTH_SCAN
    } else {
        Manifest.permission.ACCESS_FINE_LOCATION
    }
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun ByteArray.toHexString(): String =
    joinToString(separator = " ") { eachByte -> "%02X".format(eachByte) }

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun JoveBindGlassesScreenPreview() {
    JoveTheme(darkTheme = false, dynamicColor = false) {
        JoveBindGlassesScreen()
    }
}
