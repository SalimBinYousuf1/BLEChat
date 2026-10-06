# Salim Mesh Binary Protocol (v1)

## 1. Packet Wire Format

Every wire packet has a 46-byte binary header followed by an optional variable payload and a 64-byte Ed25519 digital signature.

| Offset | Field | Type | Description |
|---|---|---|---|
| 0 | `version` | 1 byte | `0x01` protocol version |
| 1 | `type` | 1 byte | Packet type code |
| 2 | `flags` | 1 byte | Bitmask: Encrypted (0x01), Signed (0x02), NeedsAck (0x04), IsFragment (0x08), Broadcast (0x10) |
| 3 | `ttl` | 1 byte | Time-To-Live hop counter (Default 7, max 10) |
| 4..19 | `messageId` | 16 bytes | Cryptographically random UUID |
| 20..27 | `senderId` | 8 bytes | Truncated SHA-256 fingerprint of sender Ed25519 public key |
| 28..35 | `recipientId` | 8 bytes | Intended peer ID, or `0xFFFFFFFFFFFFFFFF` for broadcast |
| 36..43 | `timestamp` | 8 bytes | Big-endian Unix epoch timestamp in milliseconds |
| 44..45 | `payloadLength` | 2 bytes | Big-endian unsigned 16-bit integer |
| 46..46+N | `payload` | N bytes | Message content or ciphertext |
| End-64..End | `signature` | 64 bytes | Ed25519 signature over bytes 0..46+N (present if SIGNED flag set) |

## 2. Packet Types
- `0x01`: `HELLO` — Link keepalive & latency check
- `0x02`: `ANNOUNCE` — Identity broadcast with Ed25519 & X25519 public keys
- `0x03`: `MESSAGE` — Text/media message (encrypted or broadcast)
- `0x04`: `ACK` — Delivery confirmation carrying original `messageId`
- `0x05`: `FRAGMENT` — Sub-packet carrying chunked payload for MTU compliance
- `0x06`: `GROUP_KEY` — Key distribution for group chats
- `0x07`: `SOS` — High-priority emergency broadcast
- `0x08`: `LEAVE` — Node graceful departure notice

## 3. Fragmentation
When encoded packets exceed the link MTU (default 247 bytes), they are fragmented:
```
[totalChunks: 1 byte][chunkIndex: 1 byte][origMsgId: 16 bytes][chunkData: N bytes]
```
Reassembly re-constructs the original packet once all fragments arrive. Missing fragments expire after 30 seconds.

## 4. Routing & Deduplication
- **Deduplication**: Nodes track recently observed `messageId`s in Room SQLite and in-memory LRU cache. Seen packets are dropped immediately.
- **Controlled Flooding**: Upon receiving a packet for forwarding, TTL is decremented. If TTL > 0, transmission is delayed by a randomized jitter (50–180ms) to prevent radio collisions.
- **Store-and-Forward**: Undelivered encrypted direct messages are cached locally for up to 24 hours. When the target recipient advertises presence, cached packets are flushed.
