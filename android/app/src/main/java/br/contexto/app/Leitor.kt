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

fun trechosResumo(e: Edicao, resumo: List<String> = e.trinta): List<String> =
    listOf("Contexto de " + e.dia + ". Em 30 segundos.") + resumo

fun trechosEdicao(e: Edicao, ordem: List<Secao>, resumo: List<String> = e.trinta): List<String> {
    val t = mutableListOf("Contexto de " + e.dia + ".", e.abre, "Em 30 segundos.")
    t += resumo
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
data class Arrumacao(
    val escolhidas: List<Secao>, val outras: List<Secao>, val faltam: List<String>,
    /** "Em 30 segundos" só com as seções que a pessoa escolheu, na ordem dela. */
    val resumo: List<String>,
)

fun arrumar(e: Edicao, ordem: List<String>, cat: Catalogo): Arrumacao {
    val esc = ordem.flatMap { id -> e.secoes.filter { it.categoria == id } }
    val outras = e.secoes.filter { it.categoria !in ordem }
    val presentes = e.secoes.map { it.categoria }.toSet()
    val faltam = ordem.filter { it !in presentes }.map { cat.nome(it) }
    return Arrumacao(esc, outras, faltam, resumoPara(e, esc))
}

private val ACENTOS = Regex("\\p{Mn}+")
private fun palavras(t: String): Set<String> =
    java.text.Normalizer.normalize(t.lowercase(), java.text.Normalizer.Form.NFD).replace(ACENTOS, "")
        .split(Regex("[^a-z0-9]+")).filter { it.length >= 4 }.toSet()

/**
 * Monta o "Em 30 segundos" de cada leitor. Cada frase do resumo da redação vai para a seção da nota
 * de que ela trata (a que tem mais palavras em comum). Ficam as frases das seções escolhidas, na
 * ordem escolhida; seção escolhida sem frase no resumo entra com o título da sua primeira nota.
 */
fun resumoPara(e: Edicao, escolhidas: List<Secao>, maximo: Int = 5): List<String> {
    if (escolhidas.isEmpty()) return e.trinta
    val notas = e.secoes.flatMap { s -> s.notas.map { n -> s to palavras(n.titulo + " " + n.texto + " " + n.pq) } }
    val porSecao = mutableMapOf<Secao, MutableList<String>>()
    for (frase in e.trinta) {
        val p = palavras(frase)
        val melhor = notas.maxByOrNull { (_, w) -> w.count { it in p } } ?: continue
        if (melhor.second.count { it in p } >= 2) porSecao.getOrPut(melhor.first) { mutableListOf() } += frase
    }
    val r = mutableListOf<String>()
    for (s in escolhidas) {
        val frases = porSecao[s]
        if (!frases.isNullOrEmpty()) r += frases
        else s.notas.firstOrNull()?.let { r += it.titulo.trimEnd('.') + "." }
    }
    return r.distinct().take(maximo).ifEmpty { e.trinta }
}
