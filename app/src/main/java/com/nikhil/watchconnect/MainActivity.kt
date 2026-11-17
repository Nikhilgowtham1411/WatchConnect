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
import androidx.compose.foundation.text.BasicTextField
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
private val Danger = Color(0xFFFF7185)

private data class WatchDevice(val name: String, val address: String, val rssi: Int)
private data class FriendRequest(val name: String, val address: String, val incoming: Boolean)
private data class ChatItem(val text: String, val mine: Boolean, val preview: Boolean = true)
private enum class AppPage { NEARBY, REQUESTS, FRIENDS, CHAT }

class MainActivity : ComponentActivity() {
    private val devices = mutableStateListOf<WatchDevice>()
    private val requests = mutableStateListOf<FriendRequest>()
    private val friends = mutableStateListOf<WatchDevice>()
    private val messages = mutableStateListOf<ChatItem>()
    private var scanning by mutableStateOf(false)
    private var status by mutableStateOf("Ready to discover nearby devices")
    private var selectedAddress by mutableStateOf<String?>(null)
    private var selectedFriend by mutableStateOf<WatchDevice?>(null)
    private var page by mutableStateOf(AppPage.NEARBY)
    private var draft by mutableStateOf("")
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
                WatchConnectApp(
                    devices = devices, requests = requests, friends = friends, messages = messages,
                    scanning = scanning, status = status, selectedAddress = selectedAddress,
                    page = page, selectedFriend = selectedFriend, draft = draft,
                    onPage = { page = it }, onScan = ::requestScan, onStop = ::stopScan,
                    onPair = ::pair, onInvite = ::sendInvite, onAccept = ::acceptRequest,
                    onDecline = ::declineRequest, onOpenChat = ::openChat,
                    onDraft = { draft = it }, onSend = ::sendMessage,
                    onReaction = ::sendReaction
                )
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
                status = "Android pairing request sent for ${device.name}"
                Toast.makeText(this, "Pairing request sent to Android", Toast.LENGTH_SHORT).show()
            }
        } catch (_: SecurityException) { status = "Bluetooth connect permission is required" }
        catch (e: Exception) { status = "Pairing failed: ${e.localizedMessage}" }
    }

    private fun sendInvite(device: WatchDevice) {
        if (requests.none { it.address == device.address }) {
            requests.add(FriendRequest(device.name, device.address, incoming = false))
        }
        status = "Invite preview created. It has not been transmitted to the other watch."
        page = AppPage.REQUESTS
    }

    private fun acceptRequest(request: FriendRequest) {
        if (friends.none { it.address == request.address }) {
            friends.add(WatchDevice(request.name, request.address, 0))
        }
        requests.removeAll { it.address == request.address }
        status = "Friend added locally. Peer-to-peer invite delivery is not connected yet."
        page = AppPage.FRIENDS
    }

    private fun declineRequest(request: FriendRequest) {
        requests.removeAll { it.address == request.address }
    }

    private fun openChat(friend: WatchDevice) {
        selectedFriend = friend
        messages.clear()
        messages.add(ChatItem("Chat preview with ${friend.name}. Messages stay on this phone until peer transport is implemented.", false))
        page = AppPage.CHAT
    }

    private fun sendMessage() {
        val value = draft.trim()
        if (value.isNotEmpty()) {
            messages.add(ChatItem(value, true))
            messages.add(ChatItem("Preview only — not sent to the other watch.", false))
            draft = ""
        }
    }

    private fun sendReaction(reaction: String) {
        messages.add(ChatItem(reaction, true))
        messages.add(ChatItem("Reaction preview only — not delivered.", false))
    }

    override fun onDestroy() { stopScan(); super.onDestroy() }
}

@Composable
private fun WatchConnectApp(
    devices: List<WatchDevice>, requests: List<FriendRequest>, friends: List<WatchDevice>,
    messages: List<ChatItem>, scanning: Boolean, status: String, selectedAddress: String?,
    page: AppPage, selectedFriend: WatchDevice?, draft: String,
    onPage: (AppPage) -> Unit, onScan: () -> Unit, onStop: () -> Unit,
    onPair: (WatchDevice) -> Unit, onInvite: (WatchDevice) -> Unit,
    onAccept: (FriendRequest) -> Unit, onDecline: (FriendRequest) -> Unit,
    onOpenChat: (WatchDevice) -> Unit, onDraft: (String) -> Unit,
    onSend: () -> Unit, onReaction: (String) -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = Ink) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("WATCHCONNECT", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Your watch, your connection.", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                }
                Text("⌚", fontSize = 30.sp)
            }
            Spacer(Modifier.height(14.dp))
            if (page != AppPage.CHAT) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NavTab("Nearby", page == AppPage.NEARBY) { onPage(AppPage.NEARBY) }
                    NavTab("Requests", page == AppPage.REQUESTS, requests.size) { onPage(AppPage.REQUESTS) }
                    NavTab("Friends", page == AppPage.FRIENDS) { onPage(AppPage.FRIENDS) }
                }
                Spacer(Modifier.height(14.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("‹ Back", color = Accent, modifier = Modifier.clickable { onPage(AppPage.FRIENDS) }.padding(end = 14.dp))
                    Text(selectedFriend?.name ?: "Chat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                }
                Spacer(Modifier.height(14.dp))
            }
            when (page) {
                AppPage.NEARBY -> NearbyPage(devices, scanning, status, selectedAddress, onScan, onStop, onPair, onInvite)
                AppPage.REQUESTS -> RequestsPage(requests, onAccept, onDecline)
                AppPage.FRIENDS -> FriendsPage(friends, onOpenChat)
                AppPage.CHAT -> ChatPage(selectedFriend, messages, draft, onDraft, onSend, onReaction)
            }
        }
    }
}

