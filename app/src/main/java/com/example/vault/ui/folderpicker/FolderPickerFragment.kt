package com.example.vault.ui.folderpicker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.vault.SecureKeyVault
import com.example.vault.Vault
import com.example.vault.VaultDBHandler
import com.example.vault.databinding.FragmentFolderpickerBinding
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import com.ionspin.kotlin.crypto.secretbox.crypto_secretbox_NONCEBYTES

private const val ARG_FOLDER_PATH = "folderpath"
private const val ARG_FOLDER_NAME = "folder_name"

class FolderPickerFragment : Fragment() {

    private var folderPath: String? = null
    private var folderName: String? = null

    private var _db: VaultDBHandler? = null
    private var currentPath: String? = null
    private lateinit var folderContentsTextView: TextView
    private lateinit var binding: FragmentFolderpickerBinding
    private lateinit var keyVault: SecureKeyVault

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            folderPath = it.getString(ARG_FOLDER_PATH)
            folderName = it.getString(ARG_FOLDER_NAME)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = FragmentFolderpickerBinding.inflate(inflater, container, false)

        _db = VaultDBHandler(activity, "vaultdb", null, 1)

        // Initialize UI elements
        val folderPickerBtn: Button = binding.folderPickerButton
        val encryptBtn: Button = binding.encryptButton
        folderContentsTextView = binding.folderContentsTextView

        keyVault = SecureKeyVault(requireContext(), this)
        keyVault.init {}

        folderPickerBtn.setOnClickListener {
            openFolderPicker()
        }
        encryptBtn.setOnClickListener {
            val title:String = binding.editText.text.toString()
            val location = currentPath

            if (location == null) {
                Toast.makeText(activity, "You must choose a folder first!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            //todo: add encryption functionality
            keyVault.init {
                keyVault.generateMasterKey()
                keyVault.authenticateWithKey(
                    onSuccess = { masterKey ->
                        // Encrypt the folder
                        val folderToEncrypt = getFolder(location)
                        val encryptedFolder = encryptFolder(folderToEncrypt, masterKey)
                        println("Folder encrypted: ${encryptedFolder.absolutePath}")

                        // Decrypt the folder
                        val decryptedFolder = decryptFolder(encryptedFolder, masterKey)
                        println("Folder decrypted: ${decryptedFolder.absolutePath}")
                    },
                    onFailure = {
                        println("Authentication failed or master key not found")
                    }
                )
            }

            val vault = Vault(title, location!!)
            _db!!.addVault(vault)
            Toast.makeText(activity, "Vault created.", Toast.LENGTH_SHORT).show()
            this.findNavController().navigateUp()
        }

        return binding.root
    }

    /**
     * Reads the contents of a file into a byte array.
     */
    private fun readFile(file: File): ByteArray {
        return Files.readAllBytes(file.toPath())
    }

    /**
     * Writes a byte array to a file.
     */
    private fun writeFile(file: File, data: ByteArray) {
        Files.write(file.toPath(), data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    }

    /**
     * Retrieves a folder based on the provided path. If the folder doesn't exist, it can optionally be created.
     *
     * @param path The path to the folder.
     * @param createIfNotExists Whether to create the folder if it doesn't exist.
     * @return The File object representing the folder.
     * @throws IllegalArgumentException If the path is invalid or not a folder.
     */
    private fun getFolder(path: String, createIfNotExists: Boolean = false): File {
        val folder = File(path)

        // Validate the path
        if (folder.exists() && !folder.isDirectory) {
            throw IllegalArgumentException("The path exists but is not a folder: $path")
        }

        // Create the folder if it doesn't exist and createIfNotExists is true
        if (!folder.exists() && createIfNotExists) {
            if (!folder.mkdirs()) {
                throw IllegalArgumentException("Failed to create folder: $path")
            }
        }

        // Ensure the folder exists
        if (!folder.exists()) {
            throw IllegalArgumentException("The folder does not exist: $path")
        }

        return folder
    }

    /**
     * Encrypts all files in the folder and saves them with encrypted content.
     * Preserves the folder structure.
     */
    @OptIn(kotlin.ExperimentalUnsignedTypes::class)
    fun encryptFolder(folder: File, masterKey: ByteArray): File {
        val encryptedFolder = File(folder.parentFile, "${folder.name}_encrypted")
        encryptedFolder.mkdirs()

        folder.walkTopDown().forEach { file ->
            if (file.isFile) {
                val relativePath = file.relativeTo(folder).path
                val encryptedFile = File(encryptedFolder, relativePath)
                encryptedFile.parentFile?.mkdirs()

                val fileData = readFile(file)
                val (encryptedData, nonce) = keyVault.encryptData(fileData.toUByteArray(), masterKey)

                // Save nonce + encrypted content in the encrypted file
                writeFile(encryptedFile, (nonce + encryptedData).toByteArray())
            }
        }

        return encryptedFolder
    }

    /**
     * Decrypts all files in the folder and restores the original content.
     * Preserves the folder structure.
     */
    @OptIn(ExperimentalUnsignedTypes::class)
    fun decryptFolder(encryptedFolder: File, masterKey: ByteArray): File {
        val decryptedFolder = File(encryptedFolder.parentFile, "${encryptedFolder.name}_decrypted")
        decryptedFolder.mkdirs()

        encryptedFolder.walkTopDown().forEach { file ->
            if (file.isFile) {
                val relativePath = file.relativeTo(encryptedFolder).path
                val decryptedFile = File(decryptedFolder, relativePath)
                decryptedFile.parentFile?.mkdirs()

                val fileData = readFile(file)

                // Separate nonce and encrypted content
                val nonce = fileData.sliceArray(0 until crypto_secretbox_NONCEBYTES)
                val encryptedData = fileData.sliceArray(crypto_secretbox_NONCEBYTES until fileData.size)

                val decryptedData = keyVault.decryptData(encryptedData.toUByteArray(), nonce.toUByteArray(), masterKey)
                writeFile(decryptedFile, decryptedData.toByteArray())
            }
        }

        return decryptedFolder
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        folderPickerLauncher.launch(intent)
    }

    //handle folder picker result
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Log.i("FolderPicker", "Directory tree: $uri")
                currentPath = uri.path
                displayContents(uri)
            } ?: run {
                Log.e("FolderPicker", "No folder selected.")
            }
        } else {
            Log.e("FolderPicker", "Folder picker canceled.")
        }
    }

    private fun displayContents(folderUri: Uri) {
        val documentFile = DocumentFile.fromTreeUri(requireContext(), folderUri)
        val fileNames = documentFile?.listFiles()?.joinToString("\n") { it.name ?: "Unnamed file" }

        folderContentsTextView.text = fileNames ?: "No files found in the selected folder."
        Log.i("FolderPicker", "Files in directory:\n$fileNames")
    }

    companion object {
        /**
         * Factory method to create a new instance of
         * this fragment using the provided folder path and name.
         *
         * @param folderPath Path of the folder.
         * @param folderName Name of the folder.
         * @return A new instance of fragment Folderpicker.
         */
        @JvmStatic
        fun newInstance(folderPath: String, folderName: String) =
            FolderPickerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FOLDER_PATH, folderPath)
                    putString(ARG_FOLDER_NAME, folderName)
                }
            }
    }
}