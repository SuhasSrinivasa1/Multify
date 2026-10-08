# Building v4.4.0

This source package intentionally excludes private signing material. The user has uninstalled the previous app, so a fresh install does not need the old certificate.

## Local build

Requirements: Android SDK 35, JDK 17, Gradle 8.9+ (or a compatible wrapper).

- Unit tests: `gradle :app:testDebugUnitTest`
- Fresh-install APK: `gradle :app:assembleDebug`

The debug build deliberately keeps the production application ID (`com.multify.traderpro.vivoy73final`) so the APK can be installed as the main app after the old version was uninstalled.

For future in-place upgrades, keep using the same signing certificate. A CI-generated debug certificate may change between environments, so do not assume it is suitable for permanent upgrade continuity.
