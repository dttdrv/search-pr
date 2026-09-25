# GeckoView ships its own consumer rules.
-keepattributes *Annotation*, InnerClasses, Signature
-dontwarn org.mozilla.**

# kotlinx.serialization
-keepclassmembers class app.pane.** {
    *** Companion;
}
-keepclasseswithmembers class app.pane.** {
    kotlinx.serialization.KSerializer serializer(...);
}
