package br.contexto.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import br.contexto.app.Categoria
import br.contexto.app.Fonte
import br.contexto.app.Item

@Composable
fun Cartao(modifier: Modifier = Modifier, fundo: Color? = null, conteudo: @Composable () -> Unit) {
    val c = LocalCores.current
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(fundo ?: c.superficie)
            .border(1.dp, c.linha, RoundedCornerShape(14.dp))
            .padding(18.dp)
    ) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { conteudo() } }
}

@Composable
fun Etiqueta(texto: String, cat: Categoria?) {
    val c = LocalCores.current
    val fundo = cat?.let { Color(if (c.escuro) it.corEscura else it.corClara) } ?: c.acentoSuave
    Text(
        texto.uppercase(), style = Tipos.rotulo(), color = c.tinta,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(fundo).padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics { heading() }
    )
}

@Composable
fun Selo(selo: String) {
    if (selo.isBlank()) return
    val c = LocalCores.current
    val ressalva = selo.contains("ressalva", ignoreCase = true)
    Text(
        (if (ressalva) "⚠ " else "✓ ") + selo, style = Tipos.rotulo(),
        color = if (ressalva) c.alerta else c.ok,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (ressalva) c.alertaSuave else c.okSuave)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@Composable
fun Paragrafo(texto: String, modifier: Modifier = Modifier) {
    Text(texto, style = Tipos.corpo(), color = LocalCores.current.tinta, modifier = modifier)
}

/** "Por que importa: ..." com o rótulo em negrito. */
@Composable
fun PorQue(texto: String) {
    val c = LocalCores.current
    Text(buildAnnotatedString {
        withStyle(SpanStyle(fontFamily = Grotesca, fontWeight = FontWeight.Bold, color = c.acento)) { append("Por que importa: ") }
        append(texto)
    }, style = Tipos.corpo(), color = c.tinta)
}

@Composable
fun Topico(item: Item) {
    val c = LocalCores.current
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.padding(top = 11.dp, end = 10.dp).size(6.dp).clip(CircleShape).background(c.acento))
        Text(buildAnnotatedString {
            if (!item.b.isNullOrBlank()) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(item.b) }
                append(" ")
            }
            append(item.t)
        }, style = Tipos.corpo(), color = c.tinta)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Fontes(lista: List<Fonte>, rotulo: String = "Fontes") {
    if (lista.isEmpty()) return
    val c = LocalCores.current
    val abrir = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(rotulo, style = Tipos.rotulo(), color = c.apagado)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lista.forEach { f ->
                Text(
                    f.t + " ↗", style = Tipos.pequeno(), color = c.acento,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .clickable(role = Role.Button) { runCatching { abrir.openUri(f.u) } }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                )
            }
        }
    }
}

enum class TipoBotao { Cheio, Contorno, Leve }

@Composable
fun Botao(texto: String, tipo: TipoBotao = TipoBotao.Contorno, icone: String? = null, aoTocar: () -> Unit) {
    val c = LocalCores.current
    val forma = RoundedCornerShape(50)
    val (fundo, tinta) = when (tipo) {
        TipoBotao.Cheio -> c.acento to c.sobreAcento
        TipoBotao.Contorno -> Color.Transparent to c.acento
        TipoBotao.Leve -> c.acentoSuave to c.tinta
    }
    Row(
        Modifier.clip(forma).background(fundo)
            .then(if (tipo == TipoBotao.Contorno) Modifier.border(1.5.dp, c.acento, forma) else Modifier)
            .clickable(role = Role.Button, onClick = aoTocar)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icone != null) { Text(icone, color = tinta, style = Tipos.rotulo()); Spacer(Modifier.width(6.dp)) }
        Text(texto, color = tinta, style = Tipos.rotulo())
    }
}

@Composable
fun Carregando() {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = LocalCores.current.acento)
    }
}

@Composable
fun Aviso(texto: String, acao: String? = null, aoTocar: (() -> Unit)? = null) {
    Cartao {
        Paragrafo(texto)
        if (acao != null && aoTocar != null) Botao(acao, TipoBotao.Cheio, aoTocar = aoTocar)
    }
}

@Composable
fun Divisoria() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalCores.current.linha))
}

fun compartilhar(ctx: Context, titulo: String, texto: String, url: String) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, titulo)
        .putExtra(Intent.EXTRA_TEXT, "$titulo\n\n$texto\n\n$url")
    ctx.startActivity(Intent.createChooser(i, "Compartilhar").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
