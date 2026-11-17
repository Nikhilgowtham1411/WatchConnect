package com.nikhil.watchconnect

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF090B10)
private val Panel = Color(0xFF151922)
private val Muted = Color(0xFF929BAA)
private val Accent = Color(0xFF9B8CFF)
private val Good = Color(0xFF62D6A5)

private data class WatchDevice(val name: String, val address: String, val rssi: Int)

class MainActivity : ComponentActivity() {
    private val devices = mutableStateListOf<WatchDevice>()
    private var scanning by mutableStateOf(false)
    private var status by mutableStateOf("Ready to discover nearby devices")
    private var selectedAddress by mutableStateOf<String?>(null)
    private var adapter: BluetoothAdapter? = null

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) startScan() else status = "Bluetooth permissions are required to scan"
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(type: Int, result: ScanResult) { addDevice(result.device, result.rssi) }
        override fun onBatchScanResults(results: MutableList<ScanResult>) { results.forEach { addDevice(it.device, it.rssi) } }
        override fun onScanFailed(code: Int) { scanning = false; status = "Scan failed (code $code)" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, secondary = Good, background = Ink, surface = Panel)) {
                WatchConnectScreen(devices, scanning, status, selectedAddress, ::requestScan, ::pair, ::stopScan)
            }
        }
    }

    private fun requestScan() {
        val bt = adapter
        if (bt == null) { status = "Bluetooth is not supported on this phone"; return }
        if (!bt.isEnabled) { status = "Turn on Bluetooth and scan again"; return }
        val required = if (Build.VERSION.SDK_INT >= 31)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = required.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray()) else startScan()
    }

    @Suppress("MissingPermission")
    private fun startScan() {
        try {
            val scanner = adapter?.bluetoothLeScanner
            if (scanner == null) { status = "BLE scanning is unavailable"; return }
            devices.clear()
            selectedAddress = null
            scanner.startScan(callback)
            scanning = true
            status = "Scanning nearby Bluetooth devices…"
        } catch (_: SecurityException) { status = "Bluetooth permission denied" }
        catch (e: Exception) { status = "Could not start scan: ${e.localizedMessage}" }
    }

    @Suppress("MissingPermission")
    private fun stopScan() {
        try { adapter?.bluetoothLeScanner?.stopScan(callback) } catch (_: SecurityException) {}
        scanning = false
        if (status.startsWith("Scanning")) status = "Scan stopped"
    }

    @Suppress("MissingPermission")
    private fun addDevice(device: BluetoothDevice, rssi: Int) {
        val address = try { device.address } catch (_: SecurityException) { return }
        val name = try { device.name?.takeIf { it.isNotBlank() } ?: "Unnamed Bluetooth device" }
                   catch (_: SecurityException) { "Bluetooth device" }
        val item = WatchDevice(name, address, rssi)
        val index = devices.indexOfFirst { it.address == address }
        if (index >= 0) devices[index] = item else devices.add(item)
        devices.sortByDescending { it.rssi }
    }

    @Suppress("MissingPermission")
    private fun pair(device: WatchDevice) {
        selectedAddress = device.address
        try {
            val remote = adapter?.getRemoteDevice(device.address)
            if (remote?.bondState == BluetoothDevice.BOND_BONDED) {
                status = "Already paired with ${device.name}"
            } else {
                remote?.createBond()
                status = "Pairing request sent to Android for ${device.name}"
                Toast.makeText(this, "Pairing request sent to Android", Toast.LENGTH_SHORT).show()
            }
        } catch (_: SecurityException) { status = "Bluetooth connect permission is required" }
        catch (e: Exception) { status = "Pairing failed: ${e.localizedMessage}" }
    }

    override fun onDestroy() { stopScan(); super.onDestroy() }
}

@Composable
private fun WatchConnectScreen(
    devices: List<WatchDevice>,
    scanning: Boolean,
    status: String,
    selectedAddress: String?,
    onScan: () -> Unit,
    onPair: (WatchDevice) -> Unit,
    onStop: () -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = Ink) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("WATCHCONNECT", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Your watch,\nyour connection.", color = Color.White, fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.size(54.dp).background(Panel, CircleShape), contentAlignment = Alignment.Center) { Text("⌚", fontSize = 27.sp) }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("DEVICE DISCOVERY", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text(if (scanning) "Looking around you…" else "Find nearby devices", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(status, color = Muted, fontSize = 13.sp)
                    if (scanning) OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Stop scanning") }
                    else Button(onClick = onScan, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) {
                        Text("Scan for watches", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nearby devices", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${devices.size} found", color = Muted, fontSize = 12.sp)
            }
            if (devices.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⌚", fontSize = 40.sp)
                        Text("No devices found yet", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Keep your watch nearby and discoverable.", color = Muted, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(devices, key = { it.address }) { device ->
                        Card(Modifier.fillMaxWidth().clickable { onPair(device) }, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                            Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(44.dp).background(Color(0xFF242B3A), CircleShape), contentAlignment = Alignment.Center) { Text("⌚", fontSize = 23.sp) }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(device.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text("Signal ${device.rssi} dBm", color = Muted, fontSize = 11.sp)
                                }
                                Text(if (selectedAddress == device.address) "Selected" else "Pair", color = if (selectedAddress == device.address) Good else Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Text("Full features depend on each watch’s Bluetooth services and manufacturer support.", color = Muted, fontSize = 11.sp)
        }
    }
}
