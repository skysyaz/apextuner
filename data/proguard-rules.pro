# Add project specific ProGuard rules here.
-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.serialization.**

# Keep serializable model classes
-keep class com.apextuner.data.model.** { *; }
