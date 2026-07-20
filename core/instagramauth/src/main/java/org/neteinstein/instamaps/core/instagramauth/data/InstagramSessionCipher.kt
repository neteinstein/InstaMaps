package org.neteinstein.instamaps.core.instagramauth.data

/**
 * Encrypts/decrypts the Instagram session cookie before it touches disk. Kept as an interface
 * (implemented by [AndroidKeystoreInstagramSessionCipher]) so [EncryptedInstagramAuthRepository]
 * can be unit-tested on a plain JVM with a fake, without an Android Keystore available.
 */
interface InstagramSessionCipher {
    /** Encrypts [plainText], returning an opaque string safe to store in DataStore. */
    fun encrypt(plainText: String): String

    /** Reverses [encrypt]. Returns `null` if [cipherText] can't be decrypted (e.g. key was lost). */
    fun decrypt(cipherText: String): String?
}
