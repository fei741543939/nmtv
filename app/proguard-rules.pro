# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Media3 / Jellyfin FFmpeg 扩展：JNI 依赖 Java 类名与方法签名，R8 混淆或裁剪会导致加载 native 后崩溃
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-dontwarn androidx.media3.decoder.ffmpeg.**
# 后台对接 / 语音 / 设备
-keep class top.yogiczy.mytv.data.repositories.backend.** { *; }
-keep class top.yogiczy.mytv.utils.** { *; }
-keep class top.yogiczy.mytv.ui.screens.leanback.auth.** { *; }
-keepattributes *Annotation*
-keepclassmembers class ** {
  @kotlinx.serialization.Serializable <fields>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
