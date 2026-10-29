// Gera o kit de redes de uma edição: carrossel do Instagram (1080x1350), story do Número do dia (1080x1920),
// legenda e o texto "Em 30 segundos" para o WhatsApp. Tudo em social/<id>/.
// Uso: node ferramentas/instagram.js [id-da-edicao]   (sem id, usa a mais recente)
const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const RAIZ = path.join(__dirname, "..");
const SITE = JSON.parse(fs.readFileSync(path.join(__dirname, "site.json"), "utf8")).site.replace(/\/$/, "");
const MANTER = 7; // quantos kits ficam no repositório
const COR = { Mundo: "#cfe9e6", Brasil: "#b2dcd7", "Inovação": "#93cec7" };

const esc = (s) => String(s == null ? "" : s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const nomesFontes = (arr) => (arr || []).map((f) => f.t).join(", ");

const FONTES = "https://fonts.googleapis.com/css2?family=Schibsted+Grotesk:wght@600;700;800&family=Source+Serif+4:opsz,wght@8..60,400;8..60,600&family=IBM+Plex+Mono:wght@500;600&display=block";
const CSS = `
* { box-sizing: border-box; margin: 0; }
body { width: 1080px; height: var(--h); overflow: hidden; }
.card { width: 1080px; height: var(--h); padding: 84px 84px 72px; display: flex; flex-direction: column; gap: 34px; font-family: "Source Serif 4", Georgia, serif; }
.escuro { background: #121821; color: #e8edf2; }
.claro { background: #f6f8f9; color: #121821; }
.wm { display: inline-flex; font-family: "Schibsted Grotesk"; font-weight: 800; font-size: 44px; letter-spacing: -.02em; line-height: 1; }
.wm span { padding: 9px 7px 11px; color: #121821; }
.wm .a { background: #cfe9e6; } .wm .b { background: #b2dcd7; } .wm .c { background: #93cec7; }
.topo { display: flex; justify-content: space-between; align-items: center; }
.pag { font-family: "IBM Plex Mono"; font-weight: 600; font-size: 26px; letter-spacing: .08em; opacity: .6; }
.etq { align-self: flex-start; font-family: "IBM Plex Mono"; font-weight: 600; font-size: 28px; letter-spacing: .12em; text-transform: uppercase; padding: 10px 16px; color: #121821; }
h1, h2 { font-family: "Schibsted Grotesk"; font-weight: 800; letter-spacing: -.015em; line-height: 1.08; text-wrap: balance; }
h1 { font-size: 96px; }
h2 { font-size: 62px; }
.corpo { flex: 1; display: flex; flex-direction: column; gap: 28px; min-height: 0; }
.corpo p { font-size: var(--fs, 38px); line-height: 1.42; }
.pq b { font-family: "Schibsted Grotesk"; font-weight: 700; color: #0b7a75; }
.fonte { font-family: "Schibsted Grotesk"; font-weight: 600; font-size: 26px; color: #5a6573; }
.rodape { display: flex; justify-content: space-between; align-items: center; font-family: "Schibsted Grotesk"; font-weight: 700; font-size: 28px; }
.claro .rodape { color: #5a6573; }
.escuro .rodape { color: #9aa6b4; }
ol { list-style: none; padding: 0; display: flex; flex-direction: column; gap: 30px; counter-reset: n; }
ol li { counter-increment: n; display: grid; grid-template-columns: 60px 1fr; font-size: var(--fs, 42px); line-height: 1.35; }
ol li::before { content: counter(n); font-family: "IBM Plex Mono"; font-weight: 600; color: #93cec7; }
.data { font-family: "IBM Plex Mono"; font-weight: 600; font-size: 30px; letter-spacing: .1em; text-transform: uppercase; color: #93cec7; }
.numero { font-family: "Schibsted Grotesk"; font-weight: 800; font-size: 150px; line-height: 1; color: #93cec7; letter-spacing: -.03em; }
.grande p { font-size: 52px; line-height: 1.35; }
.cta { background: #93cec7; color: #121821; font-family: "Schibsted Grotesk"; font-weight: 800; font-size: 46px; padding: 26px 34px; border-radius: 16px; align-self: flex-start; }
.miudo { font-family: "Schibsted Grotesk"; font-size: 30px; color: #9aa6b4; line-height: 1.4; }
`;

const marca = '<span class="wm"><span class="a">con</span><span class="b">tex</span><span class="c">to</span></span>';
const pag = (i, n) => `<span class="pag">${i}/${n}</span>`;

function cartoes(ed) {
  const notas = [];
  (ed.secoes || []).forEach((s) => (s.notas || []).forEach((n) => notas.push({ ...n, secao: s.nome })));
  const total = 1 + notas.length + (ed.numero ? 1 : 0) + 1;
  const c = [];
  c.push(`<div class="card escuro"><div class="topo">${marca}${pag(1, total)}</div>
    <div class="corpo"><span class="data">${esc(ed.dia)}</span><h1>O que importa hoje e por quê</h1>
    <ol style="--fs:40px">${(ed.trinta || []).map((t) => `<li><span>${esc(t)}</span></li>`).join("")}</ol></div>
    <div class="rodape"><span>Mundo · Brasil · Inovação</span><span>Arraste →</span></div></div>`);
  notas.forEach((n, i) => {
    c.push(`<div class="card claro"><div class="topo"><span class="etq" style="background:${COR[n.secao] || "#dcefed"}">${esc(n.secao)}</span>${pag(i + 2, total)}</div>
      <div class="corpo encaixa"><h2>${esc(n.titulo)}</h2><p>${esc(n.texto)}</p>${n.pq ? `<p class="pq"><b>Por que importa:</b> ${esc(n.pq)}</p>` : ""}</div>
      <p class="fonte">Fonte${(n.fontes || []).length > 1 ? "s" : ""}: ${esc(nomesFontes(n.fontes))}</p>
      <div class="rodape">${marca}<span>Notícia completa no link da bio</span></div></div>`);
  });
  if (ed.numero) {
    c.push(`<div class="card escuro"><div class="topo"><span class="etq" style="background:#93cec7">Número do dia</span>${pag(total - 1, total)}</div>
      <div class="corpo grande" style="justify-content:center"><span class="numero">${esc(ed.numero.valor)}</span><p>${esc(ed.numero.texto)}</p></div>
      <p class="fonte" style="color:#9aa6b4">Fonte${(ed.numero.fontes || []).length > 1 ? "s" : ""}: ${esc(nomesFontes(ed.numero.fontes))}</p>
      <div class="rodape">${marca}<span></span></div></div>`);
  }
  c.push(`<div class="card escuro"><div class="topo">${marca}${pag(total, total)}</div>
    <div class="corpo" style="justify-content:center;gap:44px"><h1 style="font-size:88px">Entenda o dia em 5 minutos, sem torcida.</h1>
    <p class="miudo" style="font-size:38px;color:#e8edf2">O fato, por que importa e a fonte. Mundo, Brasil e Inovação, todo dia.</p>
    <span class="cta">Assine grátis: link na bio</span></div>
    <p class="miudo">Escrito por inteligência artificial, checado antes de sair. Salve e mande para quem vive sem tempo.</p></div>`);
  return c;
}

function story(ed) {
  if (!ed.numero) return null;
  return `<div class="card escuro" style="padding:140px 90px 160px"><div class="topo">${marca}<span class="data">${esc(ed.dia)}</span></div>
    <div class="corpo grande" style="justify-content:center;gap:40px"><span class="etq" style="background:#93cec7">Número do dia</span><span class="numero" style="font-size:160px">${esc(ed.numero.valor)}</span><p style="font-size:58px">${esc(ed.numero.texto)}</p>
    <p class="fonte" style="color:#9aa6b4;font-size:30px">Fonte${(ed.numero.fontes || []).length > 1 ? "s" : ""}: ${esc(nomesFontes(ed.numero.fontes))}</p></div>
    <span class="cta" style="align-self:center">Edição completa no link da bio</span></div>`;
}

function legenda(ed) {
  const linhas = [ed.dia, "", "Em 30 segundos:"];
  (ed.trinta || []).forEach((t, i) => linhas.push(`${i + 1}. ${t}`));
  linhas.push("", "Arraste para ver cada notícia com o porquê de ela importar. Todas com fonte.", "",
    "📩 Newsletter grátis no link da bio.", "Escrito por inteligência artificial e checado antes de sair.", "",
    "#noticias #brasil #mundo #tecnologia #inovacao #newsletter #contexto");
  return linhas.join("\n") + "\n";
}

function whatsapp(ed) {
  return [`*Contexto em 30 segundos* · ${ed.dia}`, "", ...(ed.trinta || []).map((t) => `• ${t}`), "",
    `Entenda cada uma: ${SITE}/ler/${ed._id}/`, `Assine grátis: ${SITE}/assinar/?origem=whatsapp`].join("\n") + "\n";
}

async function foto(browser, html, h, arquivo) {
  const p = await browser.newPage({ viewport: { width: 1080, height: h }, deviceScaleFactor: 1 });
  await p.setContent(`<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><link rel="stylesheet" href="${FONTES}"><style>:root{--h:${h}px}${CSS}</style></head><body>${html}</body></html>`, { waitUntil: "networkidle" });
  await p.evaluate(async () => { await Promise.all(["800 40px \"Schibsted Grotesk\"", "700 40px \"Schibsted Grotesk\"", "400 40px \"Source Serif 4\"", "600 40px \"IBM Plex Mono\""].map((f) => document.fonts.load(f))); await document.fonts.ready; });
  // Diminui a letra do corpo até o cartão caber.
  await p.evaluate(() => {
    const card = document.querySelector(".card"), corpo = document.querySelector(".encaixa");
    if (!corpo) return;
    let fs = 38;
    while (card.scrollHeight > card.clientHeight && fs > 26) { fs -= 1; corpo.style.setProperty("--fs", fs + "px"); }
  });
  await p.screenshot({ path: arquivo, type: "png" });
  await p.close();
}

// Três posts fixos para abrir o perfil: o que é, como fazemos e a transparência sobre IA.
const LANCAMENTO = [
  { arq: "post-1-o-que-e.png", html: `<div class="card escuro"><div class="topo">${marca}<span class="pag">Newsletter grátis</span></div>
    <div class="corpo" style="justify-content:center;gap:40px"><h1>O que importa hoje e por quê, em 5 minutos.</h1>
    <p class="miudo" style="font-size:42px;color:#e8edf2">Mundo, Brasil e Inovação no seu e-mail, sem torcida e sem ruído.</p><span class="cta">Assine grátis: link na bio</span></div>
    <div class="rodape"><span>Mundo · Brasil · Inovação</span><span></span></div></div>`,
    legenda: "Chegou o Contexto: o que importa hoje e por quê, em 5 minutos.\n\nToda manhã, as notícias de Mundo, Brasil e Inovação com o fato, por que importa e a fonte. Sem torcida e sem ruído.\n\n📩 É grátis: link na bio.\n\n#noticias #newsletter #brasil #mundo #tecnologia" },
  { arq: "post-2-como-fazemos.png", html: `<div class="card claro"><div class="topo"><span class="etq" style="background:#b2dcd7">Como fazemos</span>${marca}</div>
    <div class="corpo" style="gap:40px"><h2>Quatro regras que toda edição segue</h2><ol style="--fs:40px">
    <li><span><b>Sem lado.</b> Verbos neutros, os dois lados com o mesmo espaço e opinião só entre aspas, com nome.</span></li>
    <li><span><b>Com fonte.</b> Todo número tem fonte e data, e cada notícia tem duas fontes independentes.</span></li>
    <li><span><b>Com controle.</b> Checagem, Ouvidoria de neutralidade e aprovação de uma pessoa antes de sair.</span></li>
    <li><span><b>Erro corrigido em público.</b> A seção Corrigimos mostra o que estava errado e o que é o certo.</span></li></ol></div>
    <div class="rodape"><span>Assine grátis: link na bio</span><span></span></div></div>`,
    legenda: "Como o Contexto é feito, em quatro regras.\n\n1. Sem lado: verbos neutros e os dois lados com o mesmo espaço.\n2. Com fonte: duas fontes independentes por notícia.\n3. Com controle: checagem, Ouvidoria e aprovação humana.\n4. Erro corrigido em público.\n\nAchou algo tendencioso? Toda edição tem um botão que vai direto para a Ouvidoria.\n\n📩 Link na bio." },
  { arq: "post-3-escrito-por-ia.png", html: `<div class="card escuro"><div class="topo">${marca}<span class="pag">Transparência</span></div>
    <div class="corpo" style="justify-content:center;gap:40px"><h1 style="font-size:92px">Escrito por IA. Checado antes de sair.</h1>
    <p class="miudo" style="font-size:40px;color:#e8edf2">A redação do Contexto é feita por inteligência artificial, e a gente diz isso em toda edição. Nenhuma pessoa escreve as notas, e ninguém finge que escreve.</p>
    <p class="miudo" style="font-size:34px">Antes de sair, cada edição passa por checagem de fontes, por uma Ouvidoria de neutralidade e pela aprovação de uma pessoa.</p></div>
    <div class="rodape"><span>Assine grátis: link na bio</span><span></span></div></div>`,
    legenda: "Sim, o Contexto é escrito por inteligência artificial. E a gente prefere dizer isso logo.\n\nO que a IA faz: lê as fontes, escreve as notas e explica o contexto.\nO que garante a qualidade: duas fontes independentes por notícia, uma Ouvidoria de neutralidade e a aprovação de uma pessoa antes de cada envio.\n\nErrou? Corrigimos em público.\n\n📩 Newsletter grátis no link da bio." },
];

(async () => {
  if (process.argv[2] === "lancamento") {
    const dir = path.join(RAIZ, "social", "lancamento");
    fs.mkdirSync(dir, { recursive: true });
    const browser = await chromium.launch();
    for (const p of LANCAMENTO) {
      await foto(browser, p.html, 1350, path.join(dir, p.arq));
      fs.writeFileSync(path.join(dir, p.arq.replace(".png", ".txt")), p.legenda + "\n");
    }
    await browser.close();
    console.log("Posts de lançamento em social/lancamento/.");
    return;
  }
  const lista = JSON.parse(fs.readFileSync(path.join(RAIZ, "edicoes", "index.json"), "utf8")).filter((e) => !e.id.startsWith("piloto"));
  const id = process.argv[2] || (lista[0] && lista[0].id);
  if (!id) { console.log("Nenhuma edição."); return; }
  const ed = JSON.parse(fs.readFileSync(path.join(RAIZ, "edicoes", id + ".json"), "utf8"));
  ed._id = id;
  const dir = path.join(RAIZ, "social", id);
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(dir, { recursive: true });

  const browser = await chromium.launch();
  const cs = cartoes(ed);
  for (let i = 0; i < cs.length; i++) await foto(browser, cs[i], 1350, path.join(dir, `carrossel-${String(i + 1).padStart(2, "0")}.png`));
  const st = story(ed);
  if (st) await foto(browser, st, 1920, path.join(dir, "story-numero.png"));
  await browser.close();
  fs.writeFileSync(path.join(dir, "legenda.txt"), legenda(ed));
  fs.writeFileSync(path.join(dir, "whatsapp.txt"), whatsapp(ed));

  // Mantém só os kits mais recentes e atualiza a lista usada pela página social/.
  const kits = fs.readdirSync(path.join(RAIZ, "social")).filter((d) => /^\d{4}-\d{2}-\d{2}/.test(d)).sort().reverse();
  kits.slice(MANTER).forEach((d) => fs.rmSync(path.join(RAIZ, "social", d), { recursive: true, force: true }));
  const indice = kits.slice(0, MANTER).map((d) => ({ id: d, arquivos: fs.readdirSync(path.join(RAIZ, "social", d)).sort() }));
  fs.writeFileSync(path.join(RAIZ, "social", "kits.json"), JSON.stringify(indice, null, 1) + "\n");
  console.log(`Kit de redes de ${id}: ${cs.length} cartões${st ? " + story" : ""}.`);
})();
