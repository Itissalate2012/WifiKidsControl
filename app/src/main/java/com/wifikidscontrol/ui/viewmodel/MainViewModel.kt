package com.wifikidscontrol.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifikidscontrol.model.Device
import com.wifikidscontrol.model.DeviceProfile
import com.wifikidscontrol.model.NetworkEvent
import com.wifikidscontrol.network.RouterConfig
import com.wifikidscontrol.network.RouterType
import com.wifikidscontrol.repository.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: DeviceRepository
) : ViewModel() {

    // UI State
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab = _selectedTab.asStateFlow()

    private val _showRouterSetup = MutableStateFlow(false)
    val showRouterSetup = _showRouterSetup.asStateFlow()

    private val _routerConfig = MutableStateFlow<RouterConfig?>(null)
    val routerConfig = _routerConfig.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedDevice = MutableStateFlow<Device?>(null)
    val selectedDevice = _selectedDevice.asStateFlow()

    private val _showProfilePicker = MutableStateFlow(false)
    val showProfilePicker = _showProfilePicker.asStateFlow()

    private val _showScheduleEditor = MutableStateFlow(false)
    val showScheduleEditor = _showScheduleEditor.asStateFlow()

    private val _editingProfile = MutableStateFlow<DeviceProfile?>(null)
    val editingProfile = _editingProfile.asStateFlow()

    // Combined filtered devices
    val filteredDevices = combine(
        repository.allDevices,
        searchQuery,
        selectedTab
    ) { devices, query, tab ->
        devices.filter { device ->
            val matchesQuery = query.isBlank() ||
                device.name.contains(query, ignoreCase = true) ||
                device.macAddress.contains(query, ignoreCase = true) ||
                device.vendor?.contains(query, ignoreCase = true) == true

            val matchesTab = when (tab) {
                0 -> true // All
                1 -> device.isOnline // Online
                2 -> device.isBlocked // Blocked
                3 -> device.profileId == "child" // Children
                4 -> device.profileId == "guest" // Guests
                else -> true
            }
            matchesQuery && matchesTab
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    // Stats
    val stats = combine(
        repository.allDevices,
        repository.onlineDevices,
        repository.blockedDevices
    ) { all, online, blocked ->
        DeviceStats(
            total = all.size,
            online = online.size,
            blocked = blocked.size,
            children = all.count { it.profileId == "child" },
            guests = all.count { it.profileId == "guest" }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), DeviceStats(0,0,0,0,0))

    // Actions
    fun setTab(index: Int) { _selectedTab.value = index }

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun selectDevice(device: Device?) { _selectedDevice.value = device }

    fun toggleRouterSetup(show: Boolean) { _showRouterSetup.value = show }

    fun setRouterConfig(config: RouterConfig) {
        _routerConfig.value = config
        viewModelScope.launch { repository.configureRouter(config) }
    }

    fun disconnectRouter() {
        viewModelScope.launch { repository.disconnectRouter() }
        _routerConfig.value = null
    }

    fun toggleBlock(device: Device) {
        viewModelScope.launch {
            if (device.isBlocked) repository.unblockDevice(device)
            else repository.blockDevice(device)
        }
    }

    fun setProfile(device: Device, profileId: String) {
        viewModelScope.launch { repository.setDeviceProfile(device, profileId) }
        _selectedDevice.value = null
    }

    fun showProfilePicker(device: Device) {
        _selectedDevice.value = device
        _showProfilePicker.value = true
    }

    fun editProfile(profile: DeviceProfile) {
        _editingProfile.value = profile
        _showScheduleEditor.value = true
    }

    fun addManualDevice(mac: String, name: String, profileId: String?) {
        viewModelScope.launch { repository.addManualDevice(mac, name, profileId) }
    }

    fun updateDeviceName(mac: String, name: String) {
        viewModelScope.launch { repository.updateDeviceName(mac, name) }
    }

    data class DeviceStats(
        val total: Int,
        val online: Int,
        val blocked: Int,
        val children: Int,
        val guests: Int
    )
}