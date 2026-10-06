# Salim ProGuard/R8 Rules

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable

# Bouncy Castle Crypto Provider
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ZXing Core QR Code Library
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Room Database
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase

# Salim Core Models & Packets
-keep class com.example.salim.core.model.** { *; }
-keep class com.example.salim.core.storage.** { *; }
-keep class com.example.salim.core.crypto.** { *; }
