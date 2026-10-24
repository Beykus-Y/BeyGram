package com.beykus.beygram.data.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.beykus.beygram.domain.model.ChatExportTarget
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Writes directly to the document chosen in the system CreateDocument picker. The export never creates a
 * plaintext copy in app storage. On failure or cancellation the document is deleted; if the provider refuses
 * deletion, it is truncated to zero bytes instead.
 */
class SafChatExportTarget(
    context: Context,
    private val uri: Uri
) : ChatExportTarget {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private var discarded = false

    override suspend fun openOutput(): OutputStream = withContext(Dispatchers.IO) {
        openStream("wt") ?: openStream("w") ?: error("The selected document could not be opened")
    }

    private fun openStream(mode: String): OutputStream? = runCatching { resolver.openOutputStream(uri, mode) }.getOrNull()

    override suspend fun discard() {
        withContext(NonCancellable + Dispatchers.IO) {
            if (discarded) return@withContext
            discarded = true
            val deleted = runCatching {
                if (DocumentsContract.isDocumentUri(appContext, uri)) {
                    DocumentsContract.deleteDocument(resolver, uri)
                } else {
                    resolver.delete(uri, null, null) > 0
                }
            }.getOrDefault(false)
            if (!deleted) {
                // Some providers create the document but refuse DocumentsContract.deleteDocument. Truncating still
                // guarantees that no partial plaintext is left behind in a file the user may later open or sync.
                runCatching { (openStream("wt") ?: openStream("w"))?.use { } }
            }
        }
    }
}
