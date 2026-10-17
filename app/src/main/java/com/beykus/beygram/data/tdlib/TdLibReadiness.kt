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
                add("Не настроен доступ к Telegram в этой сборке.")
            }
            if (javaBindingsStatus is TdLibRuntimeStatus.Missing) {
                add("В приложении не хватает компонентов Telegram.")
            }
            if (nativeLibraryStatus is TdLibRuntimeStatus.Missing) {
                add("В приложении не хватает библиотеки Telegram.")
            }
        }
}
