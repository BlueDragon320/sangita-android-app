package com.sangita.app.data.local.storage

import java.security.MessageDigest

/** One stable storage key per original track path, including paths containing underscores. */
internal fun trackStorageKey(trackId: String): String = MessageDigest.getInstance("SHA-256")
    .digest(trackId.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
