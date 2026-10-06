# Testing Plan & Verification

## 1. Automated Test Suites
- **Packet Codec Tests (`SalimPacketTest`)**:
  - Valid binary encode/decode roundtrips.
  - Rejection of truncated or corrupted packets.
  - Protocol version mismatch rejection.
  - Signable byte buffer alignment.
- **Crypto Primitives Tests (`CryptoManagerTest`)**:
  - Ed25519 signature creation & verification (positive & tampered vectors).
  - X25519 ECDH key agreement symmetry between Alice & Bob.
  - HKDF-SHA256 session key derivation.
  - ChaCha20-Poly1305 AEAD authenticated encryption & decryption.
  - Deterministic 32-digit safety number computation.
- **Mesh Simulator Tests (`MeshSimulatorTest`)**:
  - In-memory virtual mesh transmission between multiple nodes with configurable latency.

## 2. Real-Device Testing Matrix

| Scenario | Devices Required | Steps | Expected Result |
|---|---|---|---|
| Direct 1-on-1 Chat | 2 phones | Open app on Phone A and Phone B; tap "Chat" in Nearby tab; send message | Message arrives instantaneously; double checkmark shows delivered |
| Multi-hop Relay (A → B → C) | 3 phones | Place Phone A out of range of Phone C, with Phone B in between | Phone B automatically forwards packet; message delivered to C |
| Public Channel Broadcast | 3+ phones | Send message in Public Channel from Phone A | All connected devices in mesh display message |
| Store-and-Forward | 2 phones | Turn off Bluetooth on Phone B; send message from Phone A; turn Bluetooth back on | Message is held by Phone A, then delivered once Phone B re-enters range |
| Emergency SOS Broadcast | 2+ phones | Trigger SOS broadcast with 3s confirmation | Loud alert & notification triggers across all connected mesh nodes |
| Panic Wipe | 1 phone | Tap "Wipe All Data Now" in Settings; confirm | App resets immediately to onboarding; all keys erased from Keystore |
| 8-Hour Battery Test | 1 phone | Run app in Balanced mode for 8 hours with mesh active | Battery drain under 3–5% per hour |
