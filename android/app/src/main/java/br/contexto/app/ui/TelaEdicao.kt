package br.contexto.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.contexto.app.Carga
import br.contexto.app.Catalogo
import br.contexto.app.Edicao
import br.contexto.app.Lembrete
import br.contexto.app.Leitor
import br.contexto.app.Nota
import br.contexto.app.SITE
import br.contexto.app.Secao
import br.contexto.app.Voz
import br.contexto.app.arrumar
import br.contexto.app.trechosEdicao
import br.contexto.app.trechosNota
import br.contexto.app.trechosResumo

/** Ações que saem de uma tela de leitura. */
class Navegar(
    val nota: (edicao: String, nota: String) -> Unit,
    val contexto: (slug: String) -> Unit,
    val ajustes: () -> Unit,
    val comentar: (edicao: String, alvo: String, titulo: String, tipo: String?) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaEdicao(leitor: Leitor, id: String?, nav: Navegar, ehHoje: Boolean, rodape: PaddingValues) {
    val indice by leitor.indice.collectAsState()
    val atualizando by leitor.atualizando.collectAsState()
    val idEfetivo = id ?: (indice as? Carga.Pronto)?.valor?.firstOrNull()?.id

    PullToRefreshBox(isRefreshing = atualizando, onRefresh = { leitor.atualizar(true) }, modifier = Modifier.fillMaxSize()) {
        when {
            idEfetivo != null -> CorpoEdicao(leitor, idEfetivo, nav, ehHoje, rodape)
            indice is Carga.Falhou -> Column(Modifier.padding(16.dp)) {
                Cabecalho(null)
                Aviso((indice as Carga.Falhou).mensagem, "Tentar de novo") { leitor.atualizar(true) }
            }
            else -> Column(Modifier.padding(16.dp)) { Cabecalho(null); Carregando() }
        }
    }
}

@Composable
private fun Cabecalho(e: Edicao?) {
    val c = LocalCores.current
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Text("Contexto", style = Tipos.marca(), color = c.tinta, modifier = Modifier.semantics { heading() })
        Text("Em 5 minutos, o que importa hoje e por quê.", style = Tipos.pequeno(), color = c.apagado)
        if (e != null) {
            Spacer(Modifier.height(10.dp))
            Text(e.dia, style = Tipos.subtitulo(), color = c.tinta)
            if (e.leitura.isNotBlank()) Text(e.leitura + " de leitura", style = Tipos.pequeno(), color = c.apagado)
        }
    }
}

@Composable
private fun CorpoEdicao(leitor: Leitor, id: String, nav: Navegar, ehHoje: Boolean, rodape: PaddingValues) {
    val carga by remember(id) { leitor.edicao(id) }.collectAsState()
    val catalogo by leitor.catalogo.collectAsState()
    val escolhidas by leitor.prefs.escolhidas.collectAsState()
    val pedido by leitor.pedidoAudio.collectAsState()
    val ctx = LocalContext.current

    when (val k = carga) {
        is Carga.Carregando -> Column(Modifier.padding(16.dp)) { Cabecalho(null); Carregando() }
        is Carga.Falhou -> Column(Modifier.padding(16.dp)) {
            Cabecalho(null); Aviso(k.mensagem, "Tentar de novo") { leitor.atualizar(true) }
        }
        is Carga.Pronto -> {
            val e = k.valor
            val ordem = escolhidas ?: leitor.prefs.efetivas(catalogo)
            val arr = arrumar(e, ordem, catalogo)
            // Toque na notificação do lembrete: começa o áudio pedido assim que a edição de hoje abre.
            LaunchedEffect(pedido, e.id) {
                val p = pedido
                if (ehHoje && p != null) {
                    leitor.pedidoAudio.value = null
                    tocarEdicao(ctx, e, if (p == "edicao") arr.escolhidas + arr.outras else null, arr.resumo)
                }
            }
            ListaEdicao(e, arr.escolhidas, arr.outras, arr.faltam, arr.resumo, catalogo, nav, rodape)
        }
    }
}

private fun tocarEdicao(ctx: android.content.Context, e: Edicao, ordem: List<Secao>?, resumo: List<String>) {
    val titulo = if (ordem == null) "Em 30 segundos" else "Edição de " + e.dia
    Voz.tocar(titulo, if (ordem == null) trechosResumo(e, resumo) else trechosEdicao(e, ordem, resumo))
    Lembrete.marcarOuvido(ctx)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListaEdicao(
    e: Edicao, secoes: List<Secao>, outras: List<Secao>, faltam: List<String>, resumo: List<String>, catalogo: Catalogo,
    nav: Navegar, rodape: PaddingValues,
) {
    val c = LocalCores.current
    val ctx = LocalContext.current
    var outrasAbertas by rememberSaveable(e.id) { mutableStateOf(false) }
    var parecerAberto by rememberSaveable(e.id) { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = rodape.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Cabecalho(e) }
        if (e.abre.isNotBlank()) item { Paragrafo(e.abre) }
        if (resumo.isNotEmpty()) item {
            Cartao(fundo = c.acentoSuave) {
                Text("Em 30 segundos", style = Tipos.titulo(), color = c.tinta, modifier = Modifier.semantics { heading() })
                resumo.forEach { Topico(br.contexto.app.Item(null, it)) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Botao("Ouvir o resumo", TipoBotao.Cheio, "▶") { tocarEdicao(ctx, e, null, resumo) }
                    Botao("Ouvir a edição inteira", TipoBotao.Contorno, "▶") { tocarEdicao(ctx, e, secoes + outras, resumo) }
                }
            }
        }
        secoes.forEach { s -> secao(s, e, catalogo, nav) }
        if (faltam.isNotEmpty()) item {
            Text("Hoje não saiu nada de " + juntar(faltam) + ".", style = Tipos.pequeno(), color = c.apagado)
        }
        if (outras.isNotEmpty()) {
            item {
                Cartao(Modifier.clickable { outrasAbertas = !outrasAbertas }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Outras seções de hoje", style = Tipos.subtitulo(), color = c.tinta)
                            Text(outras.joinToString(", ") { it.nome }, style = Tipos.pequeno(), color = c.apagado)
                        }
                        Text(if (outrasAbertas) "▲" else "▼", color = c.acento)
                    }
                }
            }
            if (outrasAbertas) outras.forEach { s -> secao(s, e, catalogo, nav) }
        }
        item {
            Text("Escolher as seções e a ordem", style = Tipos.rotulo(), color = c.acento,
                modifier = Modifier.clickable { nav.ajustes() }.padding(vertical = 6.dp))
        }
        e.numero?.let { n ->
            item {
                Cartao {
                    Etiqueta("Número do dia", null)
                    Text(n.valor, style = Tipos.marca(), color = c.acento)
                    Paragrafo(n.texto)
                    Fontes(n.fontes)
                }
            }
        }
        e.fechar?.let { f ->
            item {
                Cartao {
                    Etiqueta("Para fechar", null)
                    Paragrafo(f.texto)
                    Fontes(f.fontes)
                }
            }
        }
        if (e.corrigimos.isNotEmpty()) item {
            Cartao(fundo = c.alertaSuave) {
                Text("Corrigimos", style = Tipos.subtitulo(), color = c.tinta)
                e.corrigimos.forEach { Topico(br.contexto.app.Item(null, it)) }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Botao("Comentar a edição", TipoBotao.Leve) { nav.comentar(e.id, "edicao", "Edição inteira", null) }
                Botao("Achei tendencioso", TipoBotao.Leve) { nav.comentar(e.id, "edicao", "Edição inteira", "Achei tendencioso") }
                Botao("Compartilhar", TipoBotao.Leve) {
                    compartilhar(ctx, "Contexto de " + e.dia, e.trinta.firstOrNull() ?: "", SITE + "ler/" + e.id + "/")
                }
            }
        }
        e.parecer?.let { p ->
            item {
                Cartao(Modifier.clickable { parecerAberto = !parecerAberto }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Parecer da Ouvidoria", style = Tipos.subtitulo(), color = c.tinta)
                            Text(listOf(p.veredito, p.detalhe).filter { it.isNotBlank() }.joinToString(" "), style = Tipos.pequeno(), color = c.apagado)
                        }
                        Text(if (parecerAberto) "▲" else "▼", color = c.acento)
                    }
                    if (parecerAberto) {
                        if (p.resumo.isNotBlank()) Paragrafo(p.resumo)
                        if (p.pontos.isNotEmpty()) { Text("O que foi ajustado", style = Tipos.rotulo(), color = c.apagado); p.pontos.forEach { Topico(br.contexto.app.Item(null, it)) } }
                        if (p.equilibrio.isNotBlank()) { Text("Equilíbrio", style = Tipos.rotulo(), color = c.apagado); Paragrafo(p.equilibrio) }
                        if (p.foraDaEdicao.isNotEmpty()) { Text("Ficou de fora", style = Tipos.rotulo(), color = c.apagado); p.foraDaEdicao.forEach { Topico(br.contexto.app.Item(null, it)) } }
                    }
                }
            }
        }
        if (e.transp.isNotBlank()) item {
            Text(e.transp, style = Tipos.pequeno(), color = c.apagado, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private fun juntar(a: List<String>) = if (a.size < 2) a.joinToString("") else a.dropLast(1).joinToString(", ") + " nem de " + a.last()

private fun LazyListScope.secao(s: Secao, e: Edicao, catalogo: Catalogo, nav: Navegar) {
    item(key = "sec-" + s.categoria + s.nome) { Etiqueta(s.nome, catalogo.porId[s.categoria]) }
    items(s.notas, key = { "nota-" + s.categoria + it.id }) { n -> CartaoNota(n, e, nav) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CartaoNota(n: Nota, e: Edicao, nav: Navegar) {
    val c = LocalCores.current
    val ctx = LocalContext.current
    Cartao {
        Selo(n.selo)
        Text(n.titulo, style = Tipos.titulo(), color = c.tinta,
            modifier = Modifier.semantics { heading() }.then(if (n.completa != null) Modifier.clickable { nav.nota(e.id, n.id) } else Modifier))
        Paragrafo(n.texto)
        if (n.pq.isNotBlank()) PorQue(n.pq)
        Fontes(n.fontes)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Botao("Ouvir", TipoBotao.Leve, "▶") { Voz.tocar(n.titulo, trechosNota(n)) }
            if (n.completa != null) Botao("Notícia completa", TipoBotao.Cheio) { nav.nota(e.id, n.id) }
            if (n.contexto != null) Botao("Contexto", TipoBotao.Contorno) { nav.contexto(n.contexto) }
            Botao("Compartilhar", TipoBotao.Leve) {
                val url = SITE + "ler/" + e.id + "/" + (if (n.completa != null) n.id + "/" else "#nota-" + n.id)
                compartilhar(ctx, n.titulo, n.texto, url)
            }
        }
    }
}
