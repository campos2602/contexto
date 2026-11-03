-- Lista de inscritos do Contexto, usada até a conta da beehiiv existir (depois é só exportar e importar).
-- Qualquer visitante pode se inscrever; ninguém de fora consegue ler a lista.
create table if not exists public.contexto_inscritos (
  id bigint generated always as identity primary key,
  email text not null check (char_length(email) <= 254 and email ~* '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
  codigo text not null unique check (codigo ~ '^[a-z0-9]{6,12}$'),
  indicado_por text check (indicado_por is null or indicado_por ~ '^[a-z0-9]{6,12}$'),
  origem text check (origem is null or char_length(origem) <= 200),
  status text not null default 'ativo' check (status in ('ativo', 'cancelado', 'importado')),
  criado_em timestamptz not null default now()
);
create unique index if not exists contexto_inscritos_email_un on public.contexto_inscritos (lower(email));
alter table public.contexto_inscritos enable row level security;
drop policy if exists "contexto: inscricao publica" on public.contexto_inscritos;
create policy "contexto: inscricao publica" on public.contexto_inscritos
  for insert to anon, authenticated
  with check (status = 'ativo');

-- Quem mais indicou (rodar no painel da Supabase):
-- select i.codigo, i.email, count(*) as indicacoes
--   from public.contexto_inscritos i join public.contexto_inscritos n on n.indicado_por = i.codigo
--  group by 1, 2 order by 3 desc;

-- Minha edição (nov 2026): categorias que cada leitor escolheu, na ordem dele (ids de categorias.json).
-- Seguro rodar mais de uma vez. Sem esta parte o site continua inscrevendo, só não guarda a escolha.
alter table public.contexto_inscritos add column if not exists categorias text[];
alter table public.contexto_inscritos drop constraint if exists contexto_inscritos_categorias_ck;
alter table public.contexto_inscritos add constraint contexto_inscritos_categorias_ck
  check (categorias is null or (cardinality(categorias) <= 30 and array_to_string(categorias, ',') ~ '^[a-z0-9-]{1,40}(,[a-z0-9-]{1,40})*$'));
alter table public.contexto_inscritos add column if not exists categorias_em timestamptz;
-- Quantas notícias o leitor quer de cada categoria, ex.: {"mundo": 3, "brasil": 2, "esportes": 1}.
alter table public.contexto_inscritos add column if not exists quantidades jsonb;
alter table public.contexto_inscritos drop constraint if exists contexto_inscritos_quantidades_ck;
alter table public.contexto_inscritos add constraint contexto_inscritos_quantidades_ck
  check (quantidades is null or (jsonb_typeof(quantidades) = 'object' and pg_column_size(quantidades) <= 2000));

-- O leitor muda as categorias depois de inscrito. O código de indicação (8 letras aleatórias, só ele tem
-- no celular) é a chave; a função só mexe nessas duas colunas e não devolve dado nenhum.
drop function if exists public.contexto_preferencias(text, text[]);
create or replace function public.contexto_preferencias(p_codigo text, p_categorias text[], p_quantidades jsonb default null)
returns void language sql security definer set search_path = public as $$
  update public.contexto_inscritos
     set categorias = nullif(p_categorias, '{}'),
         quantidades = case when jsonb_typeof(p_quantidades) = 'object' and pg_column_size(p_quantidades) <= 2000 then p_quantidades end,
         categorias_em = now()
   where codigo = p_codigo and p_codigo ~ '^[a-z0-9]{6,12}$' and status <> 'cancelado';
$$;
revoke all on function public.contexto_preferencias(text, text[], jsonb) from public;
grant execute on function public.contexto_preferencias(text, text[], jsonb) to anon, authenticated;

-- Quantos leitores querem cada categoria (rodar no painel; ajuda a decidir o que cobrir):
-- select c as categoria, count(*) from public.contexto_inscritos, unnest(categorias) c
--  where status = 'ativo' group by 1 order by 2 desc;
