# Kursach Mobile (Android)

Barebones Android mobile client scaffolded as a separate repository.

## Why this fits your request

- Separate folder/repo: `C:\Users\1\Desktop\kursach-mobile`
- Not a subfolder of your current `kursach` project
- APK export path included (local script + GitHub Actions build)
- No Node.js dependency (Android Gradle/JDK only)

## Stack

- Kotlin
- Android SDK (minSdk 24, targetSdk 34)
- Gradle build system

## Connect to your existing backend

Your backend remains in the current repo. This app should call it by HTTP API.

1. Copy `local.properties.example` to `local.properties`
2. Set your Android SDK path in `local.properties`
3. Optionally add API URL in `local.properties`:

```properties
API_BASE_URL=http://10.0.2.2:3000
```

`10.0.2.2` maps Android emulator to host machine localhost.

## Build APK locally

From PowerShell in this repo:

```powershell
./scripts/export-apk.ps1 -BuildType debug
```

Debug APK output:

- `app/build/outputs/apk/debug/app-debug.apk`

For release (unsigned unless signing is configured):

```powershell
./scripts/export-apk.ps1 -BuildType release
```

## CI APK build

GitHub Actions workflow is included:

- `.github/workflows/build-apk.yml`

It builds and uploads `app-debug.apk` as an artifact.

## Initialize remote repository (optional)

```powershell
git add .
git commit -m "Initial barebones Android mobile app"
# then add your remote
# git remote add origin <your-repo-url>
# git push -u origin main
```
