package com.beykus.beygram.domain.model

/** Reasons offered when a message is reported to Telegram. The data layer matches them to the options Telegram returns. */
enum class MessageReportReason { Spam, Violence, PersonalDetails }
