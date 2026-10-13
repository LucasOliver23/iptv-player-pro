package com.example.iptvplayer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * Preferências do app.
 * - servidor/usuário/formato: SharedPreferences normal
 * - senha: EncryptedSharedPreferences (com fallback se o Keystore do aparelho falhar)
 * - favoritos e "continuar assistindo": guardados só por IDs (nunca URLs com senha)
 */
class Store(context: Context) {

    private val plain: SharedPreferences =
        context.getSharedPreferences("iptv_pro", Context.MODE_PRIVATE)
    private val secure: SharedPreferences = openSecure(context)

    init {
        // Migra a senha que versões antigas guardavam em texto puro.
        val legacy = plain.getString("password", null)
        if (legacy != null) {
            if (secure.getString("password", null) == null) {
                secure.edit().putString("password", legacy).apply()
            }
            plain.edit().remove("password").apply()
        }
    }

    var server: String
        get() = plain.getString("server", "") ?: ""
        set(value) { plain.edit().putString("server", value).apply() }

    var user: String
        get() = plain.getString("user", "") ?: ""
        set(value) { plain.edit().putString("user", value).apply() }

    var password: String
        get() = secure.getString("password", "") ?: ""
        set(value) { secure.edit().putString("password", value).apply() }

    /** "ts" (padrão) ou "m3u8". */
    var liveFormat: String
        get() = plain.getString("live_format", "ts") ?: "ts"
        set(value) { plain.edit().putString("live_format", value).apply() }

    /** Tamanho do vídeo: 0 = ajustar, 1 = preencher (zoom), 2 = esticar. */
    var videoScale: Int
        get() = plain.getInt("video_scale", 0)
        set(value) { plain.edit().putInt("video_scale", value).apply() }

    val hasCredentials: Boolean
        get() = server.isNotBlank() && user.isNotBlank() && password.isNotBlank()

    companion object {
        /**
         * Servidores do seu painel, tentados em ordem (o primeiro que responder é usado).
         * Preenchidos = a tela de login pede só usuário e senha.
         * Deixe a lista vazia para voltar a pedir o endereço na tela de login.
         */
        val DEFAULT_SERVERS: List<String> = listOf(
            "http://cdntp.sbs",
            "http://tlfp.fun:80",
            "http://telefunplay.xyz:80",
        )
    }

    fun saveLogin(server: String, user: String, password: String) {
        this.server = server
        this.user = user
        this.password = password
    }

    fun clearPassword() {
        secure.edit().remove("password").apply()
    }

    // ---------- Favoritos ("Minha lista") ----------

    var favorites: Set<String>
        get() = HashSet(plain.getStringSet("favorites", emptySet()) ?: emptySet())
        set(value) { plain.edit().putStringSet("favorites", HashSet(value)).apply() }

    // ---------- Continuar assistindo ----------

    fun loadResume(): List<ResumeItem> {
        val raw = plain.getString("resume", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<ResumeItem>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val request = PlayRequest(
                    key = o.getString("key"),
                    kind = PlayKind.valueOf(o.getString("kind")),
                    refId = o.getString("refId"),
                    extension = o.optString("extension", "mp4"),
                    title = o.optString("title"),
                    subtitle = o.str("subtitle"),
                    image = o.str("image"),
                    seriesId = o.str("seriesId"),
                )
                out.add(
                    ResumeItem(
                        request = request,
                        positionMs = o.optLong("positionMs"),
                        durationMs = o.optLong("durationMs"),
                        updatedAt = o.optLong("updatedAt"),
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveResume(items: List<ResumeItem>) {
        val arr = JSONArray()
        for (item in items) {
            val r = item.request
            arr.put(
                JSONObject()
                    .put("key", r.key)
                    .put("kind", r.kind.name)
                    .put("refId", r.refId)
                    .put("extension", r.extension)
                    .put("title", r.title)
                    .put("subtitle", r.subtitle ?: JSONObject.NULL)
                    .put("image", r.image ?: JSONObject.NULL)
                    .put("seriesId", r.seriesId ?: JSONObject.NULL)
                    .put("positionMs", item.positionMs)
                    .put("durationMs", item.durationMs)
                    .put("updatedAt", item.updatedAt)
            )
        }
        plain.edit().putString("resume", arr.toString()).apply()
    }

    private companion object {
        fun openSecure(context: Context): SharedPreferences = try {
            val key = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "iptv_pro_secure",
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            // Alguns aparelhos/boxes têm Keystore quebrado: melhor funcionar do que travar.
            context.getSharedPreferences("iptv_pro_fallback", Context.MODE_PRIVATE)
        }
    }
}
