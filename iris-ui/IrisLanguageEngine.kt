package com.iris.assistant

import android.content.Context
import org.json.JSONObject

/**
 * Ponto de integracao do futuro modelo IRIS treinado do zero.
 * Nao declara pesos treinados nem executa inferencia ficticia.
 */
interface IrisLanguageEngine {
    val ready: Boolean
    fun generate(prompt: String): String?
    fun close()
}

object IrisLanguageEngineFactory {
    private const val MANIFEST = "iris-neural/model.json"

    /** Validacao basica de pacote; um manifesto nao equivale a um runtime funcional. */
    fun inspectPackage(context: Context): String {
        return try {
            val manifest = context.assets.open(MANIFEST).bufferedReader().use { JSONObject(it.readText()) }
            val format = manifest.optString("format")
            val tokenizer = manifest.optString("tokenizer")
            val weights = manifest.optString("weights")
            require(format == "iris-neural-v1") { "Formato de modelo nao suportado" }
            require(tokenizer.isNotBlank() && weights.isNotBlank()) { "Arquivos nao declarados" }
            require(!tokenizer.contains("..") && !weights.contains("..") && !tokenizer.startsWith("/") && !weights.startsWith("/")) { "Caminho invalido" }
            context.assets.open("iris-neural/$tokenizer").use { require(it.read() != -1) { "Tokenizer vazio" } }
            context.assets.open("iris-neural/$weights").use { require(it.read() != -1) { "Pesos vazios" } }
            "Pacote encontrado; inferencia ainda requer implementacao do runtime"
        } catch (_: Exception) {
            "Modelo neural proprio ainda nao instalado"
        }
    }

    fun create(context: Context): IrisLanguageEngine = UnavailableEngine

    private object UnavailableEngine : IrisLanguageEngine {
        override val ready = false
        override fun generate(prompt: String): String? = null
        override fun close() {}
    }
}
