# Architecture Specification

## 1. Module & Layer Structure
Salim strictly enforces unidirectional data flow (UDF) across clean modular boundaries:

```
[UI Layer (Screens & Composables)]
               │
               ▼
   [Presentation: ViewModels]
               │
               ▼
    [Domain & Routing: MeshEngine]
         │                 │
         ▼                 ▼
[Data: SalimRepository] [Transport: BleMeshTransport / MeshSimulator]
         │                 │
         ▼                 ▼
[Room SQLite / Keystore] [Android BLE Hardware (Central + Peripheral)]
```

### Invariant:
- UI components **never** interact directly with BLE hardware or Bluetooth sockets.
- The `Transport` interface encapsulates all packet transmission. This enables drop-in additions of Wi-Fi Aware, Wi-Fi Direct, or Nostr internet relays without touching domain routing or UI.

## 2. Threading Model
- **UI Thread**: State rendering, compose animations, gesture input.
- **Dispatchers.IO**: Room database queries, Android Keystore cryptographic operations, disk persistence.
- **Dispatchers.Default**: In-memory binary packet serialization/deserialization, Ed25519 signature computation, X25519 ECDH calculations.
- **BLE Callback Thread**: Android BLE GATT callbacks execute on system Binder threads, dispatched immediately into thread-safe coroutine SharedFlows (`incomingPackets`).

## 3. Lifecycle & Background Execution
- `SalimMeshService` runs as an Android Foreground Service with type `connectedDevice`.
- An ongoing system notification ensures Android's OOM killer does not terminate the BLE mesh server when the user switches apps or locks the screen.
- Resumes mesh scanning and advertising upon process restart (`START_STICKY`).
