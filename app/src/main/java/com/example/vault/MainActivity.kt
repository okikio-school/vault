package com.example.vault

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vault.databinding.ActivityMainBinding
import com.example.vault.databinding.FragmentHomeBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var folderContentsTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        folderContentsTextView = TextView(this).apply { // Add TextView for displaying folder contents
            id = R.id.folderContentsTextView
            text = "Folder contents will appear here." //todo: add string to values xml
        }
        binding.container.addView(folderContentsTextView)

        // Configure the Floating Action Button (FAB) to open the folder picker
        val fab: FloatingActionButton = binding.fabOpenFolderPicker
        fab.setOnClickListener {
            openFolderPicker()
        }

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_search, R.id.navigation_profile
            )
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    // Open folder picker on FAB click
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
                Log.i("MainActivity", "Directory selected: $uri")
                displayContents(uri)
            } ?: Log.e("MainActivity", "No folder selected.")
        } else {
            Log.e("MainActivity", "Folder picker canceled.")
        }
    }

    // Display contents of the selected folder
    private fun displayContents(folderUri: Uri) {
        val documentFile = DocumentFile.fromTreeUri(this, folderUri)
        val fileNames = documentFile?.listFiles()?.joinToString("\n") { it.name ?: "Unnamed file" }

        folderContentsTextView.text = fileNames ?: "No files found in the selected folder."
        Log.i("MainActivity", "Files in directory:\n$fileNames")
    }
}