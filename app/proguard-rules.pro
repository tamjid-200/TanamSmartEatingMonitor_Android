# Keep the JavaScript bridge used by the YouTube WebView
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
