# EditPro

Kotlin + Jetpack Compose Android application. Application ID: `com.vextech.editpro`.

## Build

Run commands from the workspace root:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
python tools\static_checks.py
```

The project requires JDK 25 (configured in `gradle/gradle-daemon-jvm.properties`) and Android SDK platform 37.1. Feature notes are in `docs/`; production-policy verifiers are in `tools/`.
