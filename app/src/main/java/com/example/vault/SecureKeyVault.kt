package com.example.vault

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.widget.Toast
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
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

class SecureKeyVault(private val context: Context, private val activity: FragmentActivity) {

    private val keyStoreAlias = "MasterKeyAlias"

    suspend fun init() {
        // Initialize Libsodium
        if (!LibsodiumInitializer.isInitialized()) {
            LibsodiumInitializer.initialize()
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
                decryptMasterKey(context, onSuccess, onFailure)
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
        context: Context,
        onSuccess: (ByteArray) -> Unit,
        onFailure: () -> Unit
    ) {
        val sharedPreferences = context.getSharedPreferences("VaultPrefs", Context.MODE_PRIVATE)
        val encryptedMasterKey = sharedPreferences.getString("encryptedMasterKey", null)
            ?.split(",")?.map { it.toByte() }?.toByteArray()
        val iv = sharedPreferences.getString("iv", null)
            ?.split(",")?.map { it.toByte() }?.toByteArray()

        if (encryptedMasterKey == null || iv == null) {
            onFailure()
            return
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, getKeystoreKey(), GCMParameterSpec(128, iv))
        }

        onSuccess(cipher.doFinal(encryptedMasterKey))
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


    @OptIn(ExperimentalUnsignedTypes::class)
    fun generateMasterKey(context: Context): Pair<ByteArray, ByteArray>? {
        val sharedPreferences = context.getSharedPreferences("VaultPrefs", Context.MODE_PRIVATE)
        if (sharedPreferences.contains("encryptedMasterKey")) {
            // Master key already exists
            return null
        }

        // Generate a new Libsodium master key
        val masterKey = LibsodiumRandom.buf(32)

        // Encrypt the master key using Android Keystore
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, generateKeystoreKey())

        val encryptedMasterKey = cipher.doFinal(masterKey.toByteArray())
        val iv = cipher.iv

        // Persist the encrypted master key and IV
        sharedPreferences.edit()
            .putString("encryptedMasterKey", encryptedMasterKey.joinToString(",") { it.toString() })
            .putString("iv", iv.joinToString(",") { it.toString() })
            .apply()

        return Pair(encryptedMasterKey, iv)
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
