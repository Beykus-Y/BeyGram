package com.beykus.beygram.data.music

import android.content.Context
import java.io.File

/** Directory under filesDir that holds one library file per account. */
const val MUSIC_LIBRARY_DIRECTORY = "music_library"

/** App-private store: `filesDir/music_library/<sha256(accountId)>.json`. All file work is in [MusicLibraryFileStore]. */
class MusicLibraryStore(context: Context) : MusicLibraryPersistence by MusicLibraryFileStore(
    File(context.applicationContext.filesDir, MUSIC_LIBRARY_DIRECTORY)
)
