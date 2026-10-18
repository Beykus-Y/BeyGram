package com.beykus.beygram.domain.model

/** What the user is doing in a chat. Other members see it as "печатает…", "записывает голосовое…" and so on. */
enum class ChatActivity { Typing, RecordingVoice, RecordingVideoNote, UploadingPhoto, UploadingVideo, UploadingVideoNote, UploadingVoice, UploadingDocument }
