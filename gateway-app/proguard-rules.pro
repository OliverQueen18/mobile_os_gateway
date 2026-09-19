-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

-keep class com.osgateway.shared.model.** { *; }
-keep class com.osgateway.gateway.ussd.** { *; }
-keep class com.osgateway.gateway.sms.** { *; }
-keep class com.osgateway.gateway.service.** { *; }

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
