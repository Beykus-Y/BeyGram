package com.beykus.beygram.data.tdlib

import android.content.Context

class TdLibBootstrap(
    context: Context,
    val config: TdLibConfig = TdLibConfig.from(context.applicationContext)
) {
    fun readiness(): TdLibReadiness =
        TdLibReadiness(
            credentialsConfigured = config.hasApiCredentials,
            javaBindingsStatus = TdLibRuntime.checkJavaBindings(),
            nativeLibraryStatus = TdLibRuntime.checkNativeLibrary()
        )

    fun runtimeStatus(): TdLibRuntimeStatus = TdLibRuntime.checkNativeLibrary()

    fun canCreateClient(): Boolean =
        readiness().canCreateClient
}
