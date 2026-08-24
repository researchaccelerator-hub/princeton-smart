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

# AWSMobileClient (pulled in transitively by Amplify's aws-auth-cognito) references optional
# Facebook/Google/Cognito-user-pools hosted sign-in UI classes that only exist if those separate,
# opt-in SDK artifacts are also added as dependencies. This app doesn't use AWSMobileClient's
# hosted UI (only Amplify's own sign-in flow), so those classes are never actually loaded, but R8
# still needs to be told they're safe to leave unresolved. Per AWS's own documented Proguard
# guidance: https://github.com/aws-amplify/aws-sdk-android/blob/main/Proguard.md
-dontwarn com.amazonaws.mobile.auth.**
-dontwarn com.amazonaws.mobileconnectors.cognitoauth.**
