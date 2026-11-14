# Methods the page calls through addJavascriptInterface.
-keepclassmembers class tv.cleanwatch.** {
    @android.webkit.JavascriptInterface <methods>;
}
