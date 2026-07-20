# GitRepoManager ProGuard rules

# Keep data models used for Kotlinx Serialization (GitHub API DTOs)
-keep,includedescriptorclasses class com.lmob.gitrepomanager.data.model.**$$serializer { *; }
-keepclassmembers class com.lmob.gitrepomanager.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.lmob.gitrepomanager.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**

# OkHttp
-dontwarn okio.**

# Hilt / Dagger
-dontwarn com.google.errorprone.annotations.**

# Kotlin Serialization general
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
