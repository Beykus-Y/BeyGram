package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.TdApi

/** TDLib request shapes live here so JVM tests can verify fields without a running client. */
internal fun importedContact(firstName: String, lastName: String, phone: String): TdApi.ImportedContact =
    TdApi.ImportedContact(phone, firstName, lastName, TdApi.FormattedText("", emptyArray<TdApi.TextEntity>()))

/**
 * TDLib edits an existing contact through `addContact` (the jar has no `EditImportedContact`).
 * `sharePhoneNumber` is false: editing a contact must never expose the current user's number.
 */
internal fun addContactRequest(userId: Long, firstName: String, lastName: String, phone: String): TdApi.AddContact =
    TdApi.AddContact(userId, importedContact(firstName, lastName, phone), false)

internal fun removeContactRequest(userId: Long): TdApi.RemoveContacts =
    TdApi.RemoveContacts(longArrayOf(userId))

internal fun contactMessageContent(contact: TdApi.Contact): TdApi.InputMessageContact =
    TdApi.InputMessageContact(contact)
