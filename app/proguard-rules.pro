# Add project specific ProGuard rules here.
-keep class com.apextuner.app.** { *; }
-keep class com.apextuner.vpn.** { *; }
-keep class com.apextuner.engine.** { *; }
-keep class com.apextuner.data.** { *; }

-keepclassmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel *;
}
-keep class kotlin.Metadata { *; }
