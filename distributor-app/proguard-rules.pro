-keepattributes Signature
-keepattributes *Annotation*
-keep class com.osgateway.shared.model.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
