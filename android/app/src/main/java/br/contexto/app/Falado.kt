package br.contexto.app

/**
 * Adapta o texto escrito para ser falado, sem mudar o conteúdo. Mesmas regras de ler/falado.js,
 * para o app e o site falarem igual. Ex.: "na sexta (30)" -> "na sexta, dia 30";
 * "Flávio Bolsonaro (PL-RJ)" -> "Flávio Bolsonaro, do PL do Rio de Janeiro,"; "US$ 6,3 bilhões" -> "6,3 bilhões de dólares".
 */
object Falado {
    private val UF = mapOf(
        "AC" to "do Acre", "AL" to "de Alagoas", "AP" to "do Amapá", "AM" to "do Amazonas", "BA" to "da Bahia",
        "CE" to "do Ceará", "DF" to "do Distrito Federal", "ES" to "do Espírito Santo", "GO" to "de Goiás",
        "MA" to "do Maranhão", "MT" to "de Mato Grosso", "MS" to "de Mato Grosso do Sul", "MG" to "de Minas Gerais",
        "PA" to "do Pará", "PB" to "da Paraíba", "PR" to "do Paraná", "PE" to "de Pernambuco", "PI" to "do Piauí",
        "RJ" to "do Rio de Janeiro", "RN" to "do Rio Grande do Norte", "RS" to "do Rio Grande do Sul",
        "RO" to "de Rondônia", "RR" to "de Roraima", "SC" to "de Santa Catarina", "SP" to "de São Paulo",
        "SE" to "de Sergipe", "TO" to "do Tocantins",
    )
    private val PARTIDOS = listOf("PL", "PT", "PP", "PSD", "PSDB", "MDB", "PSB", "PDT", "PSOL", "PSol", "PCdoB", "PV", "Novo",
        "Podemos", "Avante", "Solidariedade", "Cidadania", "Rede", "Republicanos", "União Brasil", "União", "Agir", "DC", "PRD",
        "PMB", "Mobiliza", "PRTB", "PCO", "PSTU", "UP")
    private val ORDINAIS = listOf("", "primeiro", "segundo", "terceiro", "quarto", "quinto", "sexto", "sétimo", "oitavo", "nono", "décimo")
    private val SIGLAS = mapOf("EUA" to "Estados Unidos", "nº" to "número", "Nº" to "Número")
    private const val DIAS = "domingo|segunda|terça|quarta|quinta|sexta|sábado"

    private val reDia = Regex("\\b($DIAS)(-feira)?\\s?\\((\\d{1,2})\\)", RegexOption.IGNORE_CASE)
    private val rePartido = Regex("\\s?\\((" + PARTIDOS.joinToString("|") { Regex.escape(it) } + ")(?:\\s?[-–/]\\s?([A-Z]{2}))?\\)")
    private val reUf = Regex("\\s?\\(([A-Z]{2})\\)")
    private const val ESCALA = "(mil|milhão|milhões|bilhão|bilhões|trilhão|trilhões)"
    private val moedas = listOf(
        Triple("US\\$", "dólar", "dólares"), Triple("R\\$", "real", "reais"),
        Triple("€", "euro", "euros"), Triple("£", "libra", "libras"),
    ).map { (s, n, p) -> Triple(Regex(s + "\\s?(\\d[\\d.]*(?:,\\d+)?)(?:\\s" + ESCALA + ")?"), n, p) }
    private val reCentavos = Regex("^(\\d{1,3}(?:\\.\\d{3})*|\\d+),(\\d{2})$")

    private fun emLugar(de: String) = de.replaceFirst(Regex("^do "), "no ").replaceFirst(Regex("^da "), "na ").replaceFirst(Regex("^de "), "em ")

    private fun moeda(valor: String, escala: String?, nome: String, plural: String): String {
        if (!escala.isNullOrEmpty()) return "$valor $escala de $plural"
        reCentavos.matchEntire(valor)?.let { m ->
            val inteiro = m.groupValues[1]; val cent = m.groupValues[2].toInt()
            return inteiro + " " + (if (inteiro == "1") nome else plural) +
                (if (cent > 0) " e $cent " + (if (cent == 1) "centavo" else "centavos") else "")
        }
        return "$valor " + (if (valor == "1") nome else plural)
    }

    fun falar(entrada: String): String {
        var t = entrada
        t = reDia.replace(t) { m -> m.groupValues[1] + m.groupValues[2] + ", dia " + m.groupValues[3] + "," }
        t = rePartido.replace(t) { m ->
            val uf = m.groupValues[2]
            ", do " + m.groupValues[1] + (UF[uf]?.let { " $it" } ?: "") + ","
        }
        t = reUf.replace(t) { m -> UF[m.groupValues[1]]?.let { ", " + emLugar(it) + "," } ?: m.value }
        for ((re, nome, plural) in moedas) t = re.replace(t) { m -> moeda(m.groupValues[1], m.groupValues[2], nome, plural) }
        t = Regex("\\b(\\d{1,2})h(\\d{2})?\\b").replace(t) { m ->
            val h = m.groupValues[1]; val min = m.groupValues[2]
            h + (if (h == "1") " hora" else " horas") + (if (min.isNotEmpty() && min != "00") " e " + min.toInt() else "")
        }
        t = Regex("(^|[\\s(])[−-](\\d[\\d.,]*)\\s?%").replace(t, "$1menos $2%")
        t = Regex("(^|[\\s(])\\+(\\d[\\d.,]*)\\s?%").replace(t, "$1mais $2%")
        t = Regex("(\\d)\\s?%").replace(t, "$1 por cento")
        t = Regex("\\b(\\d{1,2})[º°]").replace(t) { m -> ORDINAIS.getOrNull(m.groupValues[1].toInt())?.takeIf { it.isNotEmpty() } ?: m.value }
        t = Regex("\\b(\\d+)\\s?km\\b").replace(t, "$1 quilômetros")
        for ((s, v) in SIGLAS) t = Regex("(^|[^\\wÀ-ú])" + Regex.escape(s) + "(?![\\wÀ-ú])").replace(t) { m -> m.groupValues[1] + v }
        return t.replace(Regex(",\\s*([,.;:!?)])"), "$1").replace(Regex("\\(\\s*,\\s*"), "(")
            .replace(Regex("\\s+,"), ",").replace(Regex("\\s{2,}"), " ").trim()
    }

    /** Frases de até ~220 caracteres, para poder avançar e voltar. */
    fun partir(texto: String): List<String> {
        val frases = Regex("[^.!?…]+[.!?…]+[\"”')]*\\s*|[^.!?…]+$").findAll(texto).map { it.value.trim() }.filter { it.isNotEmpty() }
        val out = mutableListOf<String>()
        var atual = ""
        for (f in frases) {
            if (atual.isNotEmpty() && atual.length + f.length + 1 > 220) { out += atual; atual = f }
            else atual = if (atual.isEmpty()) f else "$atual $f"
        }
        if (atual.isNotEmpty()) out += atual
        return out
    }
}
