// Grava o áudio da edição com a voz da ElevenLabs, trecho por trecho, em audio/<id>/.
// Cada trecho é exatamente um dos textos que o app e o site leem em voz alta (título, texto,
// "Por que importa: ...", frases do "Em 30 segundos", nome da seção...). Assim o leitor monta a
// própria playlist (com as seções que escolheu) e, se faltar um trecho, usa a voz do celular só nele.
//
// Uso:  node ferramentas/audio.js [id-da-edicao]      (sem id: a edição mais recente)
//       node ferramentas/audio.js --amostras          (uma amostra curta de cada voz da conta)
// Precisa de ELEVENLABS_API_KEY. Opcionais: ELEVENLABS_VOZ (id da voz), ELEVENLABS_MODELO.
"use strict";
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const falado = require("../ler/falado.js");

const RAIZ = path.join(__dirname, "..");
const CHAVE = ["ELEVENLABS_API_KEY", "ELEVEN_LABS_API_KEY", "ELEVENLABS_KEY", "ELEVEN_API_KEY", "XI_API_KEY"]
  .map((n) => process.env[n]).find(Boolean) || "";
const VOZ = process.env.ELEVENLABS_VOZ || "bJrNspxJVFovUxNBQ0wh"; // Marcelo Costa (pt-BR), escolhida pelo Luiz
const MODELO = process.env.ELEVENLABS_MODELO || "eleven_v4_turbo"; // metade do custo: cabe no plano Creator
const FORMATO = "mp3_44100_64";
const MANTER_DIAS = 21; // áudios mais velhos saem do site (continuam no histórico do git)
const AJUSTES = { stability: 0.55, similarity_boost: 0.75, style: 0.15, use_speaker_boost: true, speed: 1.0 };

function ler(p) { return JSON.parse(fs.readFileSync(path.join(RAIZ, p), "utf8")); }
function sem(x) { return (x || "").trim(); }

/** Os mesmos textos que o app (Leitor.kt) e o site (ler/ouvir.js) mandam para a voz. */
function trechos(e) {
  const t = [];
  const add = (s) => { s = sem(s); if (s && !t.includes(s)) t.push(s); };
  add("Contexto de " + e.dia + ".");
  add("Contexto de " + e.dia + ". Em 30 segundos.");
  add(e.abre);
  add("Em 30 segundos.");
  (e.trinta || []).forEach(add);
  (e.secoes || []).forEach((s) => {
    add(s.nome + ".");
    (s.notas || []).forEach((n) => {
      add(n.titulo);
      add(n.texto);
      if (sem(n.pq)) add("Por que importa: " + n.pq);
      add(sem(n.titulo).replace(/\.+$/, "") + ".");
    });
  });
  if (e.numero && sem(e.numero.texto)) add("Número do dia: " + (e.numero.valor || "") + ". " + e.numero.texto);
  if (e.fechar && sem(e.fechar.texto)) add("Para fechar. " + e.fechar.texto);
  return t;
}

function hash(s) { return crypto.createHash("sha1").update(VOZ + "|" + MODELO + "|" + JSON.stringify(AJUSTES) + "|" + s).digest("hex").slice(0, 12); }

async function gravar(texto, anterior, seguinte) {
  const url = "https://api.elevenlabs.io/v1/text-to-speech/" + VOZ + "?output_format=" + FORMATO;
  for (let tentativa = 1; tentativa <= 4; tentativa++) {
    const r = await fetch(url, {
      method: "POST",
      headers: { "xi-api-key": CHAVE, "Content-Type": "application/json", Accept: "audio/mpeg" },
      body: JSON.stringify({
        text: texto, model_id: MODELO, voice_settings: AJUSTES,
        previous_text: anterior || undefined, next_text: seguinte || undefined,
      }),
    });
    if (r.ok) return Buffer.from(await r.arrayBuffer());
    const erro = await r.text();
    if (r.status === 401 || r.status === 402 || /quota/i.test(erro)) throw new Error("ElevenLabs recusou (" + r.status + "): " + erro.slice(0, 300));
    console.log("Tentativa " + tentativa + " falhou (" + r.status + "): " + erro.slice(0, 200));
    await new Promise((ok) => setTimeout(ok, 2000 * tentativa));
  }
  throw new Error("ElevenLabs não respondeu depois de 4 tentativas.");
}

async function edicao(id) {
  const e = ler("edicoes/" + id + ".json");
  if (e.status === "retida") { console.log("Edição " + id + " retida pelo editor-chefe: sem áudio."); return; }
  const pasta = path.join(RAIZ, "audio", id);
  fs.mkdirSync(pasta, { recursive: true });
  const lista = trechos(e);
  const clipes = [];
  let gastos = 0, novos = 0;
  for (let i = 0; i < lista.length; i++) {
    const escrito = lista[i];
    const fala = falado(escrito);
    const arquivo = hash(fala) + ".mp3";
    if (!fs.existsSync(path.join(pasta, arquivo))) {
      const mp3 = await gravar(fala, lista[i - 1] && falado(lista[i - 1]), lista[i + 1] && falado(lista[i + 1]));
      fs.writeFileSync(path.join(pasta, arquivo), mp3);
      gastos += fala.length; novos++;
    }
    clipes.push({ texto: escrito, arquivo });
  }
  // Tira MP3 de versões antigas do texto (edição corrigida depois de gravada).
  const usados = new Set(clipes.map((c) => c.arquivo));
  fs.readdirSync(pasta).filter((f) => f.endsWith(".mp3") && !usados.has(f)).forEach((f) => fs.unlinkSync(path.join(pasta, f)));
  fs.writeFileSync(path.join(pasta, "audio.json"),
    JSON.stringify({ edicao: id, voz: VOZ, modelo: MODELO, gerado: new Date().toISOString(), clipes }, null, 1) + "\n");
  console.log("Edição " + id + ": " + clipes.length + " trechos, " + novos + " gravados agora (" + gastos + " caracteres).");
}

function limpar() {
  const pasta = path.join(RAIZ, "audio");
  if (!fs.existsSync(pasta)) return;
  const limite = Date.now() - MANTER_DIAS * 86400e3;
  fs.readdirSync(pasta).filter((d) => /^\d{4}-\d{2}-\d{2}$/.test(d) && Date.parse(d + "T12:00:00Z") < limite)
    .forEach((d) => { fs.rmSync(path.join(pasta, d), { recursive: true }); console.log("Removido audio/" + d); });
}

async function amostras() {
  const r = await fetch("https://api.elevenlabs.io/v1/voices", { headers: { "xi-api-key": CHAVE } });
  if (!r.ok) throw new Error("Não consegui listar as vozes: " + r.status + " " + (await r.text()).slice(0, 200));
  const vozes = (await r.json()).voices || [];
  const texto = falado("Bom dia. Este é o Contexto de hoje: em cinco minutos, o que importa e por quê. " +
    "O Banco Central manteve a Selic em 15% ao ano, e o dólar fechou a R$ 5,34.");
  const pasta = path.join(RAIZ, "audio", "amostras");
  fs.mkdirSync(pasta, { recursive: true });
  const lista = [];
  for (const v of vozes.slice(0, 12)) {
    const nome = v.name.normalize("NFD").replace(/[^\w]+/g, "-").toLowerCase();
    const mp3 = await gravarCom(v.voice_id, texto);
    fs.writeFileSync(path.join(pasta, nome + ".mp3"), mp3);
    lista.push({ nome: v.name, id: v.voice_id, arquivo: nome + ".mp3", sotaque: (v.labels || {}).accent || "", idioma: (v.labels || {}).language || "" });
    console.log("Amostra: " + v.name + " (" + v.voice_id + ")");
  }
  fs.writeFileSync(path.join(pasta, "vozes.json"), JSON.stringify(lista, null, 1) + "\n");
}

async function gravarCom(voz, texto) {
  const r = await fetch("https://api.elevenlabs.io/v1/text-to-speech/" + voz + "?output_format=" + FORMATO, {
    method: "POST", headers: { "xi-api-key": CHAVE, "Content-Type": "application/json" },
    body: JSON.stringify({ text: texto, model_id: MODELO, voice_settings: AJUSTES }),
  });
  if (!r.ok) throw new Error("Amostra falhou: " + r.status + " " + (await r.text()).slice(0, 200));
  return Buffer.from(await r.arrayBuffer());
}

(async () => {
  if (process.argv[2] === "--contar") { // quantos caracteres cada edição gasta, sem gravar nada
    const id = process.argv[3] || ler("edicoes/index.json")[0].id;
    const l = trechos(ler("edicoes/" + id + ".json"));
    console.log(id + ": " + l.length + " trechos, " + l.reduce((n, t) => n + falado(t).length, 0) + " caracteres");
    return;
  }
  if (!CHAVE) { console.log("Sem ELEVENLABS_API_KEY: nada a gravar. O app e o site seguem com a voz do aparelho."); return; }
  const arg = process.argv[2];
  if (arg === "--amostras") return amostras();
  const id = arg || ler("edicoes/index.json")[0].id;
  await edicao(id);
  limpar();
})().catch((e) => { console.error(e.message || e); process.exit(1); });
