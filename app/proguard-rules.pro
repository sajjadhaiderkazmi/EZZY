# SQLCipher ships native bindings that are reached reflectively from JNI.
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }

# Template specs are stored as JSON, so their serializers must survive shrinking.
-keepclassmembers class com.ezzy.vault.data.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ezzy.vault.data.model.**$$serializer { *; }
