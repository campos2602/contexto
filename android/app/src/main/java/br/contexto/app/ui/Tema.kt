package br.contexto.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import br.contexto.app.R

/** Cores do guia de marca do Contexto (as mesmas do site). */
@Immutable
data class Cores(
    val fundo: Color, val superficie: Color, val tinta: Color, val apagado: Color, val linha: Color,
    val acento: Color, val acentoSuave: Color, val sobreAcento: Color, val barra: Color, val barraTinta: Color,
    val ok: Color, val okSuave: Color, val alerta: Color, val alertaSuave: Color, val escuro: Boolean,
)

val CoresClaras = Cores(
    fundo = Color(0xFFEEF1F3), superficie = Color(0xFFFFFFFF), tinta = Color(0xFF121821), apagado = Color(0xFF5A6573),
    linha = Color(0xFFD5DBE1), acento = Color(0xFF0B7A75), acentoSuave = Color(0xFFDCEFED), sobreAcento = Color.White,
    barra = Color(0xFF121821), barraTinta = Color(0xFFE8EDF2), ok = Color(0xFF1D7A4F), okSuave = Color(0xFFE0F1E8),
    alerta = Color(0xFF9A5B00), alertaSuave = Color(0xFFFBEFD9), escuro = false,
)

val CoresEscuras = Cores(
    fundo = Color(0xFF0C1117), superficie = Color(0xFF151C25), tinta = Color(0xFFE8EDF2), apagado = Color(0xFF9AA6B4),
    linha = Color(0xFF2A3441), acento = Color(0xFF3CC7BD), acentoSuave = Color(0xFF123331), sobreAcento = Color(0xFF0C1117),
    barra = Color(0xFF151C25), barraTinta = Color(0xFFE8EDF2), ok = Color(0xFF5FCF96), okSuave = Color(0xFF12301F),
    alerta = Color(0xFFE8AA3C), alertaSuave = Color(0xFF33270F), escuro = true,
)

val LocalCores = staticCompositionLocalOf { CoresClaras }
val LocalEscala = staticCompositionLocalOf { 1f }

@OptIn(ExperimentalTextApi::class)
private fun variavel(res: Int, peso: Int) =
    Font(res, FontWeight(peso), variationSettings = FontVariation.Settings(FontVariation.weight(peso)))

/** Títulos: Schibsted Grotesk. Texto: Source Serif 4. As duas com licença livre (SIL OFL). */
val Grotesca = FontFamily(
    variavel(R.font.schibsted_grotesk, 400), variavel(R.font.schibsted_grotesk, 500),
    variavel(R.font.schibsted_grotesk, 600), variavel(R.font.schibsted_grotesk, 700), variavel(R.font.schibsted_grotesk, 800),
)
val Serifada = FontFamily(
    variavel(R.font.source_serif, 400), variavel(R.font.source_serif, 600), variavel(R.font.source_serif, 700),
)

object Tipos {
    @Composable fun marca() = TextStyle(fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp * LocalEscala.current, letterSpacing = (-0.5).sp)
    @Composable fun titulo() = TextStyle(fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 21.sp * LocalEscala.current, lineHeight = 26.sp * LocalEscala.current)
    @Composable fun tituloGrande() = TextStyle(fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 26.sp * LocalEscala.current, lineHeight = 31.sp * LocalEscala.current)
    @Composable fun subtitulo() = TextStyle(fontFamily = Grotesca, fontWeight = FontWeight.SemiBold, fontSize = 17.sp * LocalEscala.current, lineHeight = 22.sp * LocalEscala.current)
    @Composable fun rotulo() = TextStyle(fontFamily = Grotesca, fontWeight = FontWeight.SemiBold, fontSize = 13.sp * LocalEscala.current, letterSpacing = 0.3.sp)
    @Composable fun corpo() = TextStyle(fontFamily = Serifada, fontSize = 17.sp * LocalEscala.current, lineHeight = 27.sp * LocalEscala.current)
    @Composable fun pequeno() = TextStyle(fontFamily = Serifada, fontSize = 14.sp * LocalEscala.current, lineHeight = 21.sp * LocalEscala.current)
}

@Composable
fun TemaContexto(tema: String, escala: Float, conteudo: @Composable () -> Unit) {
    val escuro = when (tema) { "claro" -> false; "escuro" -> true; else -> isSystemInDarkTheme() }
    val c = if (escuro) CoresEscuras else CoresClaras
    val esquema = if (escuro) darkColorScheme(
        primary = c.acento, onPrimary = c.sobreAcento, background = c.fundo, onBackground = c.tinta,
        surface = c.superficie, onSurface = c.tinta, surfaceVariant = c.acentoSuave, onSurfaceVariant = c.apagado,
        outline = c.linha, secondaryContainer = c.acentoSuave, onSecondaryContainer = c.tinta,
    ) else lightColorScheme(
        primary = c.acento, onPrimary = c.sobreAcento, background = c.fundo, onBackground = c.tinta,
        surface = c.superficie, onSurface = c.tinta, surfaceVariant = c.acentoSuave, onSurfaceVariant = c.apagado,
        outline = c.linha, secondaryContainer = c.acentoSuave, onSecondaryContainer = c.tinta,
    )
    val base = Typography()
    val tipografia = Typography(
        bodyLarge = base.bodyLarge.copy(fontFamily = Serifada),
        bodyMedium = base.bodyMedium.copy(fontFamily = Serifada),
        titleLarge = base.titleLarge.copy(fontFamily = Grotesca, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = Grotesca, fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontFamily = Grotesca, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = Grotesca),
    )
    CompositionLocalProvider(LocalCores provides c, LocalEscala provides escala) {
        MaterialTheme(colorScheme = esquema, typography = tipografia, content = conteudo)
    }
}
