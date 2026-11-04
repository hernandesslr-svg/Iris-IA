package com.iris.assistant

import java.text.Normalizer
import java.util.Locale

/** Classificador de intencao adaptado de iris-intent.ts do projeto original. */
object IrisIntent {
    enum class Kind { CHAT, SEARCH }
    private fun norm(s:String)=Normalizer.normalize(s.lowercase(Locale.ROOT),Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"),"").replace(Regex("[^a-z0-9 ]")," ").replace(Regex("\\s+")," ").trim()
    fun classify(input:String):Kind {
        val n=norm(input).replace(Regex("^(?:iris|oi|ola|me diz|me fala|por favor)\\s+"),"")
        if(Regex("^(pesquisa|pesquise|procura|procure|busca|busque)\\b").containsMatchIn(n) ||
            Regex("\\b(na internet|na web|na rede)\\b").containsMatchIn(n)) return Kind.SEARCH
        var search=0
        var chat=0
        if(Regex("^(o que e|quem foi|quem e|quando foi|onde fica|quantos|quantas|quanto custa|como funciona|como fazer|qual e)\\b").containsMatchIn(n)) search+=4
        if(Regex("\\b(noticia|noticias|cotacao|preco|dolar|bitcoin|populacao|presidente|fundador|significado|historia)\\b").containsMatchIn(n)) search+=2
        if(Regex("\\b(eu|meu|minha|comigo|estou|sinto|fiquei|voce|sua|seu)\\b").containsMatchIn(n)) chat+=3
        if(Regex("\\b(triste|feliz|ansioso|ansiosa|cansado|cansada|nervoso|nervosa|saudade|medo|preocupado|preocupada)\\b").containsMatchIn(n)) chat+=4
        if(Regex("\\b(conversar|conversa|desabafar|piada|companhia|opinioes|opiniao)\\b").containsMatchIn(n)) chat+=5
        if(n.split(" ").size<=3 && search==0) chat+=2
        return if(search>=3 && search>chat) Kind.SEARCH else Kind.CHAT
    }
    fun mood(input:String):String {
        val n=norm(input)
        val neg=Regex("\\b(triste|ruim|horrivel|cansado|cansada|ansioso|ansiosa|estressado|estressada|perdi|problema|doente|dificil|sozinho|sozinha)\\b").containsMatchIn(n)
        val pos=Regex("\\b(feliz|bom|boa|otimo|otima|legal|maravilhoso|maravilhosa|ganhei|consegui|adorei|amei|incrivel)\\b").containsMatchIn(n)
        return when { neg && !pos -> "neg"; pos && !neg -> "pos"; else -> "neu" }
    }
}
