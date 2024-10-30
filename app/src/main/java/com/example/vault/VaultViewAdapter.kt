package com.example.vault

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class VaultViewAdapter : RecyclerView.Adapter<VaultViewAdapter.VaultViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VaultViewHolder {
        TODO("Not yet implemented")
    }

    override fun getItemCount(): Int {
        TODO("Not yet implemented")
    }

    override fun onBindViewHolder(holder: VaultViewHolder, position: Int) {
        TODO("Not yet implemented")
    }

    //view holder internal class
    class VaultViewHolder(vaultView: View) : RecyclerView.ViewHolder(vaultView) {
        var titleOutput: TextView = vaultView.findViewById<TextView>(R.id.titleView)
        var contentOutput: TextView = vaultView.findViewById<TextView>(R.id.contentView)
        var rootView: View = vaultView.rootView
    }
}