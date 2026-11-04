package com.iris.assistant

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ln

/** Inferencia Naive Bayes compativel com iris-lm-core.ts; usa pesos exportados do modelo original. */
class IrisStatisticalModel(context:Context) {
    private val root:JSONObject?=try {
        context.assets.open("iris-lm-weights.json").bufferedReader().use { JSONObject(it.readText()) }
    } catch (_:Exception) { null }
    data class Prediction(val label:String,val gap:Double,val known:Int,val confident:Boolean)
    val available:Boolean get()=root!=null
    val trainingStats:JSONObject? get()=root?.optJSONObject("stats")
    private fun features(input:String):Set<String> {
        val normalized=Normalizer.normalize(input.lowercase(Locale.ROOT),Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"),"")
        val tokens=normalized.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
        val result=linkedSetOf<String>()
        tokens.forEachIndexed { index,token ->
            if(token.length>=2) result.add("w:$token")
            if(index+1<tokens.size) result.add("b:${token}_${tokens[index+1]}")
            if(token.length>=4) {
                val padded="^$token$"
                for(size in 4..5) for(at in 0..padded.length-size)
                    result.add("c:${padded.substring(at,at+size)}")
            }
        }
        return result
    }
    private fun predict(section:String,input:String):Prediction? {
        val model=root?.optJSONObject(section) ?: return null
        val labels=model.getJSONArray("classes")
        val priors=model.getJSONArray("prior")
        val totals=model.getJSONArray("total")
        val sparse=model.getJSONObject("feats")
        val alpha=model.optDouble("alpha",2.0)
        val vocab=model.getInt("vocab")
        val scores=DoubleArray(labels.length()) { priors.getDouble(it) }
        var known=0
        for(feature in features(input)) {
            val row=sparse.optJSONArray(feature) ?: continue
            known++
            val counts=DoubleArray(labels.length())
            var j=0
            while(j+1<row.length()) { counts[row.getInt(j)]=row.getDouble(j+1); j+=2 }
            for(i in scores.indices) {
                val denominator=totals.getDouble(i)+alpha*vocab
                scores[i]+=ln((counts[i]+alpha)/denominator)
            }
        }
        val best=scores.indices.maxByOrNull { scores[it] } ?: return null
        val second=scores.indices.filter { it!=best }.maxOfOrNull { scores[it] } ?: Double.NEGATIVE_INFINITY
        val gap=if(known>0) (scores[best]-second)/known else 0.0
        return Prediction(labels.getString(best),gap,known,known>=3 && gap>=model.optDouble("minGap",0.0))
    }
    fun topic(input:String)=predict("topic",input)
    fun mood(input:String)=predict("mood",input)
}
