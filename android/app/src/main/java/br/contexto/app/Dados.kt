package br.contexto.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Os dados do Contexto vêm em JSON do mesmo endereço que alimenta o site
 * (edicoes/index.json, edicoes/<id>.json, contextos/<slug>.json e categorias.json).
 * O app desenha tudo com telas nativas; o JSON fica guardado no aparelho para abrir sem internet.
 */
const val SITE = "https://campos2602.github.io/contexto/"

data class Fonte(val t: String, val u: String)

/** Item de lista: texto simples ou {b: destaque, t: texto}. */
data class Item(val b: String?, val t: String)

data class Bloco(val h: String, val p: List<String>, val li: List<Item>)

data class Completa(
    val fina: String, val atualizada: String, val selo: String, val fato: String,
    val blocos: List<Bloco>, val fontes: List<Fonte>,
)

data class Nota(
    val id: String, val selo: String, val titulo: String, val texto: String, val pq: String,
    val fontes: List<Fonte>, val contexto: String?, val completa: Completa?,
)

data class Secao(val nome: String, val categoria: String, val notas: List<Nota>)

data class Numero(val valor: String, val texto: String, val fontes: List<Fonte>)

data class Parecer(
    val veredito: String, val detalhe: String, val resumo: String, val pontos: List<String>,
    val equilibrio: String, val foraDaEdicao: List<String>,
)

data class Edicao(
    val id: String, val data: String, val dia: String, val leitura: String, val status: String,
    val abre: String, val trinta: List<String>, val secoes: List<Secao>,
    val numero: Numero?, val fechar: Numero?, val corrigimos: List<String>, val transp: String,
    val parecer: Parecer?,
)

data class ResumoEdicao(val id: String, val data: String, val dia: String, val statusTexto: String)

data class Marco(val d: String, val t: String)

data class Contexto(
    val titulo: String, val atualizado: String, val selo: String, val frase: String,
    val linha: List<Marco>, val termos: List<Item>, val quem: List<Item>, val disputa: List<Item>,
    val observar: List<String>, val fontes: List<Fonte>,
)

data class Categoria(
    val id: String, val nome: String, val desc: String, val padrao: Boolean,
    val corClara: Long, val corEscura: Long, val aliases: List<String>,
)

// ---------- leitura do JSON ----------

private fun JSONObject.s(k: String) = if (isNull(k)) "" else optString(k, "")
private fun JSONObject.sn(k: String) = s(k).ifBlank { null }
private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it, null)?.takeIf { s -> s.isNotBlank() } }

private fun fontes(a: JSONArray?): List<Fonte> =
    if (a == null) emptyList() else (0 until a.length()).mapNotNull { i ->
        a.optJSONObject(i)?.let { Fonte(it.s("t"), it.s("u")) }?.takeIf { it.u.startsWith("http") }
    }

private fun itens(a: JSONArray?): List<Item> =
    if (a == null) emptyList() else (0 until a.length()).mapNotNull { i ->
        val o = a.opt(i)
        when (o) {
            is JSONObject -> {
                // Contextos usam {t, d} em "termos"; notas usam {b, t}.
                if (o.has("d") && !o.has("b")) Item(o.s("t"), o.s("d")) else Item(o.sn("b"), o.s("t"))
            }
            is String -> Item(null, o)
            else -> null
        }
    }

private fun completa(o: JSONObject?): Completa? {
    if (o == null) return null
    val bl = o.optJSONArray("blocos")
    val blocos = if (bl == null) emptyList() else (0 until bl.length()).mapNotNull { i ->
        bl.optJSONObject(i)?.let { b -> Bloco(b.s("h"), b.optJSONArray("p").strings(), itens(b.optJSONArray("li"))) }
    }
    return Completa(o.s("fina"), o.s("atualizada"), o.s("selo"), o.s("fato"), blocos, fontes(o.optJSONArray("fontes")))
}

private fun numero(o: JSONObject?): Numero? =
    o?.let { Numero(it.s("valor"), it.s("texto"), fontes(it.optJSONArray("fontes"))) }?.takeIf { it.texto.isNotBlank() }

fun lerEdicao(id: String, j: JSONObject, cats: Catalogo): Edicao {
    val sa = j.optJSONArray("secoes") ?: JSONArray()
    val secoes = (0 until sa.length()).mapNotNull { i ->
        val s = sa.optJSONObject(i) ?: return@mapNotNull null
        val na = s.optJSONArray("notas") ?: JSONArray()
        val notas = (0 until na.length()).mapNotNull { k ->
            val n = na.optJSONObject(k) ?: return@mapNotNull null
            Nota(
                n.s("id"), n.s("selo"), n.s("titulo"), n.s("texto"), n.s("pq"), fontes(n.optJSONArray("fontes")),
                n.sn("contexto"), completa(n.optJSONObject("completa")),
            )
        }
        val nome = s.s("nome")
        Secao(nome, cats.idDe(nome, s.sn("categoria")), notas)
    }
    val p = j.optJSONObject("parecer")
    return Edicao(
        id = id, data = j.s("data"), dia = j.s("dia"), leitura = j.s("leitura"), status = j.s("status"),
        abre = j.s("abre"), trinta = j.optJSONArray("trinta").strings(), secoes = secoes,
        numero = numero(j.optJSONObject("numero")), fechar = numero(j.optJSONObject("fechar")),
        corrigimos = j.optJSONArray("corrigimos").strings(), transp = j.s("transp"),
        parecer = p?.let {
            Parecer(it.s("veredito"), it.s("detalhe"), it.s("resumo"), it.optJSONArray("pontos").strings(),
                it.s("equilibrio"), it.optJSONArray("foraDaEdicao").strings())
        },
    )
}

fun lerContexto(j: JSONObject): Contexto {
    val la = j.optJSONArray("linha") ?: JSONArray()
    val linha = (0 until la.length()).mapNotNull { i -> la.optJSONObject(i)?.let { Marco(it.s("d"), it.s("t")) } }
    return Contexto(
        j.s("titulo"), j.s("atualizado"), j.s("selo"), j.s("frase"), linha,
        itens(j.optJSONArray("termos")), itens(j.optJSONArray("quem")), itens(j.optJSONArray("disputa")),
        j.optJSONArray("observar").strings(), fontes(j.optJSONArray("fontes")),
    )
}

private fun cor(s: String?, padrao: Long): Long =
    s?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000 or it } ?: padrao

class Catalogo(val lista: List<Categoria>) {
    val porId = lista.associateBy { it.id }

    private fun norm(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").lowercase().trim()

    /** Mesmo critério do site: usa "categoria" e, sem ela, casa o nome da seção (ou um apelido) com o catálogo. */
    fun idDe(nome: String, categoria: String?): String {
        if (categoria != null && porId.containsKey(categoria)) return categoria
        val n = norm(nome)
        lista.firstOrNull { norm(it.nome) == n || it.id == n || it.aliases.any { a -> norm(a) == n } }?.let { return it.id }
        return categoria ?: n.replace(Regex("[^a-z0-9]+"), "-").trim('-')
    }

    fun nome(id: String) = porId[id]?.nome ?: id

    companion object {
        fun ler(j: JSONObject): Catalogo {
            val a = j.optJSONArray("categorias") ?: JSONArray()
            return Catalogo((0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val c = o.optJSONArray("cor")
                Categoria(
                    o.s("id"), o.s("nome"), o.s("desc"), o.optBoolean("padrao", false),
                    cor(c?.optString(0), 0xFFCFE9E6), cor(c?.optString(1), 0xFF15403D),
                    o.optJSONArray("aliases").strings(),
                )
            }.filter { it.id.isNotBlank() })
        }

        val PADRAO = Catalogo(listOf(
            Categoria("brasil", "Brasil", "", true, 0xFFB2DCD7, 0xFF1B5651, emptyList()),
            Categoria("mundo", "Mundo", "", true, 0xFFCFE9E6, 0xFF15403D, emptyList()),
            Categoria("inovacao", "Inovação", "", true, 0xFF93CEC7, 0xFF226C66, listOf("Tecnologia")),
        ))
    }
}

// ---------- rede com cópia no aparelho ----------

class Repositorio(private val ctx: Context) {
    private val pasta = File(ctx.filesDir, "cache").apply { mkdirs() }

    private fun arquivo(caminho: String) = File(pasta, caminho.replace('/', '_'))

    /** Baixa do site; se falhar, usa a última cópia guardada. Devolve null se não houver nenhuma das duas. */
    private suspend fun obter(caminho: String, forcar: Boolean): String? = withContext(Dispatchers.IO) {
        val f = arquivo(caminho)
        // Edições antigas não mudam: se já tem cópia, não precisa baixar de novo.
        val estavel = caminho.startsWith("edicoes/") && caminho != "edicoes/index.json" && !forcar
        if (estavel && f.exists() && System.currentTimeMillis() - f.lastModified() > 6 * 3600_000L) return@withContext f.readText()
        try {
            val c = (URL(SITE + caminho + "?t=" + System.currentTimeMillis() / 60_000).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (c.responseCode != 200) throw java.io.IOException("HTTP " + c.responseCode)
                val txt = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                f.writeText(txt)
                txt
            } finally { c.disconnect() }
        } catch (e: Exception) {
            if (f.exists()) f.readText() else null
        }
    }

    suspend fun catalogo(forcar: Boolean = false): Catalogo =
        obter("categorias.json", forcar)?.let { runCatching { Catalogo.ler(JSONObject(it)) }.getOrNull() }
            ?.takeIf { it.lista.isNotEmpty() } ?: Catalogo.PADRAO

    /** Lista das edições, a mais recente primeiro. Edições retidas pelo editor-chefe não aparecem. */
    suspend fun indice(forcar: Boolean = false): List<ResumoEdicao>? =
        obter("edicoes/index.json", forcar)?.let { txt ->
            runCatching {
                val a = JSONArray(txt)
                (0 until a.length()).mapNotNull { i ->
                    val o = a.optJSONObject(i) ?: return@mapNotNull null
                    ResumoEdicao(o.s("id"), o.s("data"), o.s("dia"), o.s("statusTexto"))
                }.filter { !it.statusTexto.startsWith("Retida") && !it.id.startsWith("piloto") }
            }.getOrNull()
        }

    suspend fun edicao(id: String, cats: Catalogo, forcar: Boolean = false): Edicao? =
        obter("edicoes/$id.json", forcar)?.let { runCatching { lerEdicao(id, JSONObject(it), cats) }.getOrNull() }
            ?.takeIf { it.status != "retida" }

    suspend fun contexto(slug: String): Contexto? =
        obter("contextos/$slug.json", false)?.let { runCatching { lerContexto(JSONObject(it)) }.getOrNull() }
}

// ---------- comentários (mesma tabela do site) ----------

object Comentarios {
    private const val URL_BASE = "https://kgowumosjfpqhmawuzjn.supabase.co/rest/v1/contexto_comentarios"
    private const val CHAVE = "sb_publishable_dFuLZUEQXNN5p_ThuomTwA_ZcSCy0Gj"
    val TIPOS = listOf("Sugestão", "Achei tendencioso", "Erro de fato", "Elogio")

    suspend fun enviar(edicao: String, alvo: String, alvoTitulo: String, tipo: String, texto: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val corpo = JSONObject()
                    .put("edicao", edicao).put("alvo", alvo).put("alvo_titulo", alvoTitulo.take(300))
                    .put("tipo", tipo).put("texto", texto.trim().take(4000))
                val c = (URL(URL_BASE).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; doOutput = true; connectTimeout = 10_000; readTimeout = 15_000
                    setRequestProperty("apikey", CHAVE)
                    setRequestProperty("Authorization", "Bearer $CHAVE")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Prefer", "return=minimal")
                }
                try {
                    c.outputStream.use { it.write(corpo.toString().toByteArray(Charsets.UTF_8)) }
                    c.responseCode in 200..299
                } finally { c.disconnect() }
            } catch (e: Exception) { false }
        }
}
