# Keep kotlinx.serialization models
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <init>(...);
}
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Room database & DAO implementations
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Entity class * { *; }

# Keep Media3 session
-keep class androidx.media3.session.** { *; }

# Keep Retrofit API & DTOs
-keep interface com.sangita.app.data.remote.api.** { *; }
-keep class com.sangita.app.data.remote.dto.** { *; }
-keep class com.sangita.app.domain.model.** { *; }
