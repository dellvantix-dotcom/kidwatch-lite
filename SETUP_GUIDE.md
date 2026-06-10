# KidWatch Lite — Setup Guide

**By Dellvantix** | Kotlin + Firebase + Web Dashboard

---

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- Firebase account at console.firebase.google.com
- A web hosting account (Hostinger) for the parent dashboard

---

## Step 1 — Firebase Project Setup

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Click **Add project** → name it `KidWatch`
3. Enable **Google Analytics** (optional)

### Enable Authentication
- Go to **Authentication → Sign-in method**
- Enable **Email/Password**

### Enable Firestore
- Go to **Firestore Database → Create database**
- Start in **production mode**
- Choose region: `asia-south1` (Mumbai, closest to India)
- After creation, go to **Rules** tab and paste contents of `firebase/firestore.rules`

### Enable Storage
- Go to **Storage → Get started**
- Choose the same region
- After creation, go to **Rules** tab and paste contents of `firebase/storage.rules`

### Get your config
- Go to **Project Settings (gear icon) → Your apps**
- Click **Add app → Android**
  - Package name: `com.dellvantix.kidwatch`
  - Download the `google-services.json` file
  - Place it in `app/` folder of the Android project
- Click **Add app → Web** (for dashboard)
  - Copy the `firebaseConfig` object
  - Paste it into `dashboard/dashboard.js` (replace `YOUR_API_KEY` etc.)

---

## Step 2 — Build the Android APK

```bash
# In Android Studio terminal
./gradlew assembleRelease
```

Or in Android Studio:
- **Build → Generate Signed Bundle/APK → APK**
- Create a keystore (keep it safe!)
- Build `release` variant

APK output: `app/build/outputs/apk/release/app-release.apk`

### Enable Anonymous Auth for Android app
- Firebase Console → Authentication → Sign-in method → **Anonymous** → Enable
- This allows the child device to write to Firestore without a parent login

---

## Step 3 — Install on Child's Device

1. Transfer `app-release.apk` to the child's device
2. Enable **Install from Unknown Sources** in device Settings if needed
3. Install the APK
4. Open KidWatch — the Consent screen appears
5. Read consent with the child (required — this is a disclosed monitoring app)
6. Grant all permissions when prompted
7. Enable Device Administrator when prompted
8. Set a 4–6 digit **parent PIN** (only you know this)
9. Tap **Start Monitoring**
10. Note the **Device ID** shown on screen (e.g., `A3F8B2C1`)

---

## Step 4 — Deploy Parent Dashboard

Upload the `dashboard/` folder contents to Hostinger:
- `index.html`
- `style.css`
- `dashboard.js`

Or deploy to Firebase Hosting:
```bash
npm install -g firebase-tools
firebase login
firebase init hosting
firebase deploy
```

---

## Step 5 — Link Device in Dashboard

1. Open the parent dashboard in a browser
2. Sign in with your parent email/password (create account on first use)
3. Enter the **Device ID** from the child's device in the sidebar
4. Click **Link** — data starts streaming in real time

---

## Features Summary

| Feature | How it works |
|---------|-------------|
| Screenshots | MediaProjection API — requires user grant once per session |
| SMS Monitoring | READ_SMS + RECEIVE_SMS permissions |
| Call Logs | READ_CALL_LOG permission + phone state receiver |
| Uninstall Protection | Device Administrator API |
| Auto-start | BOOT_COMPLETED broadcast receiver |
| Background sync | START_STICKY foreground service + WorkManager |

---

## Screenshot Interval Configuration

Default: 60 seconds. To change:
- In `PrefsRepository.kt`, modify `KEY_SCREENSHOT_INTERVAL` default value
- Or add a settings screen that saves to DataStore

---

## Privacy & Legal Notes (India)

- This app must only be installed on a **minor's device** with **full parental consent**
- The app displays a **visible notification** that monitoring is active (required by Android)
- Data is stored in Firebase under **your Firebase account** — Dellvantix has no access
- Comply with **India's IT Act 2000** and **PDPB** (Personal Data Protection Bill)
- Consider adding a **Terms of Service** specific to your deployment

---

## Troubleshooting

**SMS not syncing?**
- Ensure `READ_SMS` and `RECEIVE_SMS` permissions are granted
- On some OEM ROMs (Xiaomi, Samsung), disable battery optimization for KidWatch

**Screenshots not uploading?**
- MediaProjection requires a foreground activity to request permission — the child must tap "Allow" once per app restart
- Check Firebase Storage rules are deployed

**App stops after reboot?**
- Ensure `RECEIVE_BOOT_COMPLETED` permission is granted
- On MIUI/ColorOS: add KidWatch to autostart whitelist in device settings

**Service killed by battery optimization?**
- Request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission
- Guide parent to whitelist KidWatch in battery settings

---

## File Structure

```
kidwatch/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── kotlin/com/dellvantix/kidwatch/
│           ├── KidWatchApp.kt
│           ├── di/AppModule.kt
│           ├── model/Models.kt
│           ├── receiver/Receivers.kt
│           ├── receiver/SmsReceiver.kt
│           ├── receiver/CallReceiver.kt
│           ├── repository/FirebaseRepository.kt
│           ├── repository/PrefsRepository.kt
│           ├── service/MonitorService.kt
│           ├── service/ScreenCaptureManager.kt
│           ├── ui/MainActivity.kt
│           ├── ui/MainViewModel.kt
│           └── ui/theme/Theme.kt
├── dashboard/
│   ├── index.html
│   ├── style.css
│   └── dashboard.js
├── firebase/
│   ├── firestore.rules
│   └── storage.rules
└── build.gradle
```
