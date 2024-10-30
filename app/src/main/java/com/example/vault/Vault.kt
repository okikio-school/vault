package com.example.vault

class Vault (private var id: Int, private var title: String, private var content: String) {

    //secondary constructor (no id)
    constructor (title: String, content: String) : this (-1, title, content)

    fun getId(): Int {
        return id
    }

    fun getTitle(): String {
        return title
    }

    fun getContent(): String {
        return content
    }

}