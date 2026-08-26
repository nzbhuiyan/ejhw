# EJHWO WebView Android App

Firebase Hosting URL: https://ejhwo-1523f.web.app

## Safe Area / bottom gap fix
- `WindowCompat.setDecorFitsSystemWindows(window, true)`
- Root layout `android:fitsSystemWindows="true"`
- No edge-to-edge / no extra bottom inset
→ Web bottom nav sits flush (no empty gap under nav)

## Build APK (Android Studio)

1. Install Android Studio: https://developer.android.com/studio
2. File → Open → select this `EJHWO-WebView` folder
3. Wait for Gradle sync
4. Build → Build Bundle(s) / APK(s) → Build APK(s)
5. APK path: `app/build/outputs/apk/debug/app-debug.apk`
6. Phone-এ copy করে install করুন (Unknown sources allow করুন)

## Change URL
Edit `app/src/main/res/values/strings.xml` → `app_url`

## Release (signed) APK
Build → Generate Signed Bundle / APK → APK → create keystore → release

## Build APK with GitHub (no PC Android Studio needed)

1. Create a new GitHub repository (public or private)
2. Upload this whole `EJHWO-WebView` folder to the repo
3. GitHub → **Actions** tab → **Build EJHWO APK** → **Run workflow**
4. When green/success → open the run → **Artifacts** → download **EJHWO-debug-apk**
5. Unzip → install `app-debug.apk` on phone

Or push to `main` branch — workflow runs automatically.
