package org.neteinstein.instamaps.core.instagramauth.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val KEY_ALIAS = "instagram_auth_session_key"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH_BITS = 128

/**
 * [InstagramSessionCipher] backed by an AES-256-GCM key generated inside the Android Keystore
 * (the `AndroidKeyStore` provider) - the raw key material never leaves secure hardware/OS-backed
 * storage and is never exposed to app code. Stores `"<base64 iv>:<base64 ciphertext>"`, since GCM
 * needs the IV to decrypt and `Cipher` generates a fresh random one per [encrypt] call.
 *
 * Deliberately thin, untested SDK glue (matches the existing precedent of
 * `YtDlpVideoDownloadRepository`/`MediaMetadataRetrieverFrameExtractor` - see `agents.md`'s
 * Testing Standards): this class only talks to `javax.crypto`/`KeyStore`, with the actual
 * encrypt/decrypt *usage* (blank handling, self-healing on failure) covered by
 * [EncryptedInstagramAuthRepository]'s tests via a fake [InstagramSessionCipher].
 *
 * Keystore-backed keys are device-bound and never survive a backup/restore to a new device
 * (hence the Auto Backup exclusion for this module's DataStore file - see `app`'s
 * `data_extraction_rules.xml`/`full_backup_content.xml`), and a missing/mismatched key raises
 * before any data is read/written - both are treated as "nothing saved" rather than crashing, via
 * [decrypt]'s fail-closed `catch`.
 */
class AndroidKeystoreInstagramSessionCipher : InstagramSessionCipher {
    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val payload = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        return "$iv:$payload"
    }

    override fun decrypt(cipherText: String): String? =
        try {
            val (ivPart, payloadPart) = cipherText.split(":", limit = 2)
            val iv = Base64.decode(ivPart, Base64.NO_WRAP)
            val ciphertext = Base64.decode(payloadPart, Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to decrypt Instagram session - treating as logged out", exception)
            null
        }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE_PROVIDER)
        val spec =
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val TAG = "InstagramSessionCipher"
    }
}
