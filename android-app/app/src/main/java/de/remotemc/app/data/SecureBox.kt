package de.remotemc.app.data

/** Encrypts small secrets at rest (Android: Keystore, Windows: DPAPI). */
interface SecureBox {
    fun encrypt(plain: String): String
    fun decrypt(encrypted: String): String
}
