package com.example.iptvplayer.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.iptvplayer.MainActivity
import com.example.iptvplayer.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lembretes de programas do guia (EPG). Avisa com uma notificação alguns minutos antes
 * do programa começar. Ficam salvos no aparelho e são reagendados quando ele reinicia.
 */
object Reminders {

    private const val PREFS = "iptv_reminders"
    private const val CHANNEL = "reminders"

    /** Avisa 2 minutos antes de começar. */
    const val LEAD_MS = 2 * 60_000L

    class Item(val id: String, val channel: String, val title: String, val startMs: Long)

    fun idFor(streamKey: String, startMs: Long) = "$streamKey:$startMs"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(c: Context): List<Item> {
        val raw = prefs(c).getString("items", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val now = System.currentTimeMillis()
            val out = ArrayList<Item>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val item = Item(o.getString("id"), o.optString("channel"), o.optString("title"), o.optLong("start"))
                if (item.startMs > now - 3_600_000L) out.add(item)
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(c: Context, items: List<Item>) {
        val arr = JSONArray()
        for (i in items) {
            arr.put(JSONObject().put("id", i.id).put("channel", i.channel).put("title", i.title).put("start", i.startMs))
        }
        prefs(c).edit().putString("items", arr.toString()).apply()
    }

    private fun pending(c: Context, item: Item): PendingIntent {
        val intent = Intent(c, ReminderReceiver::class.java)
            .putExtra("id", item.id)
            .putExtra("channel", item.channel)
            .putExtra("title", item.title)
            .putExtra("start", item.startMs)
        return PendingIntent.getBroadcast(
            c, item.id.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun schedule(c: Context, item: Item) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = System.currentTimeMillis()
        val at = maxOf(item.startMs - LEAD_MS, now + 3_000L)
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(c, item))
        } catch (e: Exception) {
            // Sem permissão de alarme neste aparelho: o lembrete simplesmente não dispara.
        }
    }

    /** Cria o lembrete. Devolve false se o programa já começou. */
    fun add(c: Context, item: Item): Boolean {
        if (item.startMs <= System.currentTimeMillis()) return false
        val items = load(c).filter { it.id != item.id } + item
        save(c, items)
        schedule(c, item)
        return true
    }

    fun remove(c: Context, id: String) {
        val items = load(c)
        val item = items.firstOrNull { it.id == id }
        if (item != null) {
            val am = c.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            am?.cancel(pending(c, item))
        }
        save(c, items.filter { it.id != id })
    }

    /** Depois de reiniciar o aparelho, os alarmes somem: agenda de novo os que ainda valem. */
    fun rescheduleAll(c: Context) {
        val now = System.currentTimeMillis()
        val items = load(c).filter { it.startMs > now }
        save(c, items)
        items.forEach { schedule(c, it) }
    }

    fun notifyNow(c: Context, channel: String, title: String, startMs: Long) {
        val nm = NotificationManagerCompat.from(c)
        if (Build.VERSION.SDK_INT >= 26) {
            val sys = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            sys.createNotificationChannel(
                NotificationChannel(CHANNEL, "Lembretes de programas", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = PendingIntent.getActivity(
            c, 0,
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val minutes = ((startMs - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L)
        val text = if (minutes <= 0L) "$channel • começando agora" else "$channel • começa em ${minutes} min"
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { "Programa" })
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            if (nm.areNotificationsEnabled()) nm.notify(("rem" + title + startMs).hashCode(), n)
        } catch (e: SecurityException) {
            // Sem permissão de notificação (Android 13+): nada a fazer.
        }
    }
}

/** Recebe o alarme na hora do lembrete e mostra a notificação. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val channel = intent.getStringExtra("channel") ?: ""
        val title = intent.getStringExtra("title") ?: ""
        val start = intent.getLongExtra("start", System.currentTimeMillis())
        Reminders.remove(context, id)
        Reminders.notifyNow(context, channel, title, start)
    }
}

/** Reagenda os lembretes depois que o aparelho reinicia. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Reminders.rescheduleAll(context)
    }
}
