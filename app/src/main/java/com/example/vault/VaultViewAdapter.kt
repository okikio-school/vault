package com.example.vault

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class VaultViewAdapter(private val context: Context, private var vaultList : List<Vault>) : RecyclerView.Adapter<VaultViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VaultViewHolder {
        return VaultViewHolder(
            LayoutInflater.from(context).inflate(R.layout.vault_thumbnail_view, parent, false)
        )
    }

    override fun getItemCount(): Int {
        return vaultList.size
    }

    override fun onBindViewHolder(holder: VaultViewHolder, position: Int) {
        val vault : Vault = vaultList[position]

        holder.titleOutput.text = vault.getTitle()
        holder.contentOutput.text = vault.getContent()
    }

    @SuppressLint("NotifyDataSetChanged") //entire dataset can change
    fun updateVaultList(newList: List<Vault>) {
        vaultList = newList
        notifyDataSetChanged()
    }
}