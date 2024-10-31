package com.example.vault

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

//view holder class
class VaultViewHolder(vaultView: View) : RecyclerView.ViewHolder(vaultView) {
    var titleOutput: TextView = vaultView.findViewById<TextView>(R.id.titleView)
    var contentOutput: TextView = vaultView.findViewById<TextView>(R.id.contentView)
    var rootView: View = vaultView.rootView
}