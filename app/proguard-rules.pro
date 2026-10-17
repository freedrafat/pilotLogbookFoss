# Keep Kotlin reflection (used by some libraries)
-keep class kotlin.reflect.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.** {
    *** Companion;
}

# Room
-keep class androidx.room.** { *; }
-keepclassmembers class * extends androidx.room.Database {
    *;
}
-keep class * extends androidx.room.RoomDatabase

# OkHttp
-keepattributes Signature
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn javax.annotation.**

# Your data models (if serialised by Kotlinx)
-keep class com.my.striptologbook.data.** { *; }

# Keep all classes that use @Serializable annotation
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}