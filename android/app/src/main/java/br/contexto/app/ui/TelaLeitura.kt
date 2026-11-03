package br.contexto.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.contexto.app.Carga
import br.contexto.app.Item
import br.contexto.app.Leitor
import br.contexto.app.SITE
import br.contexto.app.Voz
import br.contexto.app.trechosCompleta
import br.contexto.app.trechosContexto

private fun pad(rodape: PaddingValues) =
    PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = rodape.calculateBottomPadding() + 24.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelaNota(leitor: Leitor, edicaoId: String, notaId: String, nav: Navegar, rodape: PaddingValues) {
    val c = LocalCores.current
    val ctx = LocalContext.current
    val carga by remember(edicaoId) { leitor.edicao(edicaoId) }.collectAsState()
    val catalogo by leitor.catalogo.collectAsState()
    val e = (carga as? Carga.Pronto)?.valor
    val secao = e?.secoes?.firstOrNull { s -> s.notas.any { it.id == notaId } }
    val n = secao?.notas?.firstOrNull { it.id == notaId }
    val k = n?.completa

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad(rodape), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (e == null || secao == null || n == null) {
            item { if (carga is Carga.Falhou) Aviso((carga as Carga.Falhou).mensagem) else Carregando() }
            return@LazyColumn
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row { Etiqueta(secao.nome, catalogo.porId[secao.categoria]); androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp)); Selo(k?.selo ?: n.selo) }
                Text(n.titulo, style = Tipos.tituloGrande(), color = c.tinta, modifier = Modifier.semantics { heading() })
                if (!k?.fina.isNullOrBlank()) Text(k!!.fina, style = Tipos.subtitulo(), color = c.apagado)
                Text(listOfNotNull(e.dia, k?.atualizada?.takeIf { it.isNotBlank() }?.let { "atualizada em $it" }).joinToString(" · "),
                    style = Tipos.pequeno(), color = c.apagado)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Botao("Ouvir a notícia", TipoBotao.Cheio, "▶") { Voz.tocar(n.titulo, trechosCompleta(n)) }
                    if (n.contexto != null) Botao("Contexto", TipoBotao.Contorno) { nav.contexto(n.contexto) }
                    Botao("Compartilhar", TipoBotao.Leve) { compartilhar(ctx, n.titulo, n.texto, SITE + "ler/" + e.id + "/" + n.id + "/") }
                }
            }
        }
        if (k == null) {
            item { Paragrafo(n.texto) }
            if (n.pq.isNotBlank()) item { PorQue(n.pq) }
        } else {
            item { Paragrafo(k.fato) }
            items(k.blocos) { b ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (b.h.isNotBlank()) Text(b.h, style = Tipos.titulo(), color = c.tinta, modifier = Modifier.semantics { heading() })
                    b.p.forEach { Paragrafo(it) }
                    b.li.forEach { Topico(it) }
                }
            }
        }
        item { Divisoria() }
        item { Fontes(k?.fontes?.ifEmpty { n.fontes } ?: n.fontes) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Botao("Comentar", TipoBotao.Leve) { nav.comentar(e.id, n.id, n.titulo, null) }
                Botao("Achei tendencioso", TipoBotao.Leve) { nav.comentar(e.id, n.id, n.titulo, "Achei tendencioso") }
                Botao("Erro de fato", TipoBotao.Leve) { nav.comentar(e.id, n.id, n.titulo, "Erro de fato") }
            }
        }
    }
}

@Composable
fun TelaContexto(leitor: Leitor, slug: String, rodape: PaddingValues) {
    val c = LocalCores.current
    val carga by remember(slug) { leitor.contexto(slug) }.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad(rodape), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val x = carga
        if (x !is Carga.Pronto) {
            item { if (x is Carga.Falhou) Aviso(x.mensagem) else Carregando() }
            return@LazyColumn
        }
        val k = x.valor
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row { Etiqueta("Contexto", null); androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp)); Selo(k.selo) }
                Text(k.titulo, style = Tipos.tituloGrande(), color = c.tinta, modifier = Modifier.semantics { heading() })
                if (k.atualizado.isNotBlank()) Text("Atualizado em " + k.atualizado, style = Tipos.pequeno(), color = c.apagado)
                if (k.frase.isNotBlank()) Text(k.frase, style = Tipos.subtitulo(), color = c.tinta)
                Botao("Ouvir o Contexto", TipoBotao.Cheio, "▶") { Voz.tocar(k.titulo, trechosContexto(k)) }
            }
        }
        if (k.linha.isNotEmpty()) item {
            Cartao {
                Text("Linha do tempo", style = Tipos.titulo(), color = c.tinta, modifier = Modifier.semantics { heading() })
                k.linha.forEach { m ->
                    Column {
                        Text(m.d, style = Tipos.rotulo(), color = c.acento)
                        Paragrafo(m.t)
                    }
                }
            }
        }
        bloco("Termos para entender", k.termos)
        bloco("Quem é quem", k.quem)
        bloco("O que está em disputa", k.disputa)
        if (k.observar.isNotEmpty()) bloco("O que observar", k.observar.map { Item(null, it) })
        item { Divisoria() }
        item { Fontes(k.fontes) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.bloco(titulo: String, lista: List<Item>) {
    if (lista.isEmpty()) return
    item {
        Cartao {
            Text(titulo, style = Tipos.titulo(), color = LocalCores.current.tinta, modifier = Modifier.semantics { heading() })
            lista.forEach { Topico(it) }
        }
    }
}

@Composable
fun TelaArquivo(leitor: Leitor, abrir: (String) -> Unit, rodape: PaddingValues) {
    val c = LocalCores.current
    val indice by leitor.indice.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad(rodape), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Arquivo", style = Tipos.marca(), color = c.tinta, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
            Text("Todas as edições do Contexto. As que você já abriu ficam guardadas para ler sem internet.",
                style = Tipos.pequeno(), color = c.apagado)
        }
        when (val k = indice) {
            is Carga.Carregando -> item { Carregando() }
            is Carga.Falhou -> item { Aviso(k.mensagem, "Tentar de novo") { leitor.atualizar(true) } }
            is Carga.Pronto -> items(k.valor, key = { it.id }) { r ->
                Cartao(Modifier.clickable { abrir(r.id) }) {
                    Text(r.dia.ifBlank { r.data }, style = Tipos.subtitulo(), color = c.tinta)
                }
            }
        }
    }
}
