// Adapta o texto escrito do Contexto para ser falado em voz alta, sem mudar o conteúdo.
// Ex.: "na sexta (30)" vira "na sexta, dia 30"; "Flávio Bolsonaro (PL-RJ)" vira "Flávio Bolsonaro, do PL do Rio de Janeiro,";
// "US$ 6,3 bilhões" vira "6,3 bilhões de dólares"; "17h" vira "17 horas".
// Usado no app (ler/ouvir.js) e na gravação dos MP3 (ferramentas/audio.js), para os dois falarem igual.
(function (raiz) {
  "use strict";

  var UF = {
    AC: "do Acre", AL: "de Alagoas", AP: "do Amapá", AM: "do Amazonas", BA: "da Bahia", CE: "do Ceará", DF: "do Distrito Federal",
    ES: "do Espírito Santo", GO: "de Goiás", MA: "do Maranhão", MT: "de Mato Grosso", MS: "de Mato Grosso do Sul", MG: "de Minas Gerais",
    PA: "do Pará", PB: "da Paraíba", PR: "do Paraná", PE: "de Pernambuco", PI: "do Piauí", RJ: "do Rio de Janeiro", RN: "do Rio Grande do Norte",
    RS: "do Rio Grande do Sul", RO: "de Rondônia", RR: "de Roraima", SC: "de Santa Catarina", SP: "de São Paulo", SE: "de Sergipe", TO: "do Tocantins"
  };
  var PARTIDOS = ["PL", "PT", "PP", "PSD", "PSDB", "MDB", "PSB", "PDT", "PSOL", "PSol", "PCdoB", "PV", "Novo", "Podemos", "Avante", "Solidariedade",
    "Cidadania", "Rede", "Republicanos", "União Brasil", "União", "Agir", "DC", "PRD", "PMB", "Mobiliza", "PRTB", "PCO", "PSTU", "UP"];
  var ORDINAIS = ["", "primeiro", "segundo", "terceiro", "quarto", "quinto", "sexto", "sétimo", "oitavo", "nono", "décimo"];
  var SIGLAS = { EUA: "Estados Unidos", "nº": "número", "Nº": "Número" };

  function emLugar(de) { return de.replace(/^do /, "no ").replace(/^da /, "na ").replace(/^de /, "em "); }
  function esc(s) { return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"); }
  var RE_PARTIDO = new RegExp("\\s?\\((" + PARTIDOS.map(esc).join("|") + ")(?:\\s?[-–/]\\s?([A-Z]{2}))?\\)", "g");
  var DIAS = "domingo|segunda|terça|quarta|quinta|sexta|sábado";

  function moeda(valor, escala, nome, plural) {
    if (escala) return valor + " " + escala + " de " + plural;          // US$ 6,3 bilhões -> 6,3 bilhões de dólares
    var m = /^(\d{1,3}(?:\.\d{3})*|\d+),(\d{2})$/.exec(valor);         // R$ 5,34 -> 5 reais e 34 centavos
    if (m) { var inteiro = m[1], cent = +m[2];
      return inteiro + " " + (inteiro === "1" ? nome : plural) + (cent ? " e " + cent + (cent === 1 ? " centavo" : " centavos") : ""); }
    return valor + " " + (valor === "1" ? nome : plural);              // US$ 2.899 -> 2.899 dólares; R$ 5,349 -> 5,349 reais
  }

  function falado(t) {
    t = String(t || "");
    // Dia da semana com o dia do mês entre parênteses: "sexta (30)" -> "sexta, dia 30"
    t = t.replace(new RegExp("\\b(" + DIAS + ")(-feira)?\\s?\\((\\d{1,2})\\)", "gi"), function (m, d, f, n) { return d + (f || "") + ", dia " + n + ","; });
    // Partido e estado: "Lula (PT)" -> "Lula, do PT,"; "(PL-RJ)" -> ", do PL do Rio de Janeiro,"
    t = t.replace(RE_PARTIDO, function (m, p, uf) { return ", do " + p + (uf && UF[uf] ? " " + UF[uf] : "") + ","; });
    // Estado depois de cidade: "Tabatinga (AM)" -> "Tabatinga, no Amazonas,"
    t = t.replace(/\s?\(([A-Z]{2})\)/g, function (m, uf) { return UF[uf] ? ", " + emLugar(UF[uf]) + "," : m; });
    // Dinheiro
    var ESC = "(mil|milhão|milhões|bilhão|bilhões|trilhão|trilhões)";
    [["US\\$", "dólar", "dólares"], ["R\\$", "real", "reais"], ["€", "euro", "euros"], ["£", "libra", "libras"]].forEach(function (c) {
      t = t.replace(new RegExp(c[0] + "\\s?(\\d[\\d.]*(?:,\\d+)?)(?:\\s" + ESC + ")?", "g"), function (m, v, e) { return moeda(v, e, c[1], c[2]); });
    });
    // Horas: "17h" -> "17 horas"; "10h20" -> "10 horas e 20"; "1h" -> "1 hora"
    t = t.replace(/\b(\d{1,2})h(\d{2})?\b/g, function (m, h, min) { return h + (h === "1" ? " hora" : " horas") + (min ? (min === "00" ? "" : " e " + +min) : ""); });
    // Porcentagem com sinal: "−9,88%" -> "menos 9,88 por cento"
    t = t.replace(/(^|[\s(])[−-](\d[\d.,]*)\s?%/g, "$1menos $2%").replace(/(^|[\s(])\+(\d[\d.,]*)\s?%/g, "$1mais $2%").replace(/(\d)\s?%/g, "$1 por cento");
    // Ordinais pequenos: "1º turno" -> "primeiro turno"
    t = t.replace(/\b(\d{1,2})[º°]/g, function (m, n) { return ORDINAIS[+n] || n + "º"; });
    t = t.replace(/\b(\d+)\s?km\b/g, "$1 quilômetros");
    Object.keys(SIGLAS).forEach(function (s) { t = t.replace(new RegExp("(^|[^\\wÀ-ú])" + esc(s) + "(?![\\wÀ-ú])", "g"), "$1" + SIGLAS[s]); });
    // Arruma vírgulas que sobraram das trocas: ",," / ", ." / "(, " etc.
    return t.replace(/,\s*([,.;:!?)])/g, "$1").replace(/\(\s*,\s*/g, "(").replace(/\s+,/g, ",").replace(/\s{2,}/g, " ").trim();
  }

  if (typeof module === "object" && module.exports) module.exports = falado;
  else raiz.ContextoFalado = falado;
})(typeof window !== "undefined" ? window : this);