@Composable
private fun NavTab(label: String, selected: Boolean, count: Int = 0, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) Accent.copy(alpha = 0.2f) else Panel,
        shape = RoundedCornerShape(50)
    ) {
        Text(if (count > 0) "$label ($count)" else label, color = if (selected) Accent else Muted,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NearbyPage(
    devices: List<WatchDevice>, scanning: Boolean, status: String, selectedAddress: String?,
    onScan: () -> Unit, onStop: () -> Unit, onPair: (WatchDevice) -> Unit, onInvite: (WatchDevice) -> Unit
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("DISCOVER NEARBY", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text(if (scanning) "Looking for Bluetooth devices…" else "Find a watch friend", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(status, color = Muted, fontSize = 12.sp)
                if (scanning) OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Stop scanning") }
                else Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text("Scan for watches") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Nearby devices", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            Text("${devices.size} found", color = Muted, fontSize = 12.sp)
        }
        if (devices.isEmpty()) {
            EmptyState("⌚", "No devices found yet", "Scan to discover Bluetooth devices around you.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(devices, key = { it.address }) { device ->
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⌚", fontSize = 25.sp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(device.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text("Signal ${device.rssi} dBm", color = Muted, fontSize = 11.sp)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onPair(device) }, modifier = Modifier.weight(1f)) { Text(if (selectedAddress == device.address) "Pair requested" else "Pair") }
                                Button(onClick = { onInvite(device) }, modifier = Modifier.weight(1f)) { Text("Friend request") }
                            }
                        }
                    }
                }
            }
        }
        Text("A detected Bluetooth device is not necessarily a WatchConnect peer.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun RequestsPage(requests: List<FriendRequest>, onAccept: (FriendRequest) -> Unit, onDecline: (FriendRequest) -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Friend requests", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        if (requests.isEmpty()) EmptyState("✉", "No friend requests", "Requests from compatible WatchConnect devices will appear here.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(requests, key = { it.address }) { request ->
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(request.name, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(if (request.incoming) "Incoming request" else "Outgoing invite preview — not transmitted", color = Muted, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onAccept(request) }, modifier = Modifier.weight(1f)) { Text("✓ Accept") }
                            OutlinedButton(onClick = { onDecline(request) }, modifier = Modifier.weight(1f)) { Text("✕ Cancel") }
                        }
                    }
                }
            }
        }
        Text("Accepting here adds a local friend entry only. Remote friend-request delivery is not implemented yet.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun FriendsPage(friends: List<WatchDevice>, onOpenChat: (WatchDevice) -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Your watch friends", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        if (friends.isEmpty()) EmptyState("♡", "No friends yet", "Discover a device and create a friend-request preview.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(friends, key = { it.address }) { friend ->
                Card(Modifier.fillMaxWidth().clickable { onOpenChat(friend) }, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("⌚", fontSize = 26.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(friend.name, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Saved friend · live status unavailable", color = Muted, fontSize = 11.sp)
                        }
                        Text("Chat  ›", color = Accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatPage(friend: WatchDevice?, messages: List<ChatItem>, draft: String, onDraft: (String) -> Unit, onSend: () -> Unit, onReaction: (String) -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text("Chat with ${friend?.name ?: "friend"}", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Local interaction preview · messages are not transmitted", color = Muted, fontSize = 11.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("👋", "❤️", "😂", "👍", "🔥").forEach { emoji ->
                Surface(Modifier.clickable { onReaction(emoji) }, color = Panel, shape = CircleShape) {
                    Text(emoji, modifier = Modifier.padding(10.dp), fontSize = 18.sp)
                }
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (item.mine) Arrangement.End else Arrangement.Start) {
                    Surface(color = if (item.mine) Accent.copy(alpha = 0.22f) else Panel, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.widthIn(max = 280.dp).padding(11.dp)) {
                            Text(item.text, color = Color.White, fontSize = 14.sp)
                            if (item.preview) Text("PREVIEW", color = Muted, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(24.dp)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(value = draft, onValueChange = onDraft, modifier = Modifier.weight(1f), singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                decorationBox = { inner -> Box { if (draft.isEmpty()) Text("Type a preview message…", color = Muted, fontSize = 13.sp); inner() } })
            Text("Send", color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onSend).padding(8.dp))
        }
        Text("Nudge / vibration requires a real peer connection and compatible watch support.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun EmptyState(icon: String, title: String, subtitle: String) {
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(icon, fontSize = 38.sp)
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
    }
}
