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
