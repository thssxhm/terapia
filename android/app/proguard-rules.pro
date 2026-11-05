# kotlinx.serialization (modelos do Supabase)
-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class app.terapia.mobile.**$$serializer { *; }
-keepclassmembers class app.terapia.mobile.** { *** Companion; }
-keepclasseswithmembers class app.terapia.mobile.** { kotlinx.serialization.KSerializer serializer(...); }
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
