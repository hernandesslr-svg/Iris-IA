package com.iris.assistant

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings

/** Executa somente ações solicitadas explicitamente pelo usuário. */
object IrisDeviceControl {
    private val apps = mapOf(
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "tiktok" to "com.zhiliaoapp.musically",
        "youtube" to "com.google.android.youtube",
        "facebook" to "com.facebook.katana",
        "telegram" to "org.telegram.messenger",
        "spotify" to "com.spotify.music",
        "chrome" to "com.android.chrome",
        "camera" to "camera",
        "galeria" to "gallery",
        "fotos" to "gallery",
        "configuracoes" to "settings"
    )
    private fun normalize(text:String):String =
        java.text.Normalizer.normalize(text.lowercase(java.util.Locale.ROOT),java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"),"")
    private fun openInstalledByLabel(context:Context,target:String):String {
        val pm=context.packageManager
        val apps=pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
        val app=apps.firstOrNull { normalize(it.loadLabel(pm).toString()) == target }
            ?: apps.firstOrNull { normalize(it.loadLabel(pm).toString()).contains(target) }
            ?: return "Não encontrei $target instalado."
        val intent=pm.getLaunchIntentForPackage(app.activityInfo.packageName)
            ?: return "Não consegui abrir $target."
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { context.startActivity(intent); "Abrindo $target." }
            catch (_:Exception) { "O Android bloqueou a abertura de $target." }
    }
    fun tryExecute(context:Context, phrase:String):String? {
        val input = normalize(phrase).trim()
        val match = Regex("^(?:por favor[, ]*)?(?:abra|abre|abrir|inicie|iniciar|mostre|mostrar)\\s+(?:o |a |os |as )?(.+)$").find(input) ?: return null
        val target = match.groupValues[1].trim().removeSuffix(".")
        val key = apps.keys.sortedByDescending { it.length }.firstOrNull {
            target == it || target.startsWith("$it ") || target.startsWith("$it,")
        } ?: return openInstalledByLabel(context,target)
        val packageName = apps.getValue(key)
        val intent = when (packageName) {
            "gallery" -> Intent(Intent.ACTION_VIEW,MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply { type="image/*" }
            "camera" -> Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            "settings" -> Intent(Settings.ACTION_SETTINGS)
            else -> context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return "Não encontrei $key instalado ou disponível para abrir."
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            "Abrindo $key."
        } catch (_: ActivityNotFoundException) {
            "Não encontrei um aplicativo para abrir $key."
        } catch (_: SecurityException) {
            "O Android não permitiu abrir $key."
        } catch (_: Exception) {
            "Não consegui abrir $key."
        }
    }
}
