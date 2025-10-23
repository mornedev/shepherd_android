# Force Android App Icon Update

The Android system caches app icons. To see the new icon:

## Method 1: Uninstall & Reinstall (Recommended)
1. Uninstall the app from your phone completely
2. Rebuild and reinstall the app
3. The new icon should appear

## Method 2: Clear Launcher Cache
1. Go to Settings > Apps
2. Find your Launcher app (e.g., "Launcher", "Home", "Pixel Launcher")
3. Tap "Storage"
4. Tap "Clear Cache" (NOT Clear Data)
5. Restart your phone
6. Reinstall the app

## Method 3: Gradle Clean Build
Run these commands to ensure a clean build:
```bash
cd android
./gradlew clean
./gradlew assembleDebug
```
Then uninstall the old app and install the new APK.

## Verify Icon Files
The icon files should be at:
- android/app/src/main/res/mipmap-*/ic_launcher.png
- android/app/src/main/res/mipmap-*/ic_launcher_round.png

All .webp files should be deleted (already done).
