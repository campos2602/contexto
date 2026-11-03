package br.contexto.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import br.contexto.app.ui.DialogoTexto
import br.contexto.app.ui.LocalCores
import br.contexto.app.ui.Navegar
import br.contexto.app.ui.TelaAjustes
import br.contexto.app.ui.TelaArquivo
import br.contexto.app.ui.TelaContexto
import br.contexto.app.ui.TelaEdicao
import br.contexto.app.ui.TelaNota
import br.contexto.app.ui.TemaContexto
import br.contexto.app.ui.Tipos

/** Pedido de comentário aberto na tela (edição, alvo, título e tipo sugerido). */
private data class PedidoComentario(val edicao: String, val alvo: String, val titulo: String, val tipo: String?)

class MainActivity : ComponentActivity() {
    private val leitor: Leitor by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Voz.iniciar(applicationContext)
        Lembrete.criarCanal(this)
        Lembrete.agendar(this)
        tratar(intent)
        setContent {
            val tema by leitor.prefs.temaFlow.collectAsState()
            val tamanho by leitor.prefs.tamanhoFlow.collectAsState()
            TemaContexto(tema, tamanho) { App(leitor) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tratar(intent)
    }

    /** O toque no lembrete abre o app e pede para tocar o resumo ou a edição inteira. */
    private fun tratar(i: Intent?) {
        if (i?.action == Lembrete.ACAO_OUVIR) {
            leitor.pedidoAudio.value = i.getStringExtra(Lembrete.EXTRA_ABRIR) ?: "resumo"
            i.action = null
        }
    }
}

private object Rota {
    const val HOJE = "hoje"
    const val ARQUIVO = "arquivo"
    const val AJUSTES = "ajustes"
}

@Composable
private fun App(leitor: Leitor) {
    val c = LocalCores.current
    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    val rota = entrada?.destination?.route
    val pedido by leitor.pedidoAudio.collectAsState()
    var comentario by remember { mutableStateOf<PedidoComentario?>(null) }

    // Um pedido de áudio vindo do lembrete sempre leva para a edição de hoje.
    LaunchedEffect(pedido) {
        if (pedido != null && rota != null && rota != Rota.HOJE) irPara(nav, Rota.HOJE)
    }

    val navegar = remember(nav) {
        Navegar(
            nota = { e, n -> nav.navigate("nota/" + Uri.encode(e) + "/" + Uri.encode(n)) },
            contexto = { s -> nav.navigate("contexto/" + Uri.encode(s)) },
            ajustes = { irPara(nav, Rota.AJUSTES) },
            comentar = { e, a, t, tipo -> comentario = PedidoComentario(e, a, t, tipo) },
        )
    }

    Column(Modifier.fillMaxSize().background(c.fundo)) {
        val interna = rota != null && rota !in listOf(Rota.HOJE, Rota.ARQUIVO, Rota.AJUSTES)
        if (interna) BarraVoltar { nav.popBackStack() }
        else Box(Modifier.windowInsetsPadding(WindowInsets.statusBars))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val rodape = PaddingValues(0.dp)
            NavHost(nav, startDestination = Rota.HOJE) {
                composable(Rota.HOJE) { TelaEdicao(leitor, null, navegar, true, rodape) }
                composable(Rota.ARQUIVO) {
                    TelaArquivo(leitor, { id -> nav.navigate("edicao/" + Uri.encode(id)) }, rodape)
                }
                composable(Rota.AJUSTES) { TelaAjustes(leitor, rodape) }
                composable("edicao/{id}") { b ->
                    TelaEdicao(leitor, b.arguments?.getString("id"), navegar, false, rodape)
                }
                composable("nota/{edicao}/{nota}") { b ->
                    TelaNota(leitor, b.arguments?.getString("edicao") ?: "", b.arguments?.getString("nota") ?: "", navegar, rodape)
                }
                composable("contexto/{slug}") { b ->
                    TelaContexto(leitor, b.arguments?.getString("slug") ?: "", rodape)
                }
            }
        }

        MiniPlayer()
        BarraInferior(rota) { irPara(nav, it) }
    }

    comentario?.let { p ->
        DialogoTexto(
            titulo = "Comentar: " + p.titulo,
            dica = "Escreva aqui. Seu comentário vai para a equipe que revisa o Contexto.",
            aoFechar = { comentario = null },
            aoEnviar = { texto -> Comentarios.enviar(p.edicao, p.alvo, p.titulo, p.tipo ?: Comentarios.TIPOS.first(), texto) },
            tipos = Comentarios.TIPOS,
            tipoInicial = p.tipo,
            aoEnviarTipo = { tipo, texto -> Comentarios.enviar(p.edicao, p.alvo, p.titulo, tipo, texto) },
        )
    }
}

private fun irPara(nav: NavHostController, rota: String) {
    nav.navigate(rota) {
        popUpTo(Rota.HOJE) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BarraVoltar(voltar: () -> Unit) {
    val c = LocalCores.current
    Row(
        Modifier.fillMaxWidth().background(c.barra).windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "←  Voltar", style = Tipos.rotulo(), color = c.barraTinta,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = voltar)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun BarraInferior(rota: String?, ir: (String) -> Unit) {
    val c = LocalCores.current
    val abas = listOf(Rota.HOJE to "Hoje", Rota.ARQUIVO to "Arquivo", Rota.AJUSTES to "Ajustes")
    Row(
        Modifier.fillMaxWidth().background(c.barra).windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        abas.forEach { (r, nome) ->
            val ativa = rota == r || (r == Rota.ARQUIVO && rota == "edicao/{id}")
            Text(
                nome, style = Tipos.rotulo(),
                color = if (ativa) c.sobreAcento else c.barraTinta,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (ativa) c.acento else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(role = Role.Tab) { ir(r) }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun MiniPlayer() {
    val c = LocalCores.current
    val v by Voz.estado.collectAsState()
    if (!v.tocando) return
    Row(
        Modifier.fillMaxWidth().background(c.superficie).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(v.titulo, style = Tipos.rotulo(), color = c.tinta, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Trecho " + (v.indice + 1) + " de " + v.total, style = Tipos.pequeno(), color = c.apagado)
        }
        BotaoPlayer("⏮", "Voltar um trecho") { Voz.pular(-1) }
        if (v.pausado) BotaoPlayer("▶", "Continuar") { Voz.continuar() }
        else BotaoPlayer("⏸", "Pausar") { Voz.pausar() }
        BotaoPlayer("⏭", "Avançar um trecho") { Voz.pular(1) }
        BotaoPlayer(velocidadeTexto(v.velocidade), "Mudar a velocidade") { Voz.mudarVelocidade() }
        BotaoPlayer("■", "Parar") { Voz.parar() }
    }
}

private fun velocidadeTexto(v: Float): String =
    (if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString().replace('.', ',')) + "×"

@Composable
private fun BotaoPlayer(simbolo: String, descricao: String, aoTocar: () -> Unit) {
    val c = LocalCores.current
    Text(
        simbolo, style = Tipos.rotulo(), color = c.acento,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = aoTocar)
            .semantics { contentDescription = descricao }
            .padding(horizontal = 10.dp, vertical = 10.dp)
    )
}
