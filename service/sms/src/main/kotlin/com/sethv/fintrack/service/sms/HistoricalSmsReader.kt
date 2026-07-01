package com.sethv.fintrack.service.sms

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import com.sethv.fintrack.core.model.RawSms
import javax.inject.Inject

class HistoricalSmsReader @Inject constructor(
    private val contentResolver: ContentResolver,
) {

    fun readAllSms(): List<RawSms> {
        val smsList = mutableListOf<RawSms>()
        val uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf("address", "body", "date")
        val sortOrder = "date DESC"

        val cursor: Cursor? = contentResolver.query(uri, projection, null, null, sortOrder)
        cursor?.use {
            val addressIndex = it.getColumnIndexOrThrow("address")
            val bodyIndex = it.getColumnIndexOrThrow("body")
            val dateIndex = it.getColumnIndexOrThrow("date")

            while (it.moveToNext()) {
                val sender = it.getString(addressIndex) ?: continue
                val body = it.getString(bodyIndex) ?: continue
                val timestamp = it.getLong(dateIndex)

                smsList.add(RawSms(sender = sender, body = body, timestamp = timestamp))
            }
        }
        return smsList
    }
}
