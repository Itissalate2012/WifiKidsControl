# GitHub Actions Setup Guide

This guide explains how to configure GitHub Actions to automatically build **Android APK** and **iOS IPA** for WifiKidsControl.

## 🚀 Quick Start

### 1. Push to GitHub
```bash
cd E:\mon projet\WifiKidsControl
git init
git add .
git commit -m "Initial commit: WifiKidsControl"
git branch -M main
git remote add origin https://github.com/VOTRE_USER/WifiKidsControl.git
git push -u origin main
```

### 2. Enable Actions
- Go to your repo on GitHub
- Click **Actions** tab
- Click **"I understand my workflows, go ahead and enable them"**

### 3. First Build
- Push any commit or go to Actions → **Build & Release** → **Run workflow**
- Select `debug` or `release`
- Wait ~10-15 min for Android, ~20-30 min for iOS

---

## 🔐 Secrets Required

Go to **Settings → Secrets and variables → Actions → New repository secret**

### Android (Release only)
| Secret | Description | Required |
|--------|-------------|----------|
| `KEYSTORE_BASE64` | Base64 encoded keystore.jks | Release builds |
| `KEYSTORE_PASSWORD` | Keystore password | Release builds |
| `KEY_ALIAS` | Key alias name | Release builds |
| `KEY_PASSWORD` | Key password | Release builds |

**Generate keystore & base64:**
```bash
# Generate keystore
keytool -genkeypair -v -keystore keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias wifikidskey

# Convert to base64 (Linux/Mac/Git Bash)
base64 -w 0 keystore.jks  # Copy output to KEYSTORE_BASE64 secret
```

### iOS (Release only)
| Secret | Description | Required |
|--------|-------------|----------|
| `APPLE_TEAM_ID` | Your Apple Developer Team ID (10 chars) | Yes |
| `CERTIFICATE_BASE64` | Base64 encoded .p12 certificate | Release builds |
| `CERTIFICATE_PASSWORD` | .p12 password | Release builds |
| `PROVISIONING_PROFILE_BASE64` | Base64 encoded .mobileprovision | Release builds |
| `PROVISIONING_PROFILE_NAME` | Profile name (exact) | Release builds |

**Generate iOS certificates:**
1. Xcode → Preferences → Accounts → Manage Certificates
2. Create **iOS Distribution** certificate
3. Export as `.p12` (right-click → Export)
4. Create **Ad Hoc** provisioning profile for `com.wifikidscontrol`
5. Convert to base64:
```bash
base64 -w 0 Certificates.p12 > cert.base64
base64 -w 0 Profile.mobileprovision > profile.base64
```

---

## 🏷️ Creating Releases

### Automatic (on tag push)
```bash
# Create and push tag
git tag v1.0.0
git push origin v1.0.0
```
This triggers:
1. Android debug + release builds
2. iOS debug + release builds  
3. GitHub Release with APK + IPA attached

### Manual (workflow dispatch)
Go to **Actions → Build & Release → Run workflow** → choose `debug` or `release`

---

## 📱 Artifacts

After each build, download from:
- **Actions tab** → click run → **Artifacts** section
- Or **Releases page** (for tagged releases)

### Artifact naming
- `android-app-debug-<sha>.apk` - Debug APK (installable directly)
- `android-app-release-<sha>.apk` - Release APK (signed)
- `ios-Release-<sha>.ipa` - Release IPA (Ad Hoc)

---

## 🐛 Troubleshooting

### Android build fails
- **SDK license not accepted**: Add `licenses` folder to repo or accept in workflow
- **Gradle OOM**: Increase `GRADLE_OPTS` memory
- **Keystore error**: Verify base64 encoding (no newlines)

### iOS build fails
- **No Xcode project**: Create project first (see iOS Setup below)
- **Signing error**: Check Team ID, cert, profile match
- **Provisioning profile**: Must be Ad Hoc for IPA export
- **Network Extension**: Need separate target + entitlements

---

## 📋 iOS Project Setup (Required before first build)

Since the repo contains Swift source but no `.xcodeproj`, you must create it once:

### Option A: Xcode (on Mac)
```bash
# 1. Open WifiKidsControl-iOS folder in Xcode
# 2. File → New → Project → iOS → App
#    - Name: WifiKidsControl
#    - Bundle ID: com.wifikidscontrol
#    - Interface: SwiftUI
#    - Language: Swift
#    - Use Core Data: ✓
#    - Save in WifiKidsControl-iOS folder
# 3. Add Network Extension target:
#    File → New → Target → Network Extension → Packet Tunnel
#    - Name: VPNExtension
#    - Bundle ID: com.wifikidscontrol.VPNExtension
# 4. Configure Signing & Capabilities for BOTH targets:
#    - Team: Your Apple Developer Team
#    - App Groups: group.com.wifikidscontrol
#    - Network Extensions: Packet Tunnel (VPNExtension only)
#    - Keychain Sharing: com.wifikidscontrol
# 5. Copy PacketTunnelProvider.swift to VPNExtension target
# 6. Commit and push .xcodeproj folder
```

### Option B: xcodegen (automated)
```yaml
# project.yml (create in WifiKidsControl-iOS/)
name: WifiKidsControl
options:
  bundleIdPrefix: com.wifikidscontrol
  deploymentTarget:
    iOS: "17.0"
  createIntermediateGroups: true
targets:
  WifiKidsControl:
    type: application
    platform: iOS
    sources:
      - path: WifiKidsControl
    settings:
      base:
        SWIFT_VERSION: "5.9"
        CODE_SIGN_STYLE: Automatic
        DEVELOPMENT_TEAM: "$(APPLE_TEAM_ID)"
    entitlements:
      com.apple.security.application-groups: ["group.com.wifikidscontrol"]
      com.apple.developer.networking.vpn.api: ["allow-vpn"]
  VPNExtension:
    type: app-extension
    platform: iOS
    sources:
      - path: VPNExtension
    settings:
      base:
        SWIFT_VERSION: "5.9"
        CODE_SIGN_STYLE: Automatic
        DEVELOPMENT_TEAM: "$(APPLE_TEAM_ID)"
        PRODUCT_BUNDLE_IDENTIFIER: com.wifikidscontrol.VPNExtension
    entitlements:
      com.apple.developer.networking.networkextension: ["packet-tunnel"]
      com.apple.security.application-groups: ["group.com.wifikidscontrol"]
```
Then run: `xcodegen generate` (requires xcodegen installed)

---

## 💰 Costs

| Platform | Free Tier | Notes |
|----------|-----------|-------|
| GitHub Actions (Linux) | 2000 min/mo | Public repos: unlimited |
| GitHub Actions (macOS) | 200 min/mo | Public repos: unlimited |
| **Total for this project** | **Free** | Public repo = unlimited macOS minutes |

---

## 🔄 Workflow Customization

Edit `.github/workflows/build.yml` to:
- Change Xcode version: `XCODE_VERSION`
- Add test steps: `./gradlew test` / `xcodebuild test`
- Add lint: `./gradlew lint` / `swiftlint`
- Change artifact retention: `retention-days`
- Add Slack/Discord notifications

---

## ✅ Checklist Before First Release

- [ ] Push code to GitHub
- [ ] Enable Actions
- [ ] Add Android keystore secrets (for release)
- [ ] Create Xcode project on Mac (for iOS)
- [ ] Add iOS certificates secrets (for release)
- [ ] Test debug build: Actions → Run workflow → debug
- [ ] Create tag: `git tag v1.0.0 && git push origin v1.0.0`
- [ ] Verify Release page has APK + IPA
- [ ] Test install on physical devices