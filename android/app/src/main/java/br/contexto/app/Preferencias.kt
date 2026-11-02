package br.contexto.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

/** Escolhas de quem lê: quais categorias aparecem na edição e em que ordem. */
class Preferencias(c: Context) {
    private val p = c.getSharedPreferences("leitor", Context.MODE_PRIVATE)

    private val _escolhidas = MutableStateFlow(ler())
    /** null = ainda não personalizou (vale o "padrao" do catálogo). */
    val escolhidas: StateFlow<List<String>?> = _escolhidas

    private fun ler(): List<String>? = p.getString("categorias", null)?.let { s ->
        runCatching { JSONArray(s).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrNull()
    }?.takeIf { it.isNotEmpty() }

    fun salvar(lista: List<String>?) {
        if (lista.isNullOrEmpty()) p.edit().remove("categorias").apply()
        else p.edit().putString("categorias", JSONArray(lista).toString()).apply()
        _escolhidas.value = lista?.takeIf { it.isNotEmpty() }
    }

    fun efetivas(cat: Catalogo): List<String> =
        _escolhidas.value ?: cat.lista.filter { it.padrao }.map { it.id }.ifEmpty { listOf("brasil", "mundo", "inovacao") }

    var tema: String
        get() = p.getString("tema", "sistema") ?: "sistema"
        set(v) { p.edit().putString("tema", v).apply(); _tema.value = v }
    private val _tema = MutableStateFlow(tema)
    val temaFlow: StateFlow<String> = _tema

    var tamanhoTexto: Float
        get() = p.getFloat("tamanho", 1f)
        set(v) { p.edit().putFloat("tamanho", v).apply(); _tamanho.value = v }
    private val _tamanho = MutableStateFlow(tamanhoTexto)
    val tamanhoFlow: StateFlow<Float> = _tamanho
}
