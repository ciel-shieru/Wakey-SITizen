# Keep Hilt classes
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep class * extends java.lang.annotation.Annotation
-keep class dagger.** { *; }
-keep class hilt_aggregated_descriptors.** { *; }
-keep class * @dagger.hilt.internal.GeneratedInfo {}

# Keep serialization classes
-keep class com.cielshieru.wakey.domain.model.** { *; }
-keep class com.cielshieru.wakey.data.store.** { *; }
