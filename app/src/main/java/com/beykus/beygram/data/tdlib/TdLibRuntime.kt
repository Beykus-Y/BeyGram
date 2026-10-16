package com.beykus.beygram.data.tdlib

sealed interface TdLibRuntimeStatus {
    data object Available : TdLibRuntimeStatus
    data class Missing(val reason: String) : TdLibRuntimeStatus
}

object TdLibRuntime {
    fun checkJavaBindings(): TdLibRuntimeStatus =
        runCatching {
            Class.forName("org.drinkless.tdlib.Client")
            Class.forName("org.drinkless.tdlib.TdApi")
        }.fold(
            onSuccess = { TdLibRuntimeStatus.Available },
            onFailure = { error ->
                TdLibRuntimeStatus.Missing(
                    reason = error.message ?: "TDLib Java bindings were not found"
                )
            }
        )

    fun checkNativeLibrary(libraryName: String = "tdjni"): TdLibRuntimeStatus =
        runCatching {
            System.loadLibrary(libraryName)
        }.fold(
            onSuccess = { TdLibRuntimeStatus.Available },
            onFailure = { error ->
                TdLibRuntimeStatus.Missing(
                    reason = error.message ?: "Native TDLib library was not loaded"
                )
            }
        )
}
