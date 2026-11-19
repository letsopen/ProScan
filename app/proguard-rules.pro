# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\Administrator\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# --- Huawei HMS Scan Kit Rules ---
-ignorewarnings
-keepattributes *Annotation*
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable

# 华为 HMS 核心库
-keep class com.huawei.hms.** { *; }
-dontwarn com.huawei.hms.**

# 华为特定的系统类（可能在非华为手机上缺失）
-dontwarn android.telephony.HwTelephonyManager
-dontwarn com.huawei.android.os.**
-dontwarn com.huawei.libcore.io.**

# --- Bouncy Castle Rules (Huawei SDK 依赖) ---
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# --- Google ML Kit ---
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# --- ZXing (仅用于生成功能) ---
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# --- ZXing-CPP（JNI 绑定，类与方法名不能被混淆）---
-keep class zxingcpp.** { *; }
-dontwarn zxingcpp.**

# --- BoofCV（纯 Java，排除桌面端/Swing 缺失类告警）---
-keep class boofcv.** { *; }
-keep class org.ddogleg.** { *; }
-keep class georegression.** { *; }
-dontwarn boofcv.**
-dontwarn org.ddogleg.**
-dontwarn georegression.**
-dontwarn pabeles.concurrency.**
-dontwarn org.ejml.**
-dontwarn boofcv.android.**
