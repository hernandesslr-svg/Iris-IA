package com.iris.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.pow
import kotlin.random.Random

/** Gerador de trigramas original Iris, lendo os bancos treinados do APK. */
class IrisTrigramGenerator(context: Context) {
    private val gen:JSONObject?=try {
        context.assets.open("iris-lm-weights.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONObject("gen")
        }
    } catch (_:Exception) { null }
    val available:Boolean get()=gen!=null
    private fun row(keys:List<Pair<String,Int>>,a:Int,b:Int):List<Pair<Int,Double>> {
        val tables=gen?.optJSONObject("tables") ?: return emptyList()
        for(order in listOf("o3","o2")) {
            val counts=sortedMapOf<Int,Double>()
            for((key,weight) in keys) {
                val table=tables.optJSONObject(key) ?: continue
                val context=if(order=="o3") "$a,$b" else "$b"
                val data=table.optJSONObject(order)?.optJSONArray(context) ?: continue
                var i=0
                while(i+1<data.length()) {
                    val next=data.optInt(i)
                    counts[next]=(counts[next] ?: 0.0)+data.optDouble(i+1)*weight
                    i+=2
                }
            }
            if(counts.isNotEmpty()) return counts.map { it.key to it.value }
        }
        return emptyList()
    }
    fun generate(keys:List<Pair<String,Int>>,seed:Int,temperature:Double=1.0,maxTokens:Int=34):String? {
        val vocabulary=gen?.optJSONArray("vocab") ?: return null
        val random=Random(seed)
        val words=mutableListOf<String>()
        var a=0
        var b=0
        repeat(maxTokens) {
            val options=row(keys,a,b)
            if(options.isEmpty()) return null
            val total=options.sumOf { it.second.pow(1.0/temperature) }
            var pick=random.nextDouble()*total
            var next=options.last().first
            for((id,count) in options) {
                pick-=count.pow(1.0/temperature)
                if(pick<=0) { next=id; break }
            }
            if(next==1) return words.joinToString(" ").replace(Regex(" +([.,!?;:…])"),"$1").takeIf { it.isNotBlank() }
            words.add(vocabulary.optString(next))
            a=b
            b=next
        }
        return words.joinToString(" ").replace(Regex(" +([.,!?;:…])"),"$1").takeIf { it.isNotBlank() }
    }
}
