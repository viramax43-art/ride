-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

-keep,allowobfuscation,allowshrink @dagger.hilt.android.HiltAndroidApp class *
-keep class dagger.hilt.** { *; }

-keepattributes Signature, InnerClasses, EnclosingMethod
-keep,allowshrinking,allowobfuscation interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class com.rideminiapp.BuildConfig { *; }
