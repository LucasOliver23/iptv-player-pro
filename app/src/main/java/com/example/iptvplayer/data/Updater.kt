package com.example.iptvplayer.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Atualização do app pelo GitHub (Releases).
 * O app consulta a última versão publicada em [REPO] e, se for mais nova, baixa o APK e abre o instalador.
 */
object Updater {
    /**
     * Repositório PÚBLICO onde as versões (APK) são publicadas: "usuario/repositorio".
     * Troque pelo nome do seu repositório de releases (veja ATUALIZACAO_GITHUB.md).
     */
    const val REPO = "LucasOliver23/iptv-player-pro-releases"

    class Info(val version: String, val notes: String, val apkUrl: String)

    /** Versão instalada (ex.: "1.0"). */
    fun currentVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }

    /** Consulta a última versão. Retorna null se não houver nenhuma mais nova. */
    suspend fun check(context: Context): Info? = withContext(Dispatchers.IO) {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "IPTVPlayerPro")
            val code = conn.responseCode
            if (code == 404) return@withContext null // ainda não há versão publicada
            if (code != 200) throw IOException("GitHub respondeu HTTP $code")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name").trim()
            val version = tag.removePrefix("v").removePrefix("V")
            val assets = json.optJSONArray("assets")
            var apkUrl = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.optString("browser_download_url")
                        break
                    }
                }
            }
            if (version.isBlank() || apkUrl.isBlank()) return@withContext null
            if (!isNewer(version, currentVersion(context))) return@withContext null
            Info(version, json.optString("body").trim(), apkUrl)
        } finally {
            conn.disconnect()
        }
    }

    /** Compara versões como 1.10.2 > 1.9. */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.split('.', '-', '+').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** Baixa o APK (com progresso de 0 a 1) e devolve o arquivo. */
    suspend fun download(context: Context, info: Info, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "IPTVPlayerPro-${info.version}.apk")
        val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "IPTVPlayerPro")
            if (conn.responseCode != 200) throw IOException("Download falhou (HTTP ${conn.responseCode})")
            val total = conn.contentLengthLong
            var done = 0L
            conn.inputStream.use { input ->
                FileOutputStream(file).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            file
        } finally {
            conn.disconnect()
        }
    }

    /** false = o Android ainda não permitiu instalar apps desta origem (abre a tela para permitir). */
    fun ensureInstallPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return false
        }
        return true
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
