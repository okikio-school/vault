package com.example.vault

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class VaultViewAdapter(private var vaultList : Array<Vault>) : RecyclerView.Adapter<VaultViewAdapter.VaultViewHolder>() {

    //view holder class
    class VaultViewHolder(vaultView: View) : RecyclerView.ViewHolder(vaultView) {
        val titleOutput: TextView = vaultView.findViewById(R.id.titleView)
        val contentOutput: TextView = vaultView.findViewById(R.id.contentView)
        val buttonDecrypt: Button = vaultView.findViewById(R.id.buttonDecrypt)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VaultViewHolder {
        val viewRValue = R.layout.vault_thumbnail_view
        return VaultViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.vault_thumbnail_view, parent, false)
        )
    }

    override fun getItemCount(): Int {
        return vaultList.size
    }

    override fun onBindViewHolder(holder: VaultViewHolder, position: Int) {
        val vault : Vault = vaultList[position]
        holder.titleOutput.text = vault.getTitle()
        holder.contentOutput.text = vault.getContent()
        holder.buttonDecrypt.setOnClickListener {
            TODO("Decryption not implemented")
        }
    }

    @SuppressLint("NotifyDataSetChanged") //entire dataset can change
    fun updateVaultList(newList: Array<Vault>) {
        vaultList = newList
        notifyDataSetChanged()
    }

}