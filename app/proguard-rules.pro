# ProGuard rules for WifiKidsControl

# Keep Hilt generated code
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep class * extends dagger.hilt.android.HiltApplication

# Keep Room entities and DAOs
-keep class com.wifikidscontrol.model.** { *; }
-keep class com.wifikidscontrol.db.** { *; }

# Keep Moshi JSON adapters
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class *
-keepclassmembers class * {
    @com.squareup.moshi.Json **;
}

# Keep Retrofit interfaces
-keep interface com.wifikidscontrol.network.RouterApi { *; }
-keep class com.wifikidscontrol.network.* { *; }

# Keep Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep Kotlinx Serialization
-keep class kotlinx.serialization.** { *; }
-keep @kotlinx.serialization.Serializable class * { *; }

# Keep VpnService
-keep class com.wifikidscontrol.network.LocalVpnService { *; }

# Keep Application class
-keep class com.wifikidscontrol.WifiKidsApplication { *; }

# Keep ViewModels
-keep class com.wifikidscontrol.ui.viewmodel.** { *; }

# Keep Compose
-keep class androidx.compose.** { *; }
-keep class org.jetbrains.compose.** { *; }

# Keep OkHttp
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Keep Kotlinx DateTime
-keep class kotlinx.datetime.** { *; }

# Suppress warnings
-dontwarn kotlinx.coroutines.**
-dontwarn kotlinx.serialization.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn com.squareup.moshi.**
-dontwarn dagger.hilt.**
-dontwarn androidx.hilt.**