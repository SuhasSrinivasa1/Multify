# Multify release signing

Multify uses one stable Android release signing identity for all future production APK updates.

- Keystore file expected by Gradle: `app/multify-v4-stable.jks`
- Key alias: `multifyv4`
- Certificate SHA-256: `3F:72:D5:C2:4E:67:6C:71:2D:9E:7E:9F:EA:A8:29:96:0B:FC:A9:CD:FF:C9:EA:4D:A6:73:83:72:82:4E:87:55`
- Keystore and passwords are intentionally excluded from Git.
- The build workflow reads the key only from GitHub Actions secrets:
  - `MULTIFY_KEYSTORE_BASE64`
  - `MULTIFY_KEYSTORE_PASSWORD`

Never commit the keystore, API keys, TOTP seeds, passwords, access tokens, or a real `.env` file.

The same signing key must be preserved for future APK versions so Android can install them as updates to the same application ID.
