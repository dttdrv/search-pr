-keepattributes *Annotation*, InnerClasses, Signature

# kotlinx.serialization
-keepclassmembers class app.pane.** {
    *** Companion;
}
-keepclasseswithmembers class app.pane.** {
    kotlinx.serialization.KSerializer serializer(...);
}
