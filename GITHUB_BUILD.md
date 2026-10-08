# GitHub build

The repository build workflow reconstructs this source tree and runs:

1. JDK 17 setup
2. Android SDK 35 / build-tools 35.0.0 setup
3. Gradle 8.9 setup
4. `gradle :app:testDebugUnitTest`
5. `gradle :app:assembleDebug`
6. upload the resulting fresh-install APK as a GitHub Actions artifact

The debug build keeps application ID `com.multify.traderpro.vivoy73final` because this release is intended for a fresh install after the old app was uninstalled.
