# kotlinx.serialization keeps generated serializers reachable; Ktor and SQLDelight ship their own rules.
-keepattributes *Annotation*, InnerClasses
-dontwarn org.slf4j.**
