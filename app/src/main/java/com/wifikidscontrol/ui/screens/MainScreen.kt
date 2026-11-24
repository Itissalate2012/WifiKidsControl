package com.wifikidscontrol.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.dp
import com.wifikidscontrol.R
import com.wifikidscontrol.model.Device
import com.wifikidscontrol.model.DeviceProfile
import com.wifikidscontrol.network.RouterConfig
import com.wifikidscontrol.network.RouterType
import com.wifikidscontrol.repository.DeviceRepository
import com.wifikidscontrol.ui.viewmodel.MainViewModel
import com.wifikidscontrol.ui.theme.Theme
import kotlinx.coroutines.launch

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val routerStatus by viewModel.routerStatus.collectAsStateWithLifecycle()
    val useLocalFallback by viewModel.useLocalFallback.collectAsStateWithLifecycle()
    val showRouterSetup by viewModel.showRouterSetup.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val routerConfig by viewModel.routerConfig.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WifiKidsControl", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Theme.colorScheme.primaryContainer
                ),
                actions = {
                    // Router status indicator
                    Row(modifier = androidx.compose.ui.Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        RouterStatusIndicator(status = routerStatus, useLocalFallback = useLocalFallback)
                        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(8.dp))
                        if (routerConfig != null) {
                            IconButton(onClick = { viewModel.toggleRouterSetup(true) }) {
                                Icon(painterResource(id = R.drawable.ic_settings), contentDescription = "Settings")
                            }
                        } else {
                            IconButton(onClick = { viewModel.toggleRouterSetup(true) }) {
                                Icon(painterResource(id = R.drawable.ic_router), contentDescription = "Setup Router")
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                val tabs = listOf("Tous", "En ligne", "Bloqués", "Enfants", "Invités")
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        icon = { Icon(painterResource(id = tabIcon(index)), contentDescription = label) },
                        label = { Text(label) },
                        selected = selectedTab == index,
                        onClick = { viewModel.setTab(index) }
                    )
                }
            }
        }
    ) { padding ->
        if (showRouterSetup) {
            RouterSetupScreen(
                onSave = { config ->
                    viewModel.setRouterConfig(config)
                    viewModel.toggleRouterSetup(false)
                },
                onCancel = { viewModel.toggleRouterSetup(false) },
                existingConfig = routerConfig
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Stats row
                StatsRow(stats = stats)

                // Device list
                DeviceListScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun RouterStatusIndicator(
    status: DeviceRepository.RouterConnectionStatus,
    useLocalFallback: Boolean
) {
    val (color, text) = when (status) {
        is DeviceRepository.RouterConnectionStatus.Connected -> Color.Green to "Routeur: ${status.routerType.name}"
        is DeviceRepository.RouterConnectionStatus.Connecting -> Color.Orange to "Connexion..."
        is DeviceRepository.RouterConnectionStatus.Error -> Color.Red to "Erreur: ${status.message}"
        DeviceRepository.RouterConnectionStatus.Disconnected -> Color.Gray to "Déconnecté"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(8.dp)
                .background(if (useLocalFallback) Color.Blue else color)
                .clip(androidx.compose.ui.graphics.CircleShape)
        )
        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(4.dp))
        Text(text, fontSize = 12.sp, color = Theme.colorScheme.onSurface)
        if (useLocalFallback) {
            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(4.dp))
            Text("(VPN Local)", fontSize = 10.sp, color = Color.Blue)
        }
    }
}

@Composable
fun StatsRow(stats: MainViewModel.DeviceStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatCard("Total", stats.total.toString(), Theme.colorScheme.primary)
        StatCard("En ligne", stats.online.toString(), Color.Green)
        StatCard("Bloqués", stats.blocked.toString(), Color.Red)
        StatCard("Enfants", stats.children.toString(), Color.Magenta)
        StatCard("Invités", stats.guests.toString(), Color.Blue)
    }
}

@Composable
fun StatCard(label: String, value: String, color: Color) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.18f)
            .height(80.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 10.sp, color = color.copy(alpha = 0.8f))
        }
    }
}

