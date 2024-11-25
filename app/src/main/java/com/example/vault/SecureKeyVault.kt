package com.example.vault

import android.content.Context
import android.os.Build
import android.security.KeyStoreException
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.secretbox.SecretBox
import com.ionspin.kotlin.crypto.secretbox.crypto_secretbox_NONCEBYTES
import com.ionspin.kotlin.crypto.util.LibsodiumRandom
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

const val TAG_LENGTH = 16


class EncryptionOutput(val encryptedMasterKey: ByteArray,
                       val iv: ByteArray,
                       val tag: ByteArray,
                       val ciphertext: ByteArray)

class SecureKeyVault(private val context: Context, private val activity: Fragment) {

    private val keyStoreAlias = "MasterKeyAlias"

    fun init(callback: () -> Unit) {
        // Initialize Libsodium
        if (!LibsodiumInitializer.isInitialized()) {
            LibsodiumInitializer.initializeWithCallback(callback)
        } else {
            callback()
        }
    }

    fun authenticateWithKey(
        onSuccess: (ByteArray) -> Unit,
        onFailure: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                decryptMasterKey(result.cryptoObject?.cipher, onSuccess, onFailure)
                Toast.makeText(context,
                    "Authentication succeeded!", Toast.LENGTH_LONG)
                    .show()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                // Handle errors
                onFailure()
                Toast.makeText(context,
                    "Authentication error: $errString", Toast.LENGTH_LONG)
                    .show()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Handle failed attempts
                onFailure()
                Toast.makeText(context, "Authentication failed",
                    Toast.LENGTH_LONG)
                    .show()
            }
        })

        // Lets the user authenticate using either a Class 3 biometric or
        // their lock screen credential (PIN, pattern, or password).
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authenticate")
            .setSubtitle("Use your fingerprint to access the master key")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    // Get the master key from SharedPreferences and decrypt it using the Android Keystore.
    private fun decryptMasterKey(
        cipherObj: Cipher?,
        onSuccess: (ByteArray) -> Unit,
        onFailure: () -> Unit
    ) {
        try {
            val cipher = cipherObj ?: throw IllegalStateException("CryptoObject Cipher is null")

            var encryptedMasterKey: ByteArray?
            var cipherText: ByteArray?
            var tag: ByteArray?
            var iv: ByteArray?

            val generatedKey = generateMasterKey(cipher)
            if (generatedKey != null) {
                encryptedMasterKey = generatedKey.encryptedMasterKey
                cipherText = generatedKey.ciphertext
                tag = generatedKey.tag
                iv = generatedKey.iv
            } else {
                val sharedPreferences =
                    context.getSharedPreferences("VaultPrefs", Context.MODE_PRIVATE)
                encryptedMasterKey = sharedPreferences.getString("encryptedMasterKey", null)
                    ?.split(",")?.map { it.toByte() }?.toByteArray()
                cipherText = sharedPreferences.getString("cipherText", null)
                    ?.split(",")?.map { it.toByte() }?.toByteArray()
                tag = sharedPreferences.getString("tag", null)
                    ?.split(",")?.map { it.toByte() }?.toByteArray()
                iv = sharedPreferences.getString("iv", null)
                    ?.split(",")?.map { it.toByte() }?.toByteArray()
            }

            if (encryptedMasterKey == null || iv == null || tag == null || cipherText == null) {
                onFailure()
                return
            }

            val spec = GCMParameterSpec(TAG_LENGTH * 8, iv)
//            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
//
//            }

            cipher.init(Cipher.DECRYPT_MODE, getKeystoreKey(), spec)

            onSuccess(cipher.doFinal(cipherText + tag))
        } catch (e: Exception) {
            println("Error decrypting master key: $e")
            e.printStackTrace()
            onFailure()
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun generateMasterKey(cipher: Cipher): EncryptionOutput? {
        val sharedPreferences = context.getSharedPreferences("VaultPrefs", Context.MODE_PRIVATE)
        if (sharedPreferences.contains("encryptedMasterKey")) {
            // Master key already exists
            return null
        }

        // Generate a new Libsodium master key
        val masterKey = LibsodiumRandom.buf(32)
        require(masterKey.size == 32) { "Master key must be 32 bytes" }

        // Encrypt the master key using Android Keystore
        val secretKey = generateKeystoreKey()
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val byteArrMasterKey = masterKey.toByteArray()
        require(byteArrMasterKey.size == 32) { "Byte Array Master key must be 32 bytes" }

        val iv = cipher.iv.copyOf()
        val encryptedMasterKey = cipher.doFinal(byteArrMasterKey)
        val ciphertext = encryptedMasterKey.copyOfRange(0, encryptedMasterKey.size - TAG_LENGTH)
        val tag = encryptedMasterKey.copyOfRange(encryptedMasterKey.size - TAG_LENGTH, encryptedMasterKey.size)

        // Validate encryption results
        require(encryptedMasterKey.isNotEmpty()) { "Encrypted master key is empty" }
        require(iv.size == 12) { "IV size must be 12 bytes for AES-GCM" }

        // Persist the encrypted master key and IV
        sharedPreferences.edit()
            .putString("encryptedMasterKey", encryptedMasterKey.joinToString(",") { it.toString() })
            .putString("cipherText", ciphertext.joinToString(",") { it.toString() })
            .putString("tag", tag.joinToString(",") { it.toString() })
            .putString("iv", iv.joinToString(",") { it.toString() })
            .apply()

        return EncryptionOutput(encryptedMasterKey, iv, tag, ciphertext)
    }

    // Generates a keystore key in the Android Keystore.
    private fun generateKeystoreKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                keyStoreAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true) // Enforces user authentication
                .setInvalidatedByBiometricEnrollment(false) // Prevents invalidation when biometrics change
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun getKeystoreKey(): SecretKey? {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return keyStore.getKey(keyStoreAlias, null) as? SecretKey
    }

    // Encrypts data using Libsodium and a key fetched from Keystore.
    @OptIn(ExperimentalUnsignedTypes::class)
    fun encryptData(data: UByteArray, masterKey: ByteArray): Pair<UByteArray, UByteArray> {
        val nonce = LibsodiumRandom.buf(crypto_secretbox_NONCEBYTES)
        val encryptedData = SecretBox.easy(data, nonce, masterKey.toUByteArray())
        return Pair(encryptedData, nonce) // Save nonce with encrypted data
    }

    // Decrypts data using Libsodium and a key fetched from Keystore.
    @OptIn(ExperimentalUnsignedTypes::class)
    fun decryptData(encryptedData: UByteArray, nonce: UByteArray, masterKey: ByteArray): UByteArray {
        return SecretBox.openEasy(encryptedData, nonce, masterKey.toUByteArray())
    }
}
