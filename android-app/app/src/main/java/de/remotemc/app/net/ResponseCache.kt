package de.remotemc.app.net

import java.security.MessageDigest

/**
 * The last answers of GET requests with their ETag, so the next request can ask the server
 * "changed?" (If-None-Match) and get an empty 304 when nothing changed. Kept in memory only and
 * per server + token; big answers (e.g. opened files) are not kept.
 */
internal object ResponseCache {
    const val MAX_ENTRIES = 64
    const val MAX_BODY = 256 * 1024

    data class Entry(val etag: String, val body: String)

    private val entries = object : LinkedHashMap<String, Entry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > MAX_ENTRIES
    }

    /** The token is hashed so it never sits in the cache in plain text. */
    fun key(url: String, token: String?): String {
        val who = token?.let {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) }.take(16)
        }.orEmpty()
        return "$who $url"
    }

    @Synchronized
    fun get(key: String): Entry? = entries[key]

    @Synchronized
    fun put(key: String, etag: String, body: String) {
        if (body.length > MAX_BODY) entries.remove(key) else entries[key] = Entry(etag, body)
    }

    @Synchronized
    fun clear() = entries.clear()
}
