-keep class com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver { *; }
-keep class com.focusguard.app.service.FocusGuardAccessibilityService { *; }
-keep class com.focusguard.app.service.FocusSessionService { *; }

# Receivers and Workers
-keep class com.focusguard.app.receiver.** { *; }
-keep class com.focusguard.app.worker.** { *; }

# Room Database & TypeConverters
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# Data & Domain Models (prevent Firestore serialization field stripping)
-keep class com.focusguard.app.core.model.** { *; }
-keepclassmembers class com.focusguard.app.core.model.** { *; }
-keep class com.focusguard.app.data.model.** { *; }
-keep class com.focusguard.app.security.PresetIntent { *; }
-keep class com.focusguard.app.data.model.ReelTargetConfig { *; }
-keep class com.focusguard.app.data.model.ReelTargetRegistry { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# DataStore
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }

# Release logging: strip verbose, debug, and info logs
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
