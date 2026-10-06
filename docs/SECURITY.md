# Security & Threat Model

## 1. Cryptographic Primitives
Salim employs audited, vetted cryptographic standards (via Bouncy Castle v1.78.1 & Android Keystore):
- **Digital Signatures**: Ed25519 (RFC 8032)
- **Key Agreement**: X25519 ECDH (RFC 7748)
- **Key Derivation**: HKDF-SHA256 (RFC 5869)
- **Authenticated Encryption**: ChaCha20-Poly1305 AEAD (RFC 8439) with random 96-bit nonces.
- **At-Rest Key Storage**: Hardware-backed AES-256-GCM via Android Keystore.

## 2. What Is Protected
1. **End-to-End Encryption**: 1-on-1 private messages are readable only by sender and recipient. Forwarding mesh nodes cannot view, tamper with, or forge private messages.
2. **Message Integrity & Authenticity**: Every signed packet is verified against the sender's public key; tampered packets are dropped.
3. **No Central Server Vulnerability**: No accounts, phone numbers, or metadata logs are stored on remote servers.
4. **Panic Wipe**: Erases all keys, conversation history, and cache entries in milliseconds, overwriting the identity file with zeroes and deleting the Keystore alias.

## 3. Threat Model & Limitations
- **Public Channels**: Messages in the Public Nearby Channel are unencrypted broadcasts readable by any device in radio range.
- **Proximity & Metadata**: An adversary with a directional RF antenna can observe that BLE traffic is occurring between physical locations and estimate distance.
- **Device Seizure**: If the device is seized in an unlocked state before triggering Panic Wipe, messages stored on the device may be accessed.
- **Physical Jamming**: High-power radio frequency jamming can degrade or block Bluetooth communications in the immediate vicinity.

Salim recommends that production deployments undergo an independent third-party security audit prior to sensitive mission-critical usage.
