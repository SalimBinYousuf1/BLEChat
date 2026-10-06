# Play Store & Release Readiness

## 1. Store Listing Metadata
- **App Name**: Salim: Offline Mesh Messenger
- **Short Description (80 chars)**: Private offline mesh messenger. Zero internet. No phone number. Fully encrypted.
- **Category**: Communication
- **Content Rating**: Everyone 10+ (Unmoderated user communication)
- **Tags**: Offline messaging, Bluetooth mesh, encrypted chat, P2P, disaster communication

## 2. Google Play Data Safety Questionnaire
- **Data Collected**: None (0 bytes).
- **Data Shared**: None.
- **Data Encrypted in Transit**: Yes, private communications are encrypted end-to-end with forward secrecy.
- **Data Deletion Mechanism**: Supported directly in-app via "Emergency Panic Wipe" and standard app uninstall.

## 3. Permission Justifications
- `BLUETOOTH_SCAN` / `BLUETOOTH_ADVERTISE` / `BLUETOOTH_CONNECT`: Essential core functionality for discovering nearby mesh peers and exchanging encrypted packets without internet. `usesPermissionFlags="neverForLocation"` is declared to guarantee zero location tracking on Android 12+.
- `POST_NOTIFICATIONS`: Required for background message alerts and persistent foreground service status.
- `FOREGROUND_SERVICE_CONNECTED_DEVICE`: Required for continuous peer-to-peer packet routing in the background.
- `CAMERA`: Optional runtime permission used exclusively for scanning contact verification QR codes.
- `RECORD_AUDIO`: Optional runtime permission used exclusively when the user records voice notes.
