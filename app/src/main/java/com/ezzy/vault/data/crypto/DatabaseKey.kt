package com.ezzy.vault.data.crypto

import android.content.Context
import android.util.Base64
import java.security.SecureRandom

/**
 * Owns the SQLCipher passphrase. The passphrase itself is random and never leaves the device:
 * it is sealed with a Keystore key and the sealed blob is what sits in shared preferences,
 * so a stolen backup of the app directory yields nothing readable.
 */
class DatabaseKey(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("ezzy_keys", Context.MODE_PRIVATE)
    private val crypto = KeystoreCrypto(KEY_ALIAS)

    /** A fresh copy every call — SQLCipher zeroes the array it is handed. */
    fun passphrase(): ByteArray {
        val stored = prefs.getString(PREF_SEALED, null)
        if (stored != null) {
            runCatching { crypto.decrypt(Base64.decode(stored, Base64.NO_WRAP)) }
                .onSuccess { return it }
        }
        return create()
    }

    private fun create(): ByteArray {
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val sealed = Base64.encodeToString(crypto.encrypt(raw), Base64.NO_WRAP)
        prefs.edit().putString(PREF_SEALED, sealed).apply()
        return raw
    }

    /** Used by "erase everything" — without the key the database bytes are unreadable. */
    fun destroy() {
        prefs.edit().remove(PREF_SEALED).apply()
        crypto.deleteKey()
    }

    private companion object {
        const val KEY_ALIAS = "ezzy_db_key_v1"
        const val PREF_SEALED = "sealed_db_passphrase"
    }
}
