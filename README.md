# Contexto

App de leitura do Contexto, newsletter sobre Mundo, Brasil e Inovação. Instala no celular como app (PWA) e é publicado pelo GitHub Pages.

- `edicoes/index.json`: lista das edições, a mais recente primeiro.
- `edicoes/<id>.json`: uma edição, com as notas e a Notícia completa de cada uma.
- `contextos/<tema>.json`: páginas de Contexto, reaproveitadas entre edições.
- Comentários ficam na tabela `contexto_comentarios` da Supabase.

Uma tarefa agendada grava a edição nova todo dia de madrugada.

## App Android

A pasta `android/` tem um app que abre o site do Contexto em tela cheia. A cada mudança nela, o GitHub Actions gera o `contexto.apk` e publica na página Releases do repositório.

## Site público e kit de redes

O app acima é o do editor: mostra o status de aprovação e o parecer da Ouvidoria. Para os leitores existe um site público gerado a partir dos mesmos arquivos, por `ferramentas/gerar_site.py`:

- `ler/`: a edição mais recente; `ler/<id>/` cada edição; `ler/<id>/<nota>/` a Notícia completa; `ler/contexto/<tema>/` as páginas de Contexto; `ler/arquivo/`.
- `assinar/`: página de inscrição, pensada para o link da bio. Os e-mails vão para a tabela `contexto_inscritos` da Supabase (`supabase/contexto_inscritos.sql`), com código de indicação por leitor.
- `feed.xml` e `sitemap.xml`, para leitores de RSS e para o Google.
- Cada nota tem botão de compartilhar (WhatsApp ou o menu do celular) e "Achei tendencioso", que cai nos comentários da redação marcado como `[leitor]`.

`ferramentas/instagram.js` gera em `social/<id>/` o carrossel do Instagram (1080x1350), o story do Número do dia, um vídeo vertical para Reels, TikTok e Shorts, a legenda e o texto do WhatsApp. O vídeo não fica no repositório: o workflow publica o do dia na versão `kit-redes` da página Releases. A página `social/` mostra o kit do dia para salvar e postar. `node ferramentas/instagram.js lancamento` gera os 3 posts fixos de abertura do perfil.

O workflow `.github/workflows/site.yml` roda os dois scripts a cada edição gravada na main e grava o resultado de volta. O endereço do site fica em `ferramentas/site.json`; quando houver domínio próprio, é só trocar ali.
