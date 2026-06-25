# Kotlinx Serialization: keep generated serializers for @Serializable models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.aurorascan.** {
    *** Companion;
}
-keepclasseswithmembers class com.aurorascan.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room, Hilt, ML Kit and WorkManager ship their own consumer rules.
