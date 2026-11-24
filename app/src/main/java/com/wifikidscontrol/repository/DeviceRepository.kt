package com.wifikidscontrol.repository

import com.wifikidscontrol.db.AppDatabase
import com.wifikidscontrol.db.DeviceDao
import com.wifikidscontrol.db.EventDao
import com.wifikidscontrol.db.ProfileDao
import com.wifikidscontrol.model.Device
import com.wifikidscontrol.model.DeviceProfile
import com.wifikidscontrol.model.NetworkEvent
import com.wifikidscontrol.model.ScheduleRule
import com.wifikidscontrol.network.ConnectedClient
import com.wifikidscontrol.network.LocalVpnService
import com.wifikidscontrol.network.RouterApi
import com.wifikidscontrol.network.RouterApiFactory
import com.wifikidscontrol.network.RouterConfig
import com.wifikidscontrol.network.RouterType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

@Singleton
class DeviceRepository @Inject constructor(
    private val db: AppDatabase,
    private val deviceDao: DeviceDao,
    private val profileDao: ProfileDao,
    private val eventDao: EventDao,
    @ApplicationContext private val context: Context
) {
    private val deviceUpdateChannel = Channel<Device>(100)
    private var routerApi: RouterApi? = null
    private var currentRouterConfig: RouterConfig? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // Reactive state
    val allDevices = deviceDao.getAllDevices().stateIn(
        scope, SharingStarted.WhileSubscribed(), emptyList()
    )
    val onlineDevices = deviceDao.getOnlineDevices().stateIn(
        scope, SharingStarted.WhileSubscribed(), emptyList()
    )
    val blockedDevices = deviceDao.getBlockedDevices().stateIn(
        scope, SharingStarted.WhileSubscribed(), emptyList()
    )
    val profiles = profileDao.getAllProfiles().stateIn(
        scope, SharingStarted.WhileSubscribed(), emptyList()
    )
    val recentEvents = eventDao.getRecentEvents(100).stateIn(
        scope, SharingStarted.WhileSubscribed(), emptyList()
    )

    // Current router connection status
    private val _routerStatus = MutableStateFlow<RouterConnectionStatus>(RouterConnectionStatus.Disconnected)
    val routerStatus = _routerStatus.asStateFlow()

    private val _useLocalFallback = MutableStateFlow(false)
    val useLocalFallback = _useLocalFallback.asStateFlow()

    sealed interface RouterConnectionStatus {
        data class Connected(val routerType: RouterType) : RouterConnectionStatus
        object Disconnected : RouterConnectionStatus
        data class Error(val message: String) : RouterConnectionStatus
        object Connecting : RouterConnectionStatus
    }

    init {
        observeRouterClients()
        observeSchedules()
    }

    // Router Configuration
    suspend fun configureRouter(config: RouterConfig): Boolean {
        _routerStatus.value = RouterConnectionStatus.Connecting
        currentRouterConfig = config
        routerApi = RouterApiFactory.create(config, createHttpClient())

        val result = routerApi!!.connect(config)
        return when (result) {
            is RouterApi.Result.Success -> {
                _routerStatus.value = RouterConnectionStatus.Connected(config.type)
                syncDevicesFromRouter()
                true
            }
            is RouterApi.Result.Error -> {
                _routerStatus.value = RouterConnectionStatus.Error(result.message)
                enableLocalFallback()
                false
            }
            is RouterApi.Result.Unauthorized -> {
                _routerStatus.value = RouterConnectionStatus.Error("Authentification échouée")
                enableLocalFallback()
                false
            }
            is RouterApi.Result.NotConnected -> {
                _routerStatus.value = RouterConnectionStatus.Error("Routeur inaccessible")
                enableLocalFallback()
                false
            }
        }
    }

    private fun createHttpClient() = okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private fun enableLocalFallback() {
        _useLocalFallback.value = true
        // Request VPN permission if not granted
        if (!LocalVpnService.VpnController.isVpnPrepared(context)) {
            // App will need to request permission via activity
        } else {
            LocalVpnService.VpnController.startVpn(context)
        }
    }

    suspend fun disconnectRouter() {
        routerApi?.disconnect()
        routerApi = null
        currentRouterConfig = null
        _routerStatus.value = RouterConnectionStatus.Disconnected
        _useLocalFallback.value = false
        LocalVpnService.VpnController.stopVpn(context)
    }

    // Device Operations
    suspend fun blockDevice(device: Device): Boolean {
        val newDevice = device.copy(isBlocked = true)
        deviceDao.updateDevice(newDevice)
        logEvent(NetworkEvent(device.macAddress, NetworkEvent.EventType.BLOCKED, details = "Bloqué manuellement"))

        return if (_useLocalFallback.value) {
            applyLocalBlock(device.macAddress, true)
        } else {
            routerApi?.blockDevice(device.macAddress)?.isSuccess() == true
        }
    }

    suspend fun unblockDevice(device: Device): Boolean {
        val newDevice = device.copy(isBlocked = false)
        deviceDao.updateDevice(newDevice)
        logEvent(NetworkEvent(device.macAddress, NetworkEvent.EventType.UNBLOCKED, details = "Débloqué manuellement"))

        return if (_useLocalFallback.value) {
            applyLocalBlock(device.macAddress, false)
        } else {
            routerApi?.unblockDevice(device.macAddress)?.isSuccess() == true
        }
    }

    suspend fun setDeviceProfile(device: Device, profileId: String): Boolean {
        val profile = profileDao.getProfile(profileId).await() ?: return false
        val newDevice = device.copy(profileId = profileId, currentProfile = profile.name)
        deviceDao.updateDevice(newDevice)
        logEvent(NetworkEvent(device.macAddress, NetworkEvent.EventType.PROFILE_CHANGED, details = "Profil: ${profile.name}"))

        // Apply schedule rules from profile
        val rules = profileDao.getRulesForProfile(profileId).await()
        if (rules.isNotEmpty() && !_useLocalFallback.value) {
            routerApi?.setDeviceSchedule(device.macAddress, rules.first().id)
        }
        return true
    }

    // Local VPN fallback blocking
    private suspend fun applyLocalBlock(mac: String, block: Boolean) {
        val device = deviceDao.getDevice(mac).await()
        val ip = device?.ipAddress
        val macs = if (block) setOf(mac) else emptySet<String>()
        val ips = if (block && ip != null) setOf(ip) else emptySet<String>()
        LocalVpnService.VpnController.updateBlocklist(context, macs, ips)
    }

    // Sync from router
    private fun observeRouterClients() {
        scope.launch {
            routerApi?.observeClients()?.collect { result ->
                if (result.isSuccess()) {
                    result.getOrNull()?.let { clients ->
                        scope.launch { syncDevicesFromRouterClients(clients) }
                    }
                }
            }
        }
    }

    private suspend fun syncDevicesFromRouter() {
        routerApi?.getConnectedClients()?.getOrNull()?.let { syncDevicesFromRouterClients(it) }
    }

    private suspend fun syncDevicesFromRouterClients(clients: List<ConnectedClient>) {
        val now = LocalDateTime.now().toString()
        val existingMacs = mutableSetOf<String>()

        for (client in clients) {
            val mac = client.mac.uppercase()
            existingMacs.add(mac)

            val existing = deviceDao.getDevice(mac).await()
            val device = existing?.copy(
                ipAddress = client.ip,
                vendor = client.vendor,
                lastSeen = LocalDateTime.now(),
                isOnline = true
            ) ?: Device.unknown(mac, client.ip).copy(
                vendor = client.vendor,
                name = client.name ?: Device.unknown(mac).name,
                isOnline = true
            )
            deviceDao.insertDevice(device)
        }

        // Mark devices not seen as offline
        val allDevices = deviceDao.getAllDevices().await()
        for (device in allDevices) {
            if (device.macAddress !in existingMacs && device.isOnline) {
                deviceDao.setOnline(device.macAddress, false, now)
            }
        }
    }

    // Schedule observation
    private fun observeSchedules() {
        scope.launch {
            profileDao.getAllProfiles().collect { profileList ->
                for (profile in profileList) {
                    profileDao.getRulesForProfile(profile.id).collect { rules ->
                        checkScheduleRules(profile.id, rules)
                    }
                }
            }
        }
    }

    private fun checkScheduleRules(profileId: String, rules: List<ScheduleRule>) {
        val now = java.time.LocalTime.now()
        val today = java.time.LocalDate.now().dayOfWeek.value

        for (rule in rules) {
            if (today in rule.days && rule.isActiveNow()) {
                // Apply rule action
                val devices = deviceDao.getDevicesByProfile(profileId).await()
                for (device in devices) {
                    when (rule.action) {
                        com.wifikidscontrol.model.ScheduleAction.BLOCK -> {
                            if (!device.isBlocked) {
                                scope.launch { blockDevice(device) }
                                logEvent(NetworkEvent(device.macAddress, NetworkEvent.EventType.SCHEDULE_TRIGGERED, details = "Bloqué par planning: ${rule.name}"))
                            }
                        }
                        com.wifikidscontrol.model.ScheduleAction.ALLOW -> {
                            if (device.isBlocked) {
                                scope.launch { unblockDevice(device) }
                                logEvent(NetworkEvent(device.macAddress, NetworkEvent.EventType.SCHEDULE_TRIGGERED, details = "Débloqué par planning: ${rule.name}"))
                            }
                        }
                    }
                }
            }
        }
    }

    // Event logging
    private suspend fun logEvent(event: NetworkEvent) {
        eventDao.insertEvent(event)
    }

    // Manual device add/edit
    suspend fun addManualDevice(mac: String, name: String, profileId: String? = null): Device {
        val device = Device(
            macAddress = mac.uppercase(),
            name = name,
            profileId = profileId,
            deviceType = com.wifikidscontrol.model.DeviceType.UNKNOWN
        )
        deviceDao.insertDevice(device)
        return device
    }

    suspend fun updateDeviceName(mac: String, name: String) {
        deviceDao.getDevice(mac).await()?.let { device ->
            deviceDao.updateDevice(device.copy(name = name))
        }
    }

    // Cleanup
    suspend fun cleanupOldData(days: Int = 30) {
        val cutoff = LocalDateTime.now().minusDays(days).toString()
        deviceDao.cleanupOldDevices(cutoff)
        eventDao.cleanupOldEvents(cutoff)
    }
}