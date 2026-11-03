package br.contexto.app

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

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

/** Um pedaço da narração: texto falado pela voz do celular ou um MP3 gravado com a voz da ElevenLabs. */
private sealed interface Parte {
    data class Texto(val texto: String) : Parte
    data class Gravado(val url: String, val reserva: List<String>) : Parte
}

/**
 * Narração. Quando a redação gravou o trecho com a voz da ElevenLabs, toca o MP3; senão (ou sem internet),
 * usa o leitor de voz do próprio celular (TextToSpeech).
 * Enquanto fala, um serviço em primeiro plano mantém o app vivo, para seguir com a tela apagada.
 */
object Voz {
    val VELOCIDADES = listOf(0.8f, 1f, 1.2f, 1.5f)

    private var tts: TextToSpeech? = null
    private var pronto = false
    private lateinit var app: Context
    private var partes: List<Parte> = emptyList()
    private var geracao = 0
    private var aoTerminar: (() -> Unit)? = null
    private var pendente: Runnable? = null
    private var player: MediaPlayer? = null
    private val principal = Handler(Looper.getMainLooper())

    /** Texto escrito -> MP3 gravado. Preenchido quando uma edição abre. */
    private val gravados = ConcurrentHashMap<String, String>()
    fun gravados(mapa: Map<String, String>) { gravados.putAll(mapa) }

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
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { if (id != null) principal.post { terminou(id) } }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) { if (id != null) principal.post { terminou(id) } }
                override fun onError(id: String?, codigo: Int) { if (id != null) principal.post { terminou(id) } }
            })
            pendente?.run(); pendente = null
        }
    }

    private fun partesDoId(id: String): Pair<Int, Int>? {
        val p = id.split(":"); if (p.size != 2) return null
        return (p[0].toIntOrNull() ?: return null) to (p[1].toIntOrNull() ?: return null)
    }

    /** Fim de um trecho do leitor do celular: id "geração:índice:subtrecho" vira "geração:índice" no último. */
    private fun terminou(id: String) {
        val (g, i) = partesDoId(id) ?: return
        if (g != geracao || _estado.value.pausado) return
        proxima(i)
    }

    private fun proxima(i: Int) {
        if (i >= partes.size - 1) {
            val fim = aoTerminar
            parar()
            fim?.invoke()
        } else tocarParte(i + 1)
    }

    /** Começa a narrar. trechos: blocos de texto escrito (título, texto, por que importa...). */
    fun tocar(titulo: String, trechos: List<String>, aoFim: (() -> Unit)? = null) {
        val lista = trechos.filter { it.isNotBlank() }.map { t ->
            val falas = Falado.partir(Falado.falar(t))
            gravados[t.trim()]?.let { Parte.Gravado(it, falas) } ?: Parte.Texto(falas.joinToString(" "))
        }.filter { it !is Parte.Texto || it.texto.isNotBlank() }
        if (lista.isEmpty()) return
        val acao = Runnable {
            geracao++
            partes = lista
            aoTerminar = aoFim
            _estado.value = _estado.value.copy(titulo = titulo, tocando = true, pausado = false, indice = 0, total = lista.size)
            VozServico.ligar(app, titulo)
            tocarParte(0)
        }
        // Só espera o leitor do celular ficar pronto se algum trecho precisar dele.
        if (pronto || lista.all { it is Parte.Gravado }) acao.run() else pendente = acao
    }

    private fun soltarPlayer() {
        player?.let { runCatching { it.reset(); it.release() } }
        player = null
    }

    private fun tocarParte(i: Int) {
        tts?.stop()
        soltarPlayer()
        val g = geracao
        _estado.value = _estado.value.copy(indice = i, pausado = false, tocando = true)
        when (val p = partes.getOrNull(i) ?: return) {
            is Parte.Texto -> falarTexto(listOf(p.texto), g, i)
            is Parte.Gravado -> {
                val mp = MediaPlayer()
                player = mp
                try {
                    mp.setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    mp.setDataSource(p.url)
                    mp.setOnPreparedListener {
                        if (g != geracao) return@setOnPreparedListener
                        aplicarVelocidade(it)
                        if (!_estado.value.pausado) it.start()
                    }
                    mp.setOnCompletionListener { if (g == geracao && !_estado.value.pausado) proxima(i) }
                    // Sem internet ou MP3 com problema: lê o mesmo trecho com a voz do celular.
                    mp.setOnErrorListener { _, _, _ -> if (g == geracao) { soltarPlayer(); falarTexto(p.reserva, g, i) }; true }
                    mp.prepareAsync()
                } catch (e: Exception) {
                    soltarPlayer(); falarTexto(p.reserva, g, i)
                }
            }
        }
    }

    private fun aplicarVelocidade(mp: MediaPlayer) {
        runCatching { mp.playbackParams = mp.playbackParams.setSpeed(_estado.value.velocidade) }
    }

    /** Fala os textos em sequência; só o último avisa o fim do trecho. */
    private fun falarTexto(textos: List<String>, g: Int, i: Int) {
        val t = tts
        if (t == null || !pronto) { principal.post { if (g == geracao) proxima(i) }; return }
        t.setSpeechRate(_estado.value.velocidade)
        textos.forEachIndexed { k, s ->
            val id = if (k == textos.size - 1) "$g:$i" else "$g:$i:$k"
            t.speak(s, if (k == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, Bundle(), id)
        }
    }

    fun pausar() {
        if (!_estado.value.tocando) return
        _estado.value = _estado.value.copy(pausado = true)
        val mp = player
        if (mp != null) { runCatching { if (mp.isPlaying) mp.pause() } }
        else { geracao++; tts?.stop() }
    }

    fun continuar() {
        if (!_estado.value.tocando) return
        val mp = player
        if (mp != null) {
            _estado.value = _estado.value.copy(pausado = false)
            runCatching { aplicarVelocidade(mp); mp.start() }.onFailure { tocarParte(_estado.value.indice) }
        } else { geracao++; tocarParte(_estado.value.indice) }
    }

    fun pular(delta: Int) {
        if (!_estado.value.tocando) return
        geracao++
        tocarParte((_estado.value.indice + delta).coerceIn(0, (partes.size - 1).coerceAtLeast(0)))
    }

    fun mudarVelocidade() {
        val atual = _estado.value.velocidade
        val nova = VELOCIDADES[(VELOCIDADES.indexOf(atual) + 1) % VELOCIDADES.size]
        app.getSharedPreferences("voz", Context.MODE_PRIVATE).edit().putFloat("vel", nova).apply()
        _estado.value = _estado.value.copy(velocidade = nova)
        if (!_estado.value.tocando || _estado.value.pausado) return
        val mp = player
        if (mp != null) runCatching { aplicarVelocidade(mp) }
        else { geracao++; tocarParte(_estado.value.indice) }
    }

    fun parar() {
        geracao++
        tts?.stop()
        soltarPlayer()
        partes = emptyList()
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
