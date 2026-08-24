package com.sethv.fintrack.service.sms

/**
 * Shared dedup key math for SMS-originated rows.
 *
 * The same physical message reaches the app through two paths whose clocks
 * differ: the live broadcast stamps the SMSC timestamp while the historical
 * scan reads the handset "date" column. Exact-timestamp equality therefore
 * misses duplicates, so fingerprints bucket timestamps to the minute.
 */
internal object SmsFingerprint {

    const val MINUTE_BUCKET_MILLIS = 60_000L

    fun minuteBucketOf(epochMillis: Long): Long = epochMillis.floorDiv(MINUTE_BUCKET_MILLIS)
}
