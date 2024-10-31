package com.example.vault.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vault.Vault
import com.example.vault.VaultDBHandler
import com.example.vault.VaultViewAdapter
import com.example.vault.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private var _db: VaultDBHandler? = null
    private var _vaultAdapter: VaultViewAdapter? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        _db = VaultDBHandler(activity, "vaultdb", null, 1)

        _vaultAdapter = VaultViewAdapter(_db!!.getVaults())
        binding.vaultsHomeViewer.setLayoutManager(LinearLayoutManager(binding.vaultsHomeViewer.context))
        binding.vaultsHomeViewer.setAdapter(_vaultAdapter)

        val root: View = binding.root
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}