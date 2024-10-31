package com.example.vault

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class VaultDBHandler(context : Context?, name : String?, factory : SQLiteDatabase.CursorFactory?, version : Int) : SQLiteOpenHelper(context, name, factory, version) {

    override fun onCreate(db: SQLiteDatabase?) {
        db!!.execSQL("CREATE TABLE IF NOT EXISTS Vaults (ID INTEGER PRIMARY KEY AUTOINCREMENT, TITLE TEXT, CONTENT TEXT, COLOUR INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase?, v1: Int, v2: Int) {
        TODO("Not yet implemented")
    }

    fun addVault(vault: Vault) {
        val sqLiteDatabase = this.writableDatabase
        val contentValues = ContentValues()
        contentValues.put("TITLE", vault.getTitle())
        contentValues.put("CONTENT", vault.getContent())

        sqLiteDatabase.insert("Vaults", null, contentValues)
    }

    fun getVaults(): Array<Vault> {
        return getVaultsByQuery("SELECT * FROM Vaults", null)
    }

    fun searchVaults(searchQuery: String): Array<Vault> {
        return getVaultsByQuery("SELECT * FROM Vaults WHERE Vaults.TITLE LIKE '%$searchQuery%';", null)
    }

    private fun getVaultsByQuery(query: String, args: Array<String>?): Array<Vault> {
        val sqLiteDatabase = this.readableDatabase
        val output: MutableList<Vault> = ArrayList<Vault>()

        val results = sqLiteDatabase.rawQuery(query, args)

        val iID = results.getColumnIndex("ID")
        val iTITLE = results.getColumnIndex("TITLE")
        val iCONTENT = results.getColumnIndex("CONTENT")

        var currentID: Int
        var currentTitle: String?
        var currentContent: String?

        while (results.moveToNext()) {
            currentID = results.getInt(iID)
            currentTitle = results.getString(iTITLE)
            currentContent = results.getString(iCONTENT)

            output.add(Vault(currentID, currentTitle, currentContent))
        }

        results.close()
        return output.toTypedArray<Vault>()
    }

}