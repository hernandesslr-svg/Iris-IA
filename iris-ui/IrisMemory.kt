package com.iris.assistant

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import java.text.Normalizer
import java.util.Locale

/**
 * Memoria expansivel e offline. SQLite indexado por palavras-chave.
 * Migra os ensinamentos do formato anterior sem perder registros.
 */
class IrisMemory(context: Context) {
    private val prefs=context.getSharedPreferences("iris_lessons",Context.MODE_PRIVATE)
    private val db=object:SQLiteOpenHelper(context,"iris_memory.db",null,1) {
        override fun onCreate(d:SQLiteDatabase) {
            d.execSQL("CREATE TABLE lessons (id INTEGER PRIMARY KEY AUTOINCREMENT, topic TEXT NOT NULL UNIQUE, say TEXT NOT NULL, created INTEGER NOT NULL)")
            d.execSQL("CREATE TABLE lesson_keys (lesson_id INTEGER NOT NULL, word TEXT NOT NULL, PRIMARY KEY(lesson_id,word))")
            d.execSQL("CREATE INDEX idx_lesson_word ON lesson_keys(word)")
        }
        override fun onUpgrade(d:SQLiteDatabase,oldVersion:Int,newVersion:Int) {}
    }.writableDatabase
    private val stop=setOf("que","como","qual","para","pra","com","uma","nao","voce","seu","sua","isso","essa","esse","quero","pode","sobre","mais","aqui","iris","lembra","falei","disse")
    private fun norm(s:String)=Normalizer.normalize(s.lowercase(Locale.ROOT),Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"),"").replace(Regex("[^a-z0-9 ]")," ").replace(Regex("\\s+")," ").trim()
    private fun tokens(s:String)=norm(s).split(" ").filter { it.length>=4 && it !in stop }.distinct().take(24)
    init { migrate() }
    private fun migrate() {
        val old=prefs.getString("lessons",null) ?: return
        val array=try { JSONArray(old) } catch (_:Exception) { return }
        db.beginTransaction()
        try {
            for(i in 0 until array.length()) {
                val item=array.optJSONObject(i) ?: continue
                val fact=item.optString("say").take(2048)
                if(fact.isNotBlank()) insert(fact)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        prefs.edit().remove("lessons").apply()
    }
    private fun insert(fact:String) {
        val topic=norm(fact).take(500)
        if(topic.isBlank()) return
        val values=ContentValues().apply {
            put("topic",topic); put("say",fact); put("created",System.currentTimeMillis())
        }
        db.insertWithOnConflict("lessons",null,values,SQLiteDatabase.CONFLICT_REPLACE)
        db.rawQuery("SELECT id FROM lessons WHERE topic=?",arrayOf(topic)).use { cursor ->
            if(!cursor.moveToFirst()) return
            val id=cursor.getLong(0)
            db.delete("lesson_keys","lesson_id=?",arrayOf(id.toString()))
            for(word in tokens(fact)) {
                val v=ContentValues().apply { put("lesson_id",id); put("word",word) }
                db.insertWithOnConflict("lesson_keys",null,v,SQLiteDatabase.CONFLICT_IGNORE)
            }
        }
    }
    @Synchronized fun teach(input:String):String? {
        val match=Regex("^(?:lembra que|guarda que|aprende que)\\s+(.+)",RegexOption.IGNORE_CASE).find(input.trim()) ?: return null
        val fact=match.groupValues[1].trim().take(2048)
        if(fact.length<8) return "Me diga o que você quer que eu guarde."
        if(tokens(fact).isEmpty()) return "Não consegui identificar o assunto."
        db.beginTransaction()
        try { insert(fact); db.setTransactionSuccessful() } finally { db.endTransaction() }
        return "Guardei isso na minha memória."
    }
    @Synchronized fun recall(input:String):String? {
        if(!Regex("^(?:voce lembra|você lembra|lembra do que|o que eu te falei|o que eu te disse)",RegexOption.IGNORE_CASE).containsMatchIn(input.trim())) return null
        val words=tokens(input)
        if(words.isEmpty()) return "Me diga o assunto que você quer lembrar."
        val placeholders=words.joinToString(",") { "?" }
        val sql="SELECT l.say, COUNT(*) AS score FROM lesson_keys k JOIN lessons l ON l.id=k.lesson_id WHERE k.word IN ($placeholders) GROUP BY l.id ORDER BY score DESC, l.created DESC LIMIT 1"
        db.rawQuery(sql,words.toTypedArray()).use { cursor ->
            if(cursor.moveToFirst()) return "Eu lembro que você me disse: ${cursor.getString(0)}."
        }
        return "Ainda não encontrei isso na minha memória."
    }
}
