# Add project specific ProGuard rules here.
# Keep model classes used for JSON (Gson) serialization
-keep class com.localnet.wifihome.data.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