@Composable
fun DeviceListScreen(viewModel: MainViewModel) {
    val devices = viewModel.filteredDevices.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val showProfilePicker by viewModel.showProfilePicker.collectAsStateWithLifecycle()
    val profiles = viewModel.profiles.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        androidx.compose.material3.TextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Rechercher appareil...") },
            leadingIcon = { Icon(painterResource(id = R.drawable.ic_search), contentDescription = "Search") },
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.TextFieldDefaults.textFieldColors(
                containerColor = Theme.colorScheme.surfaceContainerHighest
            )
        )

        // Device list
        if (devices.value.isEmpty()) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(id = R.drawable.ic_wifi_off), contentDescription = "", tint = Theme.colorScheme.onSurface.copy(alpha = 0.3f))
                    Text("Aucun appareil trouvé", color = Theme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices.value) { device ->
                    DeviceCard(
                        device = device,
                        onClick = { viewModel.selectDevice(device) },
                        onBlockToggle = { viewModel.toggleBlock(device) },
                        onProfileClick = { viewModel.showProfilePicker(device) }
                    )
                }
            }
        }
    }

    // Profile picker bottom sheet
    if (showProfilePicker) {
        ProfilePickerBottomSheet(
            device = selectedDevice!!,
            profiles = profiles.value,
            onSelect = { profileId ->
                viewModel.setProfile(selectedDevice!!, profileId)
                viewModel.showProfilePicker = false
            },
            onDismiss = { viewModel.showProfilePicker = false }
        )
    }
}

@Composable
fun DeviceCard(
    device: Device,
    onClick: () -> Unit,
    onBlockToggle: () -> Unit,
    onProfileClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (device.isBlocked) Theme.colorScheme.errorContainer else Theme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Device icon
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(48.dp)
                    .background(
                        color = if (device.isBlocked) Theme.colorScheme.error else device.profileColor,
                        shape = androidx.compose.ui.graphics.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(id = deviceTypeIcon(device.deviceType)),
                    contentDescription = device.deviceType.name,
                    tint = Theme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(12.dp))

            // Device info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(device.displayName, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    if (device.isBlocked) {
                        androidx.compose.material3.Badge(
                            badgeContent = { Text("BLOQUÉ", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = androidx.compose.material3.BadgeDefaults.badgeColors(
                                containerColor = Theme.colorScheme.error
                            )
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(device.macAddress, fontSize = 12.sp, color = Theme.colorScheme.onSurface.copy(alpha = 0.6f))
                    if (device.vendor != null) {
                        Text(" · ${device.vendor}", fontSize = 12.sp, color = Theme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    if (device.ipAddress != null) {
                        Text(" · ${device.ipAddress}", fontSize = 12.sp, color = Theme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
                if (device.profileId != null) {
                    Text("Profil: ${device.currentProfile}", fontSize = 11.sp, color = device.profileColor)
                }
            }

            // Block toggle
            androidx.compose.material3.Switch(
                checked = device.isBlocked,
                onCheckedChange = { onBlockToggle() },
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = Theme.colorScheme.error,
                    checkedTrackColor = Theme.colorScheme.errorContainer
                )
            )
        }
    }
}

@Composable
fun ProfilePickerBottomSheet(
    device: Device,
    profiles: List<DeviceProfile>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.ModalBottomSheet(
        sheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden),
        onDismissRequest = onDismiss,
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text("Choisir le profil pour ${device.displayName}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))
                profiles.forEach { profile ->
                    ProfileOptionCard(
                        profile = profile,
                        isSelected = device.profileId == profile.id,
                        onClick = { onSelect(profile.id) }
                    )
                }
            }
        }
    )
}

@Composable
fun ProfileOptionCard(profile: DeviceProfile, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) profile.colorInt.color.copy(alpha = 0.1f) else Theme.colorScheme.surfaceContainerLow,
            contentColor = if (isSelected) profile.colorInt.color else Theme.colorScheme.onSurface
        ),
        border = if (isSelected) androidx.compose.ui.graphics.Outline.Border(2.dp, profile.colorInt.color) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(40.dp)
                    .background(profile.colorInt.color.copy(alpha = 0.2f), androidx.compose.ui.graphics.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(id = profileIcon(profile.id)), contentDescription = "", tint = profile.colorInt.color, modifier = Modifier.size(20.dp))
            }
            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(12.dp))
            Column {
                Text(profile.name, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Text(profile.description, fontSize = 12.sp, color = Theme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            if (isSelected) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                Icon(painterResource(id = R.drawable.ic_check), contentDescription = "", tint = profile.colorInt.color)
            }
        }
    }
}

