package com.iris.assistant

/**
 * Identidade da voz offline escolhida para a Iris.
 *
 * A voz 'ines' pertence ao Loudkit (loudreader/loudr-1 ou loudr-1-turbo).
 * O perfil e o checkpoint precisam ser carregados pelo runtime de sintese
 * antes de qualquer reproducao. Este arquivo NAO sintetiza audio sozinho.
 *
 * Fonte: https://github.com/loudreader/loudkit/blob/main/VOICES.md
 * Perfil Ines: CC-BY-4.0 (CML-TTS).
 */
object IrisInesVoice {
    const val provider = "loudkit"
    const val modelRepository = "loudreader/loudr-1-turbo"
    const val voiceName = "ines"
    const val languageTag = "pt-BR"
    const val profileLicense = "CC-BY-4.0"
    const val modelAssetsDirectory = "loudkit"

    /** Nao anunciar a Ines como instalada sem modelo e perfil presentes. */
    fun hasProfile(context: android.content.Context): Boolean =
        try {
            context.assets.list("$modelAssetsDirectory/voices")
                ?.any { it == "$voiceName.safetensors" } == true
        } catch (_: Exception) { false }
}
