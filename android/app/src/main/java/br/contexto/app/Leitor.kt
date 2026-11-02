package br.contexto.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Estado de carregamento de uma tela. */
sealed interface Carga<out T> {
    data object Carregando : Carga<Nothing>
    data class Pronto<T>(val valor: T) : Carga<T>
    data class Falhou(val mensagem: String) : Carga<Nothing>
}

/** Guarda o que o app já baixou e pede ao repositório o que falta. */
class Leitor(app: Application) : AndroidViewModel(app) {
    val repo = Repositorio(app)
    val prefs = Preferencias(app)

    private val _catalogo = MutableStateFlow(Catalogo.PADRAO)
    val catalogo: StateFlow<Catalogo> = _catalogo

    private val _indice = MutableStateFlow<Carga<List<ResumoEdicao>>>(Carga.Carregando)
    val indice: StateFlow<Carga<List<ResumoEdicao>>> = _indice

    private val _atualizando = MutableStateFlow(false)
    val atualizando: StateFlow<Boolean> = _atualizando

    private val edicoes = mutableMapOf<String, MutableStateFlow<Carga<Edicao>>>()
    private val contextos = mutableMapOf<String, MutableStateFlow<Carga<Contexto>>>()

    /** Áudio pedido pela notificação do lembrete ("resumo" ou "edicao"), tocado quando a edição de hoje abrir. */
    val pedidoAudio = MutableStateFlow<String?>(null)

    init { atualizar(false) }

    fun atualizar(forcar: Boolean = true) {
        viewModelScope.launch {
            _atualizando.value = true
            _catalogo.value = repo.catalogo(forcar)
            val lista = repo.indice(forcar)
            _indice.value = when {
                lista == null -> Carga.Falhou("Sem conexão e sem edição guardada no aparelho. Confira a internet e tente de novo.")
                lista.isEmpty() -> Carga.Falhou("Ainda não há edição publicada.")
                else -> Carga.Pronto(lista)
            }
            // A edição mais recente pode ter sido corrigida: baixa de novo.
            lista?.firstOrNull()?.let { carregarEdicao(it.id, forcar = true) }
            _atualizando.value = false
        }
    }

    fun edicao(id: String): StateFlow<Carga<Edicao>> {
        val f = edicoes.getOrPut(id) { MutableStateFlow(Carga.Carregando) }
        if (f.value is Carga.Carregando || f.value is Carga.Falhou) carregarEdicao(id, false)
        return f
    }

    private fun carregarEdicao(id: String, forcar: Boolean) {
        val f = edicoes.getOrPut(id) { MutableStateFlow(Carga.Carregando) }
        viewModelScope.launch {
            val e = repo.edicao(id, _catalogo.value, forcar)
            if (e != null) f.value = Carga.Pronto(e)
            else if (f.value !is Carga.Pronto) f.value = Carga.Falhou("Não foi possível abrir esta edição. Confira a internet e tente de novo.")
        }
    }

    fun contexto(slug: String): StateFlow<Carga<Contexto>> {
        val f = contextos.getOrPut(slug) { MutableStateFlow(Carga.Carregando) }
        if (f.value !is Carga.Pronto) viewModelScope.launch {
            f.value = repo.contexto(slug)?.let { Carga.Pronto(it) } ?: Carga.Falhou("Não foi possível abrir esta página de Contexto.")
        }
        return f
    }
}

// ---------- o que cada botão Ouvir lê ----------

fun trechosNota(n: Nota): List<String> = listOf(n.titulo, n.texto, "Por que importa: " + n.pq)

fun trechosResumo(e: Edicao): List<String> = listOf("Contexto de " + e.dia + ". Em 30 segundos.") + e.trinta

fun trechosEdicao(e: Edicao, ordem: List<Secao>): List<String> {
    val t = mutableListOf("Contexto de " + e.dia + ".", e.abre, "Em 30 segundos.")
    t += e.trinta
    for (s in ordem) {
        t += s.nome + "."
        for (n in s.notas) t += trechosNota(n)
    }
    e.numero?.let { t += "Número do dia: " + it.valor + ". " + it.texto }
    e.fechar?.let { t += "Para fechar. " + it.texto }
    return t
}

fun trechosCompleta(n: Nota): List<String> {
    val c = n.completa ?: return trechosNota(n)
    val t = mutableListOf(n.titulo, c.fina, c.fato)
    for (b in c.blocos) {
        t += b.h + "."
        t += b.p
        t += b.li.map { if (it.b != null) it.b + " " + it.t else it.t }
    }
    return t
}

fun trechosContexto(c: Contexto): List<String> {
    val t = mutableListOf(c.titulo + ".", c.frase)
    if (c.linha.isNotEmpty()) { t += "Linha do tempo."; t += c.linha.map { it.d + ": " + it.t } }
    if (c.termos.isNotEmpty()) { t += "Termos para entender."; t += c.termos.map { (it.b ?: "") + ": " + it.t } }
    if (c.quem.isNotEmpty()) { t += "Quem é quem."; t += c.quem.map { (it.b ?: "") + " " + it.t } }
    if (c.disputa.isNotEmpty()) { t += "O que está em disputa."; t += c.disputa.map { (it.b ?: "") + " " + it.t } }
    if (c.observar.isNotEmpty()) { t += "O que observar."; t += c.observar }
    return t
}

/** Seções na ordem que a pessoa escolheu; as outras vão para "Outras seções de hoje". */
data class Arrumacao(val escolhidas: List<Secao>, val outras: List<Secao>, val faltam: List<String>)

fun arrumar(e: Edicao, ordem: List<String>, cat: Catalogo): Arrumacao {
    val esc = ordem.flatMap { id -> e.secoes.filter { it.categoria == id } }
    val outras = e.secoes.filter { it.categoria !in ordem }
    val presentes = e.secoes.map { it.categoria }.toSet()
    val faltam = ordem.filter { it !in presentes }.map { cat.nome(it) }
    return Arrumacao(esc, outras, faltam)
}
