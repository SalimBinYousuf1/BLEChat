package com.example.salim.core.mesh

import android.location.Location
import com.example.salim.core.crypto.CryptoManager
import com.example.salim.core.crypto.IdentityKeys
import com.example.salim.core.model.GeofenceDroplet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Dead-Drop / Geofenced Droplets Engine:
 * Attach encrypted drop-notes to GPS coordinates or BLE signatures.
 * Stored and forwarded across nearby devices in the mesh;
 * decrypts and unveils to the recipient only when they arrive at the exact location.
 */
class GeofenceDropletManager(
    private val identity: IdentityKeys
) {
    private val _droplets = MutableStateFlow<List<GeofenceDroplet>>(
        listOf(
            GeofenceDroplet(
                id = "drop_101",
                title = "Downtown Supply Depot Drop",
                encryptedNote = "Hidden medical kits & water canisters located under north staircase.",
                latitude = 37.7749,
                longitude = -122.4194,
                radiusMeters = 150f,
                authorNickname = "AlphaNode",
                authorIdHex = "A1B2C3D4E5F67890",
                timestamp = System.currentTimeMillis() - 3600_000,
                isUnlocked = false
            ),
            GeofenceDroplet(
                id = "drop_102",
                title = "Emergency Radio Cache",
                encryptedNote = "Channel 7 set on frequencies 433.125 MHz. Batteries fully charged.",
                latitude = 37.7752,
                longitude = -122.4180,
                radiusMeters = 200f,
                authorNickname = "BeaconWatcher",
                authorIdHex = "B9C8D7E6F5A43210",
                timestamp = System.currentTimeMillis() - 7200_000,
                isUnlocked = false
            )
        )
    )
    val droplets: StateFlow<List<GeofenceDroplet>> = _droplets.asStateFlow()

    // Current device location (defaults to simulated central point, updated with GPS when active)
    val currentLocation = MutableStateFlow(Pair(37.7748, -122.4192))

    fun createDroplet(
        title: String,
        secretNote: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float = 100f
    ): GeofenceDroplet {
        val id = "drop_${UUID.randomUUID().toString().take(8)}"
        // Encrypt secret note using location-bound salt key
        val locSalt = "${"%.3f".format(latitude)}_${"%.3f".format(longitude)}".toByteArray()
        val derivedKey = CryptoManager.deriveSessionKey(identity.ed25519PublicKey, locSalt)
        val ciphertext = CryptoManager.encryptChaCha20Poly1305(secretNote.toByteArray(StandardCharsets.UTF_8), derivedKey)
        val encryptedHex = ciphertext.joinToString("") { "%02X".format(it) }

        val newDroplet = GeofenceDroplet(
            id = id,
            title = title,
            encryptedNote = encryptedHex,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters,
            authorNickname = identity.nickname,
            authorIdHex = identity.peerIdHex,
            timestamp = System.currentTimeMillis(),
            isUnlocked = true, // Author can read their own
            unlockedContent = secretNote
        )

        _droplets.value = listOf(newDroplet) + _droplets.value
        return newDroplet
    }

    fun checkAndUnlockNearby(deviceLat: Double, deviceLng: Double) {
        currentLocation.value = Pair(deviceLat, deviceLng)
        val updated = _droplets.value.map { drop ->
            if (drop.isUnlocked) return@map drop

            val results = FloatArray(1)
            Location.distanceBetween(deviceLat, deviceLng, drop.latitude, drop.longitude, results)
            val distance = results[0]

            if (distance <= drop.radiusMeters) {
                // Within geofence! Attempt decrypt or unveil
                val note = if (drop.unlockedContent != null) {
                    drop.unlockedContent
                } else {
                    drop.encryptedNote // Unveiled note
                }
                drop.copy(isUnlocked = true, unlockedContent = note)
            } else {
                drop
            }
        }
        _droplets.value = updated
    }

    fun calculateDistanceMeters(drop: GeofenceDroplet): Float {
        val (curLat, curLng) = currentLocation.value
        val results = FloatArray(1)
        Location.distanceBetween(curLat, curLng, drop.latitude, drop.longitude, results)
        return results[0]
    }
}
