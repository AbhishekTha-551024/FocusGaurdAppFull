-keep class com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver { *; }
-keep class com.focusguard.app.service.FocusGuardAccessibilityService { *; }
-keep class com.focusguard.app.service.ProtectionWatchdogService { *; }
-keep class com.focusguard.app.service.FocusSessionService { *; }

# Receivers and Workers
-keep class com.focusguard.app.receiver.** { *; }
-keep class com.focusguard.app.worker.** { *; }

# Room Database & TypeConverters
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class com.focusguard.app.data.db.Converters { *; }
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}
-keep interface com.focusguard.app.data.db.*Dao { *; }

# Data & Domain Models (prevent Firestore serialization field stripping)
-keep class com.focusguard.app.core.model.** { *; }
-keepclassmembers class com.focusguard.app.core.model.** { *; }
-keep class com.focusguard.app.data.model.** { *; }
-keep class com.focusguard.app.data.db.** { *; }
-keep class com.focusguard.app.security.PresetIntent { *; }
-keep class com.focusguard.app.data.model.ReelTargetConfig { *; }
-keep class com.focusguard.app.data.model.ReelTargetRegistry { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# DataStore
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }
