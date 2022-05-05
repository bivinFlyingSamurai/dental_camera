# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/buildwin/Library/Android/sdk/tools/proguard/proguard-android.txt
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

# 经过简单测试，基本功能都OK，但是没有经过全面测试，慎用！！！

-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

-keepattributes *Annotation*

-keep class tv.danmaku.ijk.media.player.annotations.*
-keepclasseswithmembers class * {
    native <methods>;
    @tv.danmaku.ijk.media.player.annotations.CalledByNative <methods>;
    @tv.danmaku.ijk.media.player.annotations.AccessedByNative <fields>;
}

-keep interface tv.danmaku.ijk.media.player.misc.* { *; }

-keep class com.squareup.otto.*
-keepclasseswithmembers class * {
    @com.squareup.otto.Subscribe <methods>;
}
