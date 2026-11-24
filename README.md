# WifiKidsControl - Parental WiFi Control App

Cross-platform app (Android + iOS) to control children's and guests' WiFi access via router API or local VPN fallback.

## 🎯 Features

- **Device Discovery** - See all devices on your network with vendor detection
- **Profile System** - Child, Guest, Adult, Default profiles with custom rules
- **Time Schedules** - Block/allow by day/time (bedtime, homework, etc.)
- **One-Tap Block** - Instantly block/unblock any device
- **Hybrid Architecture** - Router API (preferred) + Local VPN fallback
- **Multi-Router Support** - OpenWrt, Asus Merlin, MikroTik, Generic HTTP/SSH
- **Event History** - Track connections, blocks, profile changes
- **Biometric Auth** - Secure app access with FaceID/TouchID/Fingerprint

## 🏗 Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   Android/iOS   │────▶│  DeviceRepository │────▶│   Router API    │
│     App (UI)    │     │  (State + Logic)  │     │  (OpenWrt/Asus/ │
└─────────────────┘     └──────────────────┘     │   MikroTik)     │
                           │                      └─────────────────┘
                           ▼
                    ┌──────────────────┐
                    │ Local VPN Fallback│
                    │ (VpnService /     │
                    │  NetworkExtension)│
                    └──────────────────┘
```

## 📁 Project Structure

### Android (Kotlin + Jetpack Compose)
```
WifiKidsControl/
├── app/
│   ├── src/main/
│   │   ├── java/com/wifikidscontrol/
│   │   │   ├── model/          # Device, Profile, Schedule, Event
│   │   │   ├── db/             # Room Database + DAOs
│   │   │   ├── network/        # RouterApi + LocalVpnService
│   │   │   ├── repository/     # DeviceRepository
│   │   │   ├── ui/
│   │   │   │   ├── screens/    # Compose Screens
│   │   │   │   ├── viewmodel/  # ViewModels
│   │   │   │   └── theme/      # Material 3 Theme
│   │   │   ├── di/             # Hilt Modules
│   │   │   └── WifiKidsApplication.kt
│   │   ├── AndroidManifest.xml
│   │   └── res/                # Resources
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
└── build_apk.sh
```

### iOS (Swift + SwiftUI + SwiftData)
```
WifiKidsControl-iOS/
├── WifiKidsControl/
│   ├── WifiKidsControlApp.swift
│   ├── Models/
│   │   ├── Device.swift
│   │   ├── DeviceProfile.swift
│   │   ├── NetworkEvent.swift
│   │   └── RouterConfig.swift
│   ├── Network/
│   │   ├── RouterApi.swift         # OpenWrt, Asus, MikroTik, Generic
│   │   └── LocalVpnManager.swift   # NetworkExtension wrapper
│   ├── Repository/
│   │   └── DeviceRepository.swift  # @Observable state management
│   ├── Views/
│   │   ├── ContentView.swift       # Main UI
│   │   └── RouterSetupView.swift   # Router configuration
│   ├── Extensions/
│   │   └── Color+Hex.swift
│   └── VPNExtension/
│       └── PacketTunnelProvider.swift  # Network Extension target
├── ExportOptions.plist
└── build_ipa.sh
```

## 🚀 Quick Start

### Android

```bash
# Prerequisites: Android SDK, JDK 17
export ANDROID_HOME=$HOME/Android/Sdk

# Build debug APK
cd WifiKidsControl
./gradlew assembleDebug

# Install on device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Or use build script
./build_apk.sh
```

### iOS

```bash
# Prerequisites: Xcode 15+, iOS 17+ device
cd WifiKidsControl-iOS

# 1. Open in Xcode and create project (see build script)
# 2. Add Network Extension target for VPN
# 3. Configure signing & capabilities:
#    - App Groups: group.com.wifikidscontrol
#    - Network Extensions: Packet Tunnel
#    - Keychain Sharing

# Build via script (after Xcode project setup)
./build_ipa.sh
```

## ⚙️ Router Configuration

### OpenWrt (Recommended)
1. Install `luci-rpc` and `luci-mod-rpc` packages
2. Enable RPC: `/etc/config/rpcd` → set `timeout=30`
3. In app: Type=OpenWrt, URL=`http://192.168.1.1`, user=`root`, pass=`your_password`

### Asus Merlin / Stock
1. Enable "Web Access from WAN" or use LAN IP
2. In app: Type=Asus, URL=`http://192.168.1.1`, user=`admin`, pass=`your_password`

### MikroTik RouterOS
1. Enable API: `/ip/service/set api disabled=no`
2. Create API user with `policy=local,reboot,read,write,policy,test,api`
3. In app: Type=MikroTik, URL=`http://192.168.1.1`, user=`apiuser`, pass=`password`

### Generic HTTP API
Implement these endpoints on your router:
- `GET /clients` → `[{mac, ip, name, vendor, signal, rx, tx}]`
- `POST /block` `{mac}` → `{success: true}`
- `POST /unblock` `{mac}` → `{success: true}`
- `GET /status` → `{connected: true, clients: N}`

## 🔐 Permissions Required

### Android
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.BIND_VPN_SERVICE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.USE_BIOMETRIC" />
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" />
```

### iOS (Entitlements)
- `com.apple.developer.networking.networkextension` (Packet Tunnel)
- `com.apple.developer.networking.vpn.api` (allow-vpn)
- App Groups: `group.com.wifikidscontrol`
- Keychain Sharing: `com.wifikidscontrol`

## 📱 Screenshots

*Coming soon - run the app to see the Material 3 / SwiftUI interface*

## 🛠 Tech Stack

| Layer | Android | iOS |
|-------|---------|-----|
| UI | Jetpack Compose (Material 3) | SwiftUI |
| State | ViewModel + StateFlow | @Observable + SwiftData |
| DB | Room | SwiftData |
| DI | Hilt | Manual (EnvironmentObject) |
| Network | Retrofit + OkHttp | URLSession + AsyncStream |
| VPN | VpnService (Kotlin) | NetworkExtension (Swift) |
| Arch | MVVM + Clean Architecture | MVVM + Swift Concurrency |

## 🔧 Development

### Adding New Router Support
1. Create new class implementing `RouterApi` (Android) / `RouterApi` protocol (iOS)
2. Add to `RouterApiFactory`
3. Add router type to `RouterType` enum
4. Test with target router

### Extending Profiles
```kotlin
// Android
DeviceProfile(
    id = "teen",
    name = "Adolescent",
    scheduleRules = listOf(...),
    maxDailyMinutes = 180,
    color = 0xFF9C27B0
)
```

```swift
// iOS
DeviceProfile(
    id: "teen",
    name: "Adolescent",
    description: "Accès modéré",
    maxDailyMinutes: 180,
    color: .purple
)
```

## 📋 Roadmap

- [ ] Push notifications for schedule events
- [ ] Usage statistics & charts
- [ ] Remote management via cloud sync
- [ ] DNS filtering (block categories: adult, gambling, social)
- [ ] Bandwidth throttling per device
- [ ] Multiple router support (mesh systems)
- [ ] Family sharing / multi-admin
- [ ] Export/import configuration
- [ ] Widget for quick block/unblock

## 🤝 Contributing

1. Fork the repo
2. Create feature branch
3. Add tests for new functionality
4. Submit PR with description

## 📄 License

MIT License - see LICENSE file

## ⚠️ Disclaimer

This app modifies network traffic. Use responsibly and ensure you have permission to manage the network. The VPN fallback requires explicit user consent on both platforms.

---

**Built with ❤️ for parental peace of mind**