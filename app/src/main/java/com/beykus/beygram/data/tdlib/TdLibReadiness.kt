package com.beykus.beygram.data.tdlib

data class TdLibReadiness(
    val credentialsConfigured: Boolean,
    val javaBindingsStatus: TdLibRuntimeStatus,
    val nativeLibraryStatus: TdLibRuntimeStatus
) {
    val canCreateClient: Boolean
        get() = credentialsConfigured &&
            javaBindingsStatus is TdLibRuntimeStatus.Available &&
            nativeLibraryStatus is TdLibRuntimeStatus.Available

    val issues: List<String>
        get() = buildList {
            if (!credentialsConfigured) {
                add("Не заполнены telegramApiId/telegramApiHash в secrets.properties.")
            }
            if (javaBindingsStatus is TdLibRuntimeStatus.Missing) {
                add("Нет Java bindings org.drinkless.tdlib в APK.")
            }
            if (nativeLibraryStatus is TdLibRuntimeStatus.Missing) {
                add("Нет native-библиотеки libtdjni.so в app/src/main/jniLibs.")
            }
        }
}
