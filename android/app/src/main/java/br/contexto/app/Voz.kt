package br.contexto.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** O que está tocando agora (ou nada). */
data class EstadoVoz(
    val titulo: String = "",
    val tocando: Boolean = false,
    val pausado: Boolean = false,
    val indice: Int = 0,
    val total: Int = 0,
    val velocidade: Float = 1f,
    val semVoz: Boolean = false,
)

/**
 * Narração com o leitor de voz do próprio celular (TextToSpeech).
 * Enquanto fala, um serviço em primeiro plano mantém o app vivo, para seguir com a tela apagada.
 */
object Voz {
    val VELOCIDADES = listOf(0.8f, 1f, 1.2f, 1.5f)

    private var tts: TextToSpeech? = null
    private var pronto = false
    private lateinit var app: Context
    private var frases: List<String> = emptyList()
    private var geracao = 0
    private var aoTerminar: (() -> Unit)? = null
    private var pendente: Runnable? = null

    private val _estado = MutableStateFlow(EstadoVoz())
    val estado: StateFlow<EstadoVoz> = _estado

    fun iniciar(c: Context) {
        if (tts != null) return
        app = c.applicationContext
        val vel = app.getSharedPreferences("voz", Context.MODE_PRIVATE).getFloat("vel", 1f)
        _estado.value = _estado.value.copy(velocidade = if (vel in VELOCIDADES) vel else 1f)
        tts = TextToSpeech(app) { status ->
            if (status != TextToSpeech.SUCCESS) { _estado.value = _estado.value.copy(semVoz = true); return@TextToSpeech }
            val t = tts ?: return@TextToSpeech
            var r = t.setLanguage(Locale("pt", "BR"))
            if (r < 0) r = t.setLanguage(Locale("pt"))
            pronto = r >= 0
            _estado.value = _estado.value.copy(semVoz = !pronto)
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { if (id != null) marcar(id) }
                override fun onDone(id: String?) { if (id != null) terminou(id) }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) { if (id != null) terminou(id) }
                override fun onError(id: String?, codigo: Int) { if (id != null) terminou(id) }
            })
            pendente?.run(); pendente = null
        }
    }

    private fun partes(id: String): Pair<Int, Int>? {
        val p = id.split(":"); if (p.size != 2) return null
        return (p[0].toIntOrNull() ?: return null) to (p[1].toIntOrNull() ?: return null)
    }

    private fun marcar(id: String) {
        val (g, i) = partes(id) ?: return
        if (g == geracao) _estado.value = _estado.value.copy(indice = i)
    }

    private fun terminou(id: String) {
        val (g, i) = partes(id) ?: return
        if (g != geracao || _estado.value.pausado) return
        if (i >= frases.size - 1) {
            val fim = aoTerminar
            parar()
            fim?.invoke()
        }
    }

    /** Começa a narrar. trechos: blocos de texto escrito (título, texto, por que importa...). */
    fun tocar(titulo: String, trechos: List<String>, aoFim: (() -> Unit)? = null) {
        val lista = trechos.filter { it.isNotBlank() }.flatMap { Falado.partir(Falado.falar(it)) }
        if (lista.isEmpty()) return
        val acao = Runnable {
            geracao++
            frases = lista
            aoTerminar = aoFim
            _estado.value = _estado.value.copy(titulo = titulo, tocando = true, pausado = false, indice = 0, total = lista.size)
            VozServico.ligar(app, titulo)
            falarDesde(0)
        }
        if (pronto) acao.run() else pendente = acao
    }

    private fun falarDesde(inicio: Int) {
        val t = tts ?: return
        t.stop()
        t.setSpeechRate(_estado.value.velocidade)
        val g = geracao
        for (i in inicio until frases.size) {
            t.speak(frases[i], if (i == inicio) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, Bundle(), "$g:$i")
        }
        _estado.value = _estado.value.copy(indice = inicio, pausado = false, tocando = true)
    }

    fun pausar() {
        if (!_estado.value.tocando) return
        geracao++
        tts?.stop()
        _estado.value = _estado.value.copy(pausado = true)
    }

    fun continuar() {
        if (!_estado.value.tocando) return
        geracao++
        falarDesde(_estado.value.indice)
    }

    fun pular(delta: Int) {
        if (!_estado.value.tocando) return
        geracao++
        falarDesde((_estado.value.indice + delta).coerceIn(0, (frases.size - 1).coerceAtLeast(0)))
    }

    fun mudarVelocidade() {
        val atual = _estado.value.velocidade
        val nova = VELOCIDADES[(VELOCIDADES.indexOf(atual) + 1) % VELOCIDADES.size]
        app.getSharedPreferences("voz", Context.MODE_PRIVATE).edit().putFloat("vel", nova).apply()
        _estado.value = _estado.value.copy(velocidade = nova)
        if (_estado.value.tocando && !_estado.value.pausado) { geracao++; falarDesde(_estado.value.indice) }
    }

    fun parar() {
        geracao++
        tts?.stop()
        frases = emptyList()
        aoTerminar = null
        _estado.value = _estado.value.copy(tocando = false, pausado = false, indice = 0, total = 0, titulo = "")
        if (::app.isInitialized) VozServico.desligar(app)
    }

    /** Abre a tela do Android para instalar a voz em português. */
    fun instalarVoz(c: Context) {
        try {
            c.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try { c.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
        }
    }
}
