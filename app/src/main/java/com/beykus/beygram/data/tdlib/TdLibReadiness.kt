package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText

data class TdLibReadiness(
    val credentialsConfigured: Boolean,
    val javaBindingsStatus: TdLibRuntimeStatus,
    val nativeLibraryStatus: TdLibRuntimeStatus
) {
    val canCreateClient: Boolean
        get() = credentialsConfigured &&
            javaBindingsStatus is TdLibRuntimeStatus.Available &&
            nativeLibraryStatus is TdLibRuntimeStatus.Available

    val issues: List<UiText>
        get() = buildList<UiText> {
            if (!credentialsConfigured) {
                add(UiText.Res(R.string.err_tdlib_credentials_missing))
            }
            if (javaBindingsStatus is TdLibRuntimeStatus.Missing) {
                add(UiText.Res(R.string.err_tdlib_java_missing))
            }
            if (nativeLibraryStatus is TdLibRuntimeStatus.Missing) {
                add(UiText.Res(R.string.err_tdlib_native_missing))
            }
        }
}
