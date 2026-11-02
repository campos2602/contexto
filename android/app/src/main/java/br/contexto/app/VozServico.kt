package br.contexto.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Serviço em primeiro plano enquanto o Contexto lê em voz alta: o Android não fecha o app
 * com a tela apagada, e a notificação permite pausar ou parar.
 */
class VozServico : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACAO_PARAR -> { Voz.parar(); return START_NOT_STICKY }
            ACAO_PAUSAR -> {
                if (Voz.estado.value.pausado) Voz.continuar() else Voz.pausar()
            }
        }
        val titulo = intent?.getStringExtra(EXTRA_TITULO) ?: Voz.estado.value.titulo
        val n = notificacao(this, titulo, Voz.estado.value.pausado)
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(ID, n)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CANAL = "ouvir"
        private const val ID = 2
        private const val ACAO_PARAR = "br.contexto.app.VOZ_PARAR"
        private const val ACAO_PAUSAR = "br.contexto.app.VOZ_PAUSAR"
        private const val EXTRA_TITULO = "titulo"

        fun ligar(c: Context, titulo: String) {
            val i = Intent(c, VozServico::class.java).putExtra(EXTRA_TITULO, titulo)
            try {
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
            } catch (e: Exception) {
                // Sem permissão para o serviço (app em segundo plano): a voz toca assim mesmo, só sem a notificação.
            }
        }

        fun desligar(c: Context) {
            try { c.stopService(Intent(c, VozServico::class.java)) } catch (_: Exception) {}
        }

        private fun notificacao(c: Context, titulo: String, pausado: Boolean): Notification {
            val nm = c.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26 && nm?.getNotificationChannel(CANAL) == null) {
                nm?.createNotificationChannel(NotificationChannel(CANAL, "Ouvindo o Contexto", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Aparece enquanto o app lê uma notícia em voz alta."
                    setShowBadge(false)
                })
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val abrir = PendingIntent.getActivity(c, 20, Intent(c, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), flags)
            val parar = PendingIntent.getService(c, 21, Intent(c, VozServico::class.java).setAction(ACAO_PARAR), flags)
            val pausar = PendingIntent.getService(c, 22, Intent(c, VozServico::class.java).setAction(ACAO_PAUSAR), flags)
            @Suppress("DEPRECATION")
            val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, CANAL) else Notification.Builder(c)
            return b.setSmallIcon(R.drawable.ic_notificacao)
                .setColor(0xFF0B7A75.toInt())
                .setContentTitle(if (pausado) "Pausado" else "Ouvindo o Contexto")
                .setContentText(titulo)
                .setContentIntent(abrir)
                .setOngoing(!pausado)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .addAction(Notification.Action.Builder(null, if (pausado) "Continuar" else "Pausar", pausar).build())
                .addAction(Notification.Action.Builder(null, "Parar", parar).build())
                .build()
        }
    }
}
