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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import com.example.vault.databinding.FragmentFolderpickerBinding

private const val ARG_FOLDER_PATH = "folderpath"
private const val ARG_FOLDER_NAME = "folder_name"

/**
 * A simple [Fragment] subclass to pick and display folder contents.
 * Use the [FolderPickerFragment.newInstance] factory method to
 * create an instance of this fragment with specific folder details.
 */
class FolderPickerFragment : Fragment() {

    private var folder_Path: String? = null
    private var folder_Name: String? = null
    private lateinit var folderContentsTextView: TextView
    private lateinit var binding: FragmentFolderpickerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            folder_Path = it.getString(ARG_FOLDER_PATH)
            folder_Name = it.getString(ARG_FOLDER_NAME)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = FragmentFolderpickerBinding.inflate(inflater, container, false)

        // Initialize UI elements
        val folderPickerBtn: Button = binding.folderPickerButton
        folderContentsTextView = binding.folderContentsTextView

        folderPickerBtn.setOnClickListener {
            openFolderPicker()
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        folderPickerLauncher.launch(intent)
    }

    // ActivityResultLauncher to handle folder picker result
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Log.i("FolderPicker", "Directory tree: $uri")
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