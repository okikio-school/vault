package com.example.vault

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.secretbox.SecretBox
import com.ionspin.kotlin.crypto.secretbox.crypto_secretbox_NONCEBYTES
import com.ionspin.kotlin.crypto.util.LibsodiumRandom
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureKeyVault(private val context: Context, private val activity: Fragment) {

    companion object {
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val VAULT_KEYSTORE_ALIAS = "VaultKeyStoreAlias"
        private const val VAULT_PREFS_ACCESS_KEY = "VaultSharedPrefs"
        private const val ENCRYPTED_MASTER_KEY_ACCESS_KEY = "EncryptedMasterKey"
        private const val INITIALIZATION_VECTOR_ACCESS_KEY = "InitializationVector"
        private val MASTER_KEY_ALIAS = MasterKeys.AES256_GCM_SPEC

        private const val ENCRYPTION_BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val ENCRYPTION_PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val ENCRYPTION_ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val ENCRYPTION_KEY_SIZE = 256
    }

    // Initialize Libsodium
    fun init(callback: () -> Unit) {
        if (!LibsodiumInitializer.isInitialized()) {
            LibsodiumInitializer.initializeWithCallback(callback)
        } else {
            callback()
        }
    }

    // Create EncryptedSharedPreferences instance
    private fun getEncryptedPrefs(): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MASTER_KEY_ALIAS)
        return EncryptedSharedPreferences.create(
            VAULT_PREFS_ACCESS_KEY,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Retrieve or generate a master key
    fun authenticate(
        onSuccess: (ByteArray) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val encryptedPrefs = getEncryptedPrefs()

        // Check if master key exists
        val encryptedMasterKeyBase64 = encryptedPrefs.getString(ENCRYPTED_MASTER_KEY_ACCESS_KEY, null)
        val masterKeyInitializationVectorBase64 = encryptedPrefs.getString(INITIALIZATION_VECTOR_ACCESS_KEY, null)
        if (encryptedMasterKeyBase64 != null && masterKeyInitializationVectorBase64 != null) {
            // Decrypt and return the existing master key
            val encryptedMasterKey = Base64.decode(encryptedMasterKeyBase64, Base64.DEFAULT)
            val iv = Base64.decode(masterKeyInitializationVectorBase64, Base64.DEFAULT)
            accessMasterKeyUsingBiometrics(encryptedMasterKey, iv, onSuccess, onFailure)
        } else {
            // Generate and store a new master key
            generateMasterKeyUsingBiometrics(encryptedPrefs, onSuccess, onFailure)
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun generateMasterKeyUsingBiometrics(
        encryptedPrefs: SharedPreferences,
        onSuccess: (ByteArray) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                try {
                    val cipher = result.cryptoObject?.cipher
                        ?: throw IllegalStateException("CryptoObject Cipher is null")

                    // Generate a 32-byte master key using LibSodium
                    val masterKey = LibsodiumRandom.buf(32).toByteArray()
                    require(masterKey.size == 32) { "Master key must be 32 bytes" }

                    // Encrypt the master key using the Keystore
                    val encryptedMasterKey = cipher.doFinal(masterKey)
                    val iv = cipher.iv

                    val encryptedMasterKeyBase64 = Base64.encodeToString(encryptedMasterKey, Base64.DEFAULT)
                    val ivBase64 = Base64.encodeToString(iv, Base64.DEFAULT)

                    // Store the encrypted master key
                    encryptedPrefs.edit().run {
                        putString(ENCRYPTED_MASTER_KEY_ACCESS_KEY, encryptedMasterKeyBase64)
                        putString(INITIALIZATION_VECTOR_ACCESS_KEY, ivBase64)
                    }.apply()
                    onSuccess(masterKey)
                } catch (e: Exception) {
                    onFailure("Failed to generate and store master key: ${e.message}")
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFailure("Authentication error: $errString")
            }

            override fun onAuthenticationFailed() {
                onFailure("Authentication failed")
            }
        })

        // Get a cipher for encryption
        val cipher = getCipher()
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreKey())

        // Attach the CryptoObject to the BiometricPrompt
        val cryptoObject = BiometricPrompt.CryptoObject(cipher)

        // Lets the user authenticate using either a Class 3 biometric or
        // their lock screen credential (PIN, pattern, or password).
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authenticate to Generate Master Key")
            .setSubtitle("Use your biometric credential")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo, cryptoObject)
    }

    private fun accessMasterKeyUsingBiometrics(
        encryptedMasterKey: ByteArray,
        iv: ByteArray,
        onSuccess: (ByteArray) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                try {
                    val cipher = result.cryptoObject?.cipher
                        ?: throw IllegalStateException("CryptoObject Cipher is null")

                    val masterKey = cipher.doFinal(encryptedMasterKey)
                    onSuccess(masterKey)
                } catch (e: Exception) {
                    onFailure("Decryption failed: ${e.message}")
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFailure("Authentication error: $errString")
            }

            override fun onAuthenticationFailed() {
                onFailure("Authentication failed")
            }
        })

        // Get a cipher for decryption
        val cipher = getCipher()

        // Attach the CryptoObject to the BiometricPrompt
        val cryptoObject = BiometricPrompt.CryptoObject(cipher)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKeystoreKey(), GCMParameterSpec(128, iv))

        // Lets the user authenticate using either a Class 3 biometric or
        // their lock screen credential (PIN, pattern, or password).
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authenticate to Access Master Key")
            .setSubtitle("Use your biometric credential")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo, cryptoObject)
    }

    // Get a cipher for encryption and/or decryption
    private fun getCipher(): Cipher {
        val transformation = "$ENCRYPTION_ALGORITHM/$ENCRYPTION_BLOCK_MODE/$ENCRYPTION_PADDING"
        return Cipher.getInstance(transformation)
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
        return keyStore.getKey(VAULT_KEYSTORE_ALIAS, null) as? SecretKey
            ?: createKeystoreKey()
    }

    private fun createKeystoreKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(ENCRYPTION_ALGORITHM, ANDROID_KEYSTORE_PROVIDER)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                VAULT_KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(ENCRYPTION_BLOCK_MODE)
                .setEncryptionPaddings(ENCRYPTION_PADDING)
                .setKeySize(ENCRYPTION_KEY_SIZE)
                .setUserAuthenticationRequired(true) // Enforces user authentication
                .setInvalidatedByBiometricEnrollment(false) // Prevents invalidation when biometrics change
                .build()
        )

        return keyGenerator.generateKey()
    }

    // Encrypts data using Libsodium and the master key fetched from EncryptedSharedPreferences.
    @OptIn(ExperimentalUnsignedTypes::class)
    fun encryptData(data: UByteArray, masterKey: ByteArray): Pair<UByteArray, UByteArray> {
        val nonce = LibsodiumRandom.buf(crypto_secretbox_NONCEBYTES)
        val encryptedData = SecretBox.easy(data, nonce, masterKey.toUByteArray())
        return Pair(encryptedData, nonce) // Save nonce with encrypted data
    }

    // Decrypts data using Libsodium and the master key fetched from EncryptedSharedPreferences.
    @OptIn(ExperimentalUnsignedTypes::class)
    fun decryptData(encryptedData: UByteArray, nonce: UByteArray, masterKey: ByteArray): UByteArray {
        return SecretBox.openEasy(encryptedData, nonce, masterKey.toUByteArray())
    }
}
