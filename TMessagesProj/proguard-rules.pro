-keep public class com.google.android.gms.* { public *; }
-keepnames @com.google.android.gms.common.annotation.KeepName class *
-keepclassmembernames class * {
    @com.google.android.gms.common.annotation.KeepName *;
}
-dontwarn com.google.android.gms.**
-dontwarn com.google.common.cache.**
-dontwarn com.google.common.primitives.**
-keep public class com.google.android.gms.common.* {public *;}
-keep public class com.googlecode.mp4parser.authoring.tracks.mjpeg.OneJpegPerIframe.* {public *;}
# Use -keep to explicitly keep any other classes shrinking would remove
-dontobfuscate
-dontwarn android.support.v4.**
-dontwarn com.googlecode.mp4parser.**
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class org.telegram.tgnet.** {*;}
-keepclasseswithmembers class * {
    native <methods>;
}
-keepclassmembers class * extends com.sun.jna.** {
    <fields>;
    <methods>;
}