@Composable
fun RouterSetupScreen(
    onSave: (RouterConfig) -> Unit,
    onCancel: () -> Unit,
    existingConfig: RouterConfig?
) {
    var routerType by remember { mutableStateOf(existingConfig?.type ?: RouterType.OPENWRT) }
    var baseUrl by remember { mutableStateOf(existingConfig?.baseUrl ?: "http://192.168.1.1") }
    var username by remember { mutableStateOf(existingConfig?.username ?: "root") }
    var password by remember { mutableStateOf(existingConfig?.password ?: "") }
    var apiKey by remember { mutableStateOf(existingConfig?.apiKey ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Configuration du Routeur", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        // Router type dropdown
        androidx.compose.material3.ExposedDropdownMenuBox(
            modifier = Modifier.fillMaxWidth()
        ) {
            androidx.compose.material3.TextField(
                value = routerType.name,
                onValueChange = { routerType = RouterType.valueOf(it) },
                label = { Text("Type de routeur") },
                readOnly = true,
                trailingIcon = { Icon(painterResource(id = R.drawable.ic_arrow_drop_down), contentDescription = "") },
                modifier = Modifier.fillMaxWidth()
            )
            androidx.compose.material3.DropdownMenu(
                expanded = routerType.name.isNotBlank(),
                onDismissRequest = {}
            ) {
                RouterType.values().forEach { type ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(type.name) },
                        onClick = { routerType = type }
                    )
                }
            }
        }

        androidx.compose.material3.TextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("URL du routeur (ex: http://192.168.1.1)") },
            modifier = Modifier.fillMaxWidth()
        )

        androidx.compose.material3.TextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Nom d'utilisateur") },
            modifier = Modifier.fillMaxWidth()
        )

        androidx.compose.material3.TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Mot de passe") },
            visualTransformation = androidx.compose.material3.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        if (routerType == RouterType.GENERIC_HTTP) {
            androidx.compose.material3.TextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Clé API (optionnel)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            androidx.compose.material3.TextButton(onClick = onCancel) { Text("Annuler") }
            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(8.dp))
            androidx.compose.material3.FilledButton(
                onClick = {
                    onSave(RouterConfig(
                        id = "main",
                        name = "Routeur Principal",
                        type = routerType,
                        baseUrl = baseUrl,
                        username = username,
                        password = password,
                        apiKey = if (apiKey.isBlank()) null else apiKey
                    ))
                }
            ) { Text("Connecter") }
        }
    }
}

// Helper functions
fun tabIcon(index: Int) = when (index) {
    0 -> R.drawable.ic_devices
    1 -> R.drawable.ic_wifi
    2 -> R.drawable.ic_block
    3 -> R.drawable.ic_child
    4 -> R.drawable.ic_guest
    else -> R.drawable.ic_devices
}

fun deviceTypeIcon(type: com.wifikidscontrol.model.DeviceType) = when (type) {
    com.wifikidscontrol.model.DeviceType.PHONE -> R.drawable.ic_phone
    com.wifikidscontrol.model.DeviceType.TABLET -> R.drawable.ic_tablet
    com.wifikidscontrol.model.DeviceType.LAPTOP -> R.drawable.ic_laptop
    com.wifikidscontrol.model.DeviceType.DESKTOP -> R.drawable.ic_desktop
    com.wifikidscontrol.model.DeviceType.TV -> R.drawable.ic_tv
    com.wifikidscontrol.model.DeviceType.CONSOLE -> R.drawable.ic_console
    com.wifikidscontrol.model.DeviceType.IOT -> R.drawable.ic_iot
    com.wifikidscontrol.model.DeviceType.ROUTER -> R.drawable.ic_router
    else -> R.drawable.ic_device_unknown
}

fun profileIcon(id: String) = when (id) {
    "child" -> R.drawable.ic_child
    "guest" -> R.drawable.ic_guest
    "adult" -> R.drawable.ic_adult
    else -> R.drawable.ic_profile
}

val com.wifikidscontrol.model.Device.profileColor: androidx.compose.ui.graphics.Color
    get() = when (profileId) {
        "child" -> Color(0xFFE91E63)
        "guest" -> Color(0xFF2196F3)
        "adult" -> Color(0xFF4CAF50)
        else -> Theme.colorScheme.primary
    }

val com.wifikidscontrol.model.DeviceProfile.colorInt: androidx.compose.ui.graphics.Color
    get() = Color(color)