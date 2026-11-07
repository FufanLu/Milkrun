package com.milkrun.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import com.milkrun.presentation.SessionStore
import com.milkrun.protocol.DevSession
import com.milkrun.protocol.SyncApi
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Shift credentials encrypted with an AES-256-GCM key that lives in the Android Keystore and
 * never leaves it (non-exportable). What reaches SharedPreferences is `base64(iv | ciphertext)`;
 * a copied prefs file or a backup is useless without this device's key.
 */
class KeystoreSessionStore(context: Context) : SessionStore {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    override fun load(): DevSession? {
        val blob = prefs.getString(PREF, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(blob, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
            val json = cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES).decodeToString()
            SyncApi.json.decodeFromString(DevSession.serializer(), json)
        }.getOrNull() // tampered or from another device: start over rather than crash
    }

    override fun save(session: DevSession) {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed =
            cipher.iv +
                cipher.doFinal(SyncApi.json.encodeToString(DevSession.serializer(), session).encodeToByteArray())
        prefs.edit { putString(PREF, Base64.encodeToString(sealed, Base64.NO_WRAP)) }
    }

    override fun clear() = prefs.edit { remove(PREF) }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "milkrun-session"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val PREF = "session.sealed"
    }
}
