package com.beykus.beygram.data.music

import android.content.Context
import java.io.File

/** Directory under filesDir for cover images. Only files inside it are accepted as playlist covers. */
const val MUSIC_COVER_DIRECTORY = "music_covers"

/**
 * The process-wide library. The history recorder and the screens share this one instance, so they see the same state.
 */
object MusicLibraries {
    @Volatile
    private var instance: MusicLibraryRepository? = null

    fun get(context: Context): MusicLibraryRepository {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: run {
                val appContext = context.applicationContext
                MusicLibraryRepository(
                    persistence = MusicLibraryStore(appContext),
                    coverRoot = File(appContext.filesDir, MUSIC_COVER_DIRECTORY),
                    coverOwners = PreferencesMusicCoverOwners(
                        appContext.getSharedPreferences(MUSIC_COVER_OWNERS_PREFERENCES, Context.MODE_PRIVATE)
                    )
                ).also { instance = it }
            }
        }
    }
}
