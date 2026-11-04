package com.iris.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** Memoria local adaptada de iris-memory.ts; sem rede e sem gravar conversa inteira. */
class IrisMemory(context: Context) {
    private val prefs=context.getSharedPreferences("iris_lessons",Context.MODE_PRIVATE)
    private val stop=setOf("que","como","qual","para","pra","com","uma","nao","voce","seu","sua","isso","essa","esse","quero","pode","sobre","mais","aqui","iris")
    private fun norm(s:String)=Normalizer.normalize(s.lowercase(Locale.ROOT),Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"),"").replace(Regex("[^a-z0-9 ]")," ").replace(Regex("\\s+")," ").trim()
    private fun tokens(s:String)=norm(s).split(" ").filter { it.length>=4 && it !in stop }.distinct().take(8)
    private fun lessons():JSONArray=try { JSONArray(prefs.getString("lessons","[]")) } catch (_:Exception) { JSONArray() }
    fun teach(input:String):String? {
        val match=Regex("^(?:lembra que|guarda que|aprende que)\\s+(.+)",RegexOption.IGNORE_CASE).find(input.trim()) ?: return null
        val fact=match.groupValues[1].trim().take(420)
        if(fact.length<8) return "Me diga o que você quer que eu guarde."
        val key=tokens(fact)
        if(key.isEmpty()) return "Não consegui identificar o assunto."
        val previous=lessons()
        val next=JSONArray()
        next.put(JSONObject().put("topic",fact).put("say",fact).put("keys",JSONArray(key)))
        for(i in 0 until minOf(previous.length(),59)) {
            val item=previous.optJSONObject(i) ?: continue
            if(norm(item.optString("topic"))!=norm(fact)) next.put(item)
        }
        prefs.edit().putString("lessons",next.toString()).apply()
        return "Guardei isso na minha memória."
    }
    fun recall(input:String):String? {
        if(!Regex("^(?:voce lembra|você lembra|lembra do que|o que eu te falei|o que eu te disse)",RegexOption.IGNORE_CASE).containsMatchIn(input.trim())) return null
        val query=tokens(input)
        var best:JSONObject?=null
        var score=0
        val data=lessons()
        for(i in 0 until data.length()) {
            val item=data.optJSONObject(i) ?: continue
            val keys=item.optJSONArray("keys") ?: continue
            var current=0
            for(j in 0 until keys.length()) if(keys.optString(j) in query) current++
            if(current>score) { score=current; best=item }
        }
        return if(score>=1) "Eu lembro que você me disse: ${best?.optString("say")}."
            else "Ainda não encontrei isso na minha memória."
    }
}
