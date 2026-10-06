# Salim — Offline Bluetooth Mesh Messenger for Android

**Salim** is a private, peer-to-peer messaging app for Android that operates with **zero internet connection, no phone number, and no accounts**, by establishing a local encrypted mesh network over Bluetooth Low Energy (BLE).

---

## 🌟 Core Highlights
- **100% Offline-First**: Works during disasters, remote expeditions, cellular outages, or internet shutdowns.
- **No Identity Tracking**: No accounts, phone numbers, email addresses, or central servers.
- **End-to-End Encrypted**: 1-on-1 private messaging uses X25519 ECDH key agreement with ChaCha20-Poly1305 authenticated encryption and Ed25519 digital signatures.
- **Hardware-Protected Keys**: Long-term identity keys protected via hardware-backed Android Keystore.
- **Multi-Hop Relay**: Automatically routes packets through intermediate nearby devices (up to 10 hops) with deduplication and loop prevention.
- **Store-and-Forward**: Bounded local caching holds encrypted packets for offline recipients and delivers when they re-enter range.
- **Emergency SOS Broadcast**: High-priority alert broadcast across all mesh nodes.
- **Emergency Panic Wipe**: Instant zeroing of private keys and wipe of all messages and cached packets.
- **Modern UI**: Polished Jetpack Compose interface with Material 3 theming (Dark & Light modes).

---

## 🏛️ Architecture Overview

```
[Jetpack Compose UI (Chats, Nearby, Channels, Settings)]
                           │
                 [SalimViewModel]
                           │
      ┌────────────────────┴─────────────────────┐
      ▼                                          ▼
[SalimRepository]                          [MeshEngine]
      │                                          │
 ┌────┴─────────────────┐                  ┌─────┴────────────────┐
 │ Room SQLite Database │                  │ Packet Codec & Dedupe│
 │ Android Keystore Enc │                  │ Store & Forward Cache│
 └──────────────────────┘                  └─────┬────────────────┘
                                                 ▼
                                        [BleMeshTransport]
                                 (Dual Peripheral & Central BLE)
```

---

## 📦 Building & Testing

### Prerequisites
- JDK 17 or higher
- Android SDK with API 36

### Build APK
```bash
gradle assembleDebug
```

### Run Tests
```bash
gradle testDebugUnitTest
```

---

## 📜 Documentation Links
- [Architecture & Modules](docs/ARCHITECTURE.md)
- [Binary Protocol Specification](docs/PROTOCOL.md)
- [Security & Threat Model](docs/SECURITY.md)
- [Design System & UI Tokens](docs/DESIGN.md)
- [Testing & Simulation Plan](docs/TESTING.md)
- [Release Checklist & Play Store Guide](docs/RELEASE.md)

---

## 📄 License
Licensed under the Apache License, Version 2.0.
