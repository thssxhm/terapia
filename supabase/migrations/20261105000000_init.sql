-- =====================================================================
-- Terapia — esquema inicial do banco (Supabase / Postgres)
-- Rode este arquivo no SQL Editor do Supabase (ou `supabase db push`).
-- Todas as tabelas usam Row Level Security (RLS): cada psicólogo só vê
-- os próprios pacientes e cada paciente só vê os próprios dados.
-- =====================================================================

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------------
-- PERFIS
-- ---------------------------------------------------------------------
create table public.profiles (
  id              uuid primary key references auth.users on delete cascade,
  role            text check (role in ('psychologist', 'patient')),
  full_name       text,
  crp             text,
  email           text,
  phone           text,
  timezone        text not null default 'America/Sao_Paulo',
  patient_limit   int  not null default 0,          -- definido pelo plano (10, 20, 30…)
  trial_until     timestamptz default (now() + interval '14 days'),
  created_at      timestamptz not null default now()
);
create index on public.profiles (lower(email));

-- Cria o perfil automaticamente quando um usuário se cadastra
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id, email, full_name)
  values (new.id, lower(new.email), coalesce(new.raw_user_meta_data->>'full_name', new.raw_user_meta_data->>'name'))
  on conflict (id) do nothing;
  return new;
end $$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- O usuário só pode alterar nome, CRP e telefone (nunca papel ou limite do plano)
revoke update on public.profiles from authenticated, anon;
grant update (full_name, crp, phone, timezone) on public.profiles to authenticated;

-- Escolher ser psicólogo (só funciona se o papel ainda não foi definido)
create or replace function public.become_psychologist(p_full_name text, p_crp text)
returns void language plpgsql security definer set search_path = public as $$
begin
  update public.profiles
     set role = 'psychologist', full_name = p_full_name, crp = p_crp
   where id = auth.uid() and (role is null or role = 'psychologist');
  if not found then
    raise exception 'Esta conta já está cadastrada como paciente.';
  end if;
end $$;

-- Limite efetivo de pacientes: plano pago OU 10 pacientes durante o teste grátis
create or replace function public.effective_patient_limit(p_psy uuid)
returns int language sql stable security definer set search_path = public as $$
  select greatest(
           coalesce(patient_limit, 0),
           case when trial_until > now() then 10 else 0 end)
    from public.profiles where id = p_psy;
$$;

-- ---------------------------------------------------------------------
-- PACIENTES
-- ---------------------------------------------------------------------
create table public.patients (
  id                uuid primary key default gen_random_uuid(),
  psychologist_id   uuid not null references public.profiles on delete cascade,
  user_id           uuid references public.profiles on delete set null,
  full_name         text not null,
  age               int,
  phone             text,
  email             text not null,
  reminder_enabled  boolean not null default false,   -- "sininho" do psicólogo
  reminder_minutes  int not null default 30,
  created_at        timestamptz not null default now(),
  unique (psychologist_id, email)
);
create index on public.patients (psychologist_id);
create index on public.patients (user_id);

create or replace function public.check_patient_limit()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_count int; v_limit int;
begin
  select count(*) into v_count from public.patients where psychologist_id = new.psychologist_id;
  v_limit := public.effective_patient_limit(new.psychologist_id);
  if v_count >= v_limit then
    raise exception 'Limite do plano atingido (% pacientes). Amplie seu plano para cadastrar mais.', v_limit
      using errcode = 'P0001';
  end if;
  return new;
end $$;

create trigger patients_limit before insert on public.patients
  for each row execute function public.check_patient_limit();

-- Funções auxiliares usadas nas políticas (evitam recursão de RLS)
create or replace function public.is_psychologist_of(p_patient uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.patients where id = p_patient and psychologist_id = auth.uid());
$$;

create or replace function public.is_patient_self(p_patient uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.patients where id = p_patient and user_id = auth.uid());
$$;

create or replace function public.can_access_patient(p_patient uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select public.is_psychologist_of(p_patient) or public.is_patient_self(p_patient);
$$;

-- ---------------------------------------------------------------------
-- HORÁRIOS FIXOS E SESSÕES
-- ---------------------------------------------------------------------
create table public.schedule_slots (
  id                uuid primary key default gen_random_uuid(),
  patient_id        uuid not null references public.patients on delete cascade,
  weekday           smallint not null check (weekday between 1 and 7),  -- 1 = segunda … 7 = domingo
  start_time        time not null,
  duration_minutes  int not null default 50,
  active            boolean not null default true,
  created_at        timestamptz not null default now()
);
create index on public.schedule_slots (patient_id);

create table public.sessions (
  id                uuid primary key default gen_random_uuid(),
  patient_id        uuid not null references public.patients on delete cascade,
  slot_id           uuid references public.schedule_slots on delete set null,
  starts_at         timestamptz not null,
  duration_minutes  int not null default 50,
  status            text not null default 'scheduled'
                    check (status in ('scheduled', 'confirmed', 'cancelled', 'done')),
  created_at        timestamptz not null default now(),
  unique (slot_id, starts_at)
);
create index on public.sessions (patient_id, starts_at);

-- Cria as próximas N semanas de sessões de um horário fixo (não duplica)
create or replace function public.fill_slot_sessions(p_slot uuid, p_weeks int default 12)
returns void language plpgsql security definer set search_path = public as $$
declare s record; d date; i int; ts timestamptz;
begin
  select sl.*, coalesce(pr.timezone, 'America/Sao_Paulo') as tz
    into s
    from public.schedule_slots sl
    join public.patients p  on p.id = sl.patient_id
    join public.profiles pr on pr.id = p.psychologist_id
   where sl.id = p_slot;
  if not found or not s.active then return; end if;

  d := (now() at time zone s.tz)::date;
  d := d + ((s.weekday - extract(isodow from d)::int + 7) % 7);
  for i in 0 .. p_weeks - 1 loop
    ts := ((d + i * 7) + s.start_time) at time zone s.tz;
    if ts > now() then
      insert into public.sessions (patient_id, slot_id, starts_at, duration_minutes)
      values (s.patient_id, p_slot, ts, s.duration_minutes)
      on conflict (slot_id, starts_at) do nothing;
    end if;
  end loop;
end $$;

-- Quando um horário fixo muda, refaz as sessões futuras ainda não confirmadas
create or replace function public.on_slot_changed()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if tg_op in ('UPDATE', 'DELETE') then
    delete from public.sessions
     where slot_id = old.id and starts_at > now() and status = 'scheduled';
  end if;
  if tg_op in ('INSERT', 'UPDATE') then
    perform public.fill_slot_sessions(new.id);
    return new;
  end if;
  return old;
end $$;

create trigger slots_after_insert_update after insert or update on public.schedule_slots
  for each row execute function public.on_slot_changed();
create trigger slots_before_delete before delete on public.schedule_slots
  for each row execute function public.on_slot_changed();

-- Se o psicólogo editar o horário de uma sessão, ela vira "avulsa"
create or replace function public.detach_edited_session()
returns trigger language plpgsql set search_path = public as $$
begin
  if new.starts_at is distinct from old.starts_at then
    new.slot_id := null;
  end if;
  return new;
end $$;
create trigger sessions_detach before update on public.sessions
  for each row execute function public.detach_edited_session();

-- Estende a agenda de todos os horários (rodar 1x por semana com pg_cron)
create or replace function public.extend_all_sessions()
returns void language plpgsql security definer set search_path = public as $$
declare r record;
begin
  for r in select id from public.schedule_slots where active loop
    perform public.fill_slot_sessions(r.id);
  end loop;
end $$;

-- Anotações pré/pós sessão do PSICÓLOGO (o paciente não vê)
create table public.psychologist_session_notes (
  session_id  uuid primary key references public.sessions on delete cascade,
  patient_id  uuid not null references public.patients on delete cascade,
  pre_note    text,
  post_note   text,
  updated_at  timestamptz not null default now()
);

-- Anotações pré/pós sessão do PACIENTE + nome opcional do bloco (o psicólogo não vê)
create table public.patient_session_notes (
  session_id  uuid primary key references public.sessions on delete cascade,
  patient_id  uuid not null references public.patients on delete cascade,
  title       text,
  pre_note    text,
  post_note   text,
  updated_at  timestamptz not null default now()
);

-- ---------------------------------------------------------------------
-- PRONTUÁRIO (somente psicólogo)
-- ---------------------------------------------------------------------
create table public.anamnesis (
  patient_id  uuid primary key references public.patients on delete cascade,
  data        jsonb not null default '{}'::jsonb,
  updated_at  timestamptz not null default now()
);

create table public.evolutions (
  id          uuid primary key default gen_random_uuid(),
  patient_id  uuid not null references public.patients on delete cascade,
  session_id  uuid references public.sessions on delete set null,
  entry_date  date not null default current_date,
  content     text not null,
  created_at  timestamptz not null default now()
);
create index on public.evolutions (patient_id, entry_date desc);

create table public.important_info (
  id          uuid primary key default gen_random_uuid(),
  patient_id  uuid not null references public.patients on delete cascade,
  category    text not null default 'outro' check (category in ('nome', 'data', 'outro')),
  title       text not null,
  details     text,
  ref_date    date,
  created_at  timestamptz not null default now()
);
create index on public.important_info (patient_id);

create table public.attachments (
  id            uuid primary key default gen_random_uuid(),
  patient_id    uuid not null references public.patients on delete cascade,
  file_name     text not null,
  storage_path  text not null,
  mime_type     text,
  size_bytes    bigint,
  created_at    timestamptz not null default now()
);
create index on public.attachments (patient_id);

-- ---------------------------------------------------------------------
-- COMUNICAÇÃO
-- ---------------------------------------------------------------------
create table public.change_requests (
  id            uuid primary key default gen_random_uuid(),
  patient_id    uuid not null references public.patients on delete cascade,
  session_id    uuid references public.sessions on delete cascade,
  kind          text not null check (kind in ('confirm', 'cancel', 'reschedule')),
  message       text,
  status        text not null default 'pending' check (status in ('pending', 'accepted', 'declined')),
  created_at    timestamptz not null default now(),
  resolved_at   timestamptz
);
create index on public.change_requests (patient_id, created_at desc);

create table public.emergencies (
  id               uuid primary key default gen_random_uuid(),
  patient_id       uuid not null references public.patients on delete cascade,
  level            text not null check (level in ('yellow', 'red')),
  message          text not null,
  created_at       timestamptz not null default now(),
  acknowledged_at  timestamptz
);
create index on public.emergencies (patient_id, created_at desc);

create table public.messages (
  id          uuid primary key default gen_random_uuid(),
  patient_id  uuid not null references public.patients on delete cascade,
  sender_id   uuid not null default auth.uid() references public.profiles on delete cascade,
  body        text not null check (length(body) between 1 and 4000),
  created_at  timestamptz not null default now(),
  read_at     timestamptz
);
create index on public.messages (patient_id, created_at);

-- ---------------------------------------------------------------------
-- HUMOR
-- ---------------------------------------------------------------------
create table public.mood_entries (
  id          uuid primary key default gen_random_uuid(),
  patient_id  uuid not null references public.patients on delete cascade,
  entry_date  date not null,
  mood        smallint not null check (mood between 1 and 5),  -- 5 = muito feliz … 1 = muito triste
  note        text,
  created_at  timestamptz not null default now(),
  unique (patient_id, entry_date)
);

-- Semana que o paciente escolheu enviar ao psicólogo
create table public.mood_shares (
  id          uuid primary key default gen_random_uuid(),
  patient_id  uuid not null references public.patients on delete cascade,
  week_start  date not null,                 -- segunda-feira da semana
  created_at  timestamptz not null default now(),
  unique (patient_id, week_start)
);

-- ---------------------------------------------------------------------
-- NOTIFICAÇÕES, DISPOSITIVOS E ASSINATURAS
-- ---------------------------------------------------------------------
create table public.notifications (
  id            uuid primary key default gen_random_uuid(),
  recipient_id  uuid not null references public.profiles on delete cascade,
  patient_id    uuid references public.patients on delete cascade,
  type          text not null,   -- message | request | emergency_yellow | emergency_red | mood | request_answer
  title         text not null,
  body          text,
  ref_id        uuid,
  read_at       timestamptz,
  created_at    timestamptz not null default now()
);
create index on public.notifications (recipient_id, created_at desc);

create table public.device_tokens (
  token       text primary key,
  user_id     uuid not null references public.profiles on delete cascade,
  updated_at  timestamptz not null default now()
);

create table public.subscriptions (
  id              uuid primary key default gen_random_uuid(),
  user_id         uuid not null references public.profiles on delete cascade,
  product_id      text not null,
  purchase_token  text not null unique,
  patients        int not null,
  expires_at      timestamptz,
  state           text,
  updated_at      timestamptz not null default now()
);

-- ---------------------------------------------------------------------
-- GATILHOS QUE GERAM NOTIFICAÇÕES (enviadas por push pela Edge Function "push")
-- ---------------------------------------------------------------------
create or replace function public.notify_on_change_request()
returns trigger language plpgsql security definer set search_path = public as $$
declare p record; v_title text; v_when text;
begin
  select pa.*, pr.timezone into p
    from public.patients pa join public.profiles pr on pr.id = pa.psychologist_id
   where pa.id = new.patient_id;
  select to_char(starts_at at time zone p.timezone, 'DD/MM "às" HH24:MI') into v_when
    from public.sessions where id = new.session_id;

  if tg_op = 'INSERT' then
    v_title := case new.kind
      when 'confirm'    then 'Confirmação: '
      when 'cancel'     then 'Cancelamento: '
      else                   'Pedido de ajuste de horário: ' end || p.full_name;
    insert into public.notifications (recipient_id, patient_id, type, title, body, ref_id)
    values (p.psychologist_id, p.id, 'request', v_title,
            coalesce('Sessão de ' || v_when || '. ', '') || coalesce(new.message, ''), new.id);
    if new.kind = 'confirm' and new.session_id is not null then
      update public.sessions set status = 'confirmed' where id = new.session_id and status = 'scheduled';
      update public.change_requests set status = 'accepted', resolved_at = now() where id = new.id;
    end if;
  elsif tg_op = 'UPDATE' and new.status <> old.status and new.kind <> 'confirm' and p.user_id is not null then
    insert into public.notifications (recipient_id, patient_id, type, title, body, ref_id)
    values (p.user_id, p.id, 'request_answer',
            case new.status when 'accepted' then 'Seu pedido foi aceito' else 'Seu pedido foi recusado' end,
            coalesce('Sessão de ' || v_when, ''), new.id);
  end if;
  return new;
end $$;
create trigger change_requests_notify after insert or update on public.change_requests
  for each row execute function public.notify_on_change_request();

create or replace function public.notify_on_emergency()
returns trigger language plpgsql security definer set search_path = public as $$
declare p record;
begin
  select * into p from public.patients where id = new.patient_id;
  insert into public.notifications (recipient_id, patient_id, type, title, body, ref_id)
  values (p.psychologist_id, p.id, 'emergency_' || new.level,
          case new.level when 'red' then '🔴 EMERGÊNCIA (muito urgente): ' else '🟡 Emergência (pouco urgente): ' end || p.full_name,
          new.message, new.id);
  return new;
end $$;
create trigger emergencies_notify after insert on public.emergencies
  for each row execute function public.notify_on_emergency();

create or replace function public.notify_on_message()
returns trigger language plpgsql security definer set search_path = public as $$
declare p record; v_to uuid; v_from text;
begin
  select pa.*, pr.full_name as psy_name into p
    from public.patients pa join public.profiles pr on pr.id = pa.psychologist_id
   where pa.id = new.patient_id;
  if new.sender_id = p.psychologist_id then
    v_to := p.user_id; v_from := p.psy_name;
  else
    v_to := p.psychologist_id; v_from := p.full_name;
  end if;
  if v_to is not null then
    insert into public.notifications (recipient_id, patient_id, type, title, body, ref_id)
    values (v_to, p.id, 'message', 'Nova mensagem de ' || coalesce(v_from, ''), left(new.body, 140), new.id);
  end if;
  return new;
end $$;
create trigger messages_notify after insert on public.messages
  for each row execute function public.notify_on_message();

create or replace function public.notify_on_mood_share()
returns trigger language plpgsql security definer set search_path = public as $$
declare p record;
begin
  select * into p from public.patients where id = new.patient_id;
  insert into public.notifications (recipient_id, patient_id, type, title, body, ref_id)
  values (p.psychologist_id, p.id, 'mood', 'Gráfico de humor: ' || p.full_name,
          'Semana de ' || to_char(new.week_start, 'DD/MM'), new.id);
  return new;
end $$;
create trigger mood_shares_notify after insert on public.mood_shares
  for each row execute function public.notify_on_mood_share();

-- Excluir a própria conta (exigência da Google Play)
create or replace function public.delete_my_account()
returns void language plpgsql security definer set search_path = public, auth as $$
begin
  -- Dados pessoais do paciente (humor e anotações próprias) saem junto com a conta
  delete from public.mood_entries          where patient_id in (select id from public.patients where user_id = auth.uid());
  delete from public.mood_shares           where patient_id in (select id from public.patients where user_id = auth.uid());
  delete from public.patient_session_notes where patient_id in (select id from public.patients where user_id = auth.uid());
  -- Psicólogo: pacientes e prontuários são apagados em cascata (profiles → patients)
  delete from auth.users where id = auth.uid();
end $$;

-- ---------------------------------------------------------------------
-- ROW LEVEL SECURITY
-- ---------------------------------------------------------------------
alter table public.profiles                   enable row level security;
alter table public.patients                   enable row level security;
alter table public.schedule_slots             enable row level security;
alter table public.sessions                   enable row level security;
alter table public.psychologist_session_notes enable row level security;
alter table public.patient_session_notes      enable row level security;
alter table public.anamnesis                  enable row level security;
alter table public.evolutions                 enable row level security;
alter table public.important_info             enable row level security;
alter table public.attachments                enable row level security;
alter table public.change_requests            enable row level security;
alter table public.emergencies                enable row level security;
alter table public.messages                   enable row level security;
alter table public.mood_entries               enable row level security;
alter table public.mood_shares                enable row level security;
alter table public.notifications              enable row level security;
alter table public.device_tokens              enable row level security;
alter table public.subscriptions              enable row level security;

-- Perfis: o próprio, o psicólogo do paciente e os pacientes do psicólogo
create policy "perfil: ver" on public.profiles for select using (
  id = auth.uid()
  or exists (select 1 from public.patients p where p.user_id = auth.uid() and p.psychologist_id = profiles.id)
  or exists (select 1 from public.patients p where p.psychologist_id = auth.uid() and p.user_id = profiles.id)
);
create policy "perfil: editar o próprio" on public.profiles for update using (id = auth.uid());

-- Pacientes
create policy "pacientes: psicólogo gerencia" on public.patients for all
  using (psychologist_id = auth.uid()) with check (psychologist_id = auth.uid());
create policy "pacientes: paciente vê o próprio" on public.patients for select
  using (user_id = auth.uid());

-- Horários fixos e sessões
create policy "slots: psicólogo gerencia" on public.schedule_slots for all
  using (public.is_psychologist_of(patient_id)) with check (public.is_psychologist_of(patient_id));
create policy "slots: paciente vê" on public.schedule_slots for select
  using (public.is_patient_self(patient_id));

create policy "sessões: psicólogo gerencia" on public.sessions for all
  using (public.is_psychologist_of(patient_id)) with check (public.is_psychologist_of(patient_id));
create policy "sessões: paciente vê" on public.sessions for select
  using (public.is_patient_self(patient_id));

create policy "notas psicólogo" on public.psychologist_session_notes for all
  using (public.is_psychologist_of(patient_id)) with check (public.is_psychologist_of(patient_id));
create policy "notas paciente" on public.patient_session_notes for all
  using (public.is_patient_self(patient_id)) with check (public.is_patient_self(patient_id));

-- Prontuário: somente o psicólogo
create policy "anamnese" on public.anamnesis for all
  using (public.is_psychologist_of(patient_id)) with check (public.is_psychologist_of(patient_id));
create policy "evolução" on public.evolutions for all
  using (public.is_psychologist_of(patient_id)) with check (public.is_psychologist_of(patient_id));
create policy "informações importantes" on public.important_info for all
  using (public.is_psychologist_of(patient_id)) with check (public.is_psychologist_of(patient_id));
create policy "anexos" on public.attachments for all
  using (public.is_psychologist_of(patient_id)) with check (public.is_psychologist_of(patient_id));

-- Pedidos: paciente cria, psicólogo responde
create policy "pedidos: ver" on public.change_requests for select
  using (public.can_access_patient(patient_id));
create policy "pedidos: paciente cria" on public.change_requests for insert
  with check (public.is_patient_self(patient_id) and status = 'pending');
create policy "pedidos: psicólogo responde" on public.change_requests for update
  using (public.is_psychologist_of(patient_id));

-- Emergências
create policy "emergências: ver" on public.emergencies for select
  using (public.can_access_patient(patient_id));
create policy "emergências: paciente envia" on public.emergencies for insert
  with check (public.is_patient_self(patient_id));
create policy "emergências: psicólogo marca como vista" on public.emergencies for update
  using (public.is_psychologist_of(patient_id));

-- Mensagens
create policy "mensagens: ver" on public.messages for select
  using (public.can_access_patient(patient_id));
create policy "mensagens: enviar" on public.messages for insert
  with check (public.can_access_patient(patient_id) and sender_id = auth.uid());
create policy "mensagens: marcar lida" on public.messages for update
  using (public.can_access_patient(patient_id) and sender_id <> auth.uid());

-- Humor: paciente registra; psicólogo só vê semanas enviadas
create policy "humor: paciente gerencia" on public.mood_entries for all
  using (public.is_patient_self(patient_id)) with check (public.is_patient_self(patient_id));
create policy "humor: psicólogo vê semanas compartilhadas" on public.mood_entries for select using (
  public.is_psychologist_of(patient_id) and exists (
    select 1 from public.mood_shares s
     where s.patient_id = mood_entries.patient_id
       and mood_entries.entry_date between s.week_start and s.week_start + 6)
);
create policy "humor compartilhado: ver" on public.mood_shares for select
  using (public.can_access_patient(patient_id));
create policy "humor compartilhado: paciente envia" on public.mood_shares for insert
  with check (public.is_patient_self(patient_id));

-- Notificações: cada um vê as suas
create policy "notificações" on public.notifications for select using (recipient_id = auth.uid());
create policy "notificações: marcar lida" on public.notifications for update using (recipient_id = auth.uid());
create policy "notificações: apagar" on public.notifications for delete using (recipient_id = auth.uid());

create policy "tokens" on public.device_tokens for all
  using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy "assinaturas: ver" on public.subscriptions for select using (user_id = auth.uid());

-- ---------------------------------------------------------------------
-- REALTIME (chat, notificações e pop-up de emergência ao vivo)
-- ---------------------------------------------------------------------
alter publication supabase_realtime add table public.messages;
alter publication supabase_realtime add table public.notifications;
alter publication supabase_realtime add table public.emergencies;

-- ---------------------------------------------------------------------
-- STORAGE: anexos (PDF/DOC) — pasta = id do psicólogo
-- ---------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('attachments', 'attachments', false, 20971520, array[
  'application/pdf',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document'])
on conflict (id) do nothing;

create policy "anexos: psicólogo dono da pasta" on storage.objects for all
  using (bucket_id = 'attachments' and (storage.foldername(name))[1] = auth.uid()::text)
  with check (bucket_id = 'attachments' and (storage.foldername(name))[1] = auth.uid()::text);

-- ---------------------------------------------------------------------
-- AGENDAMENTO SEMANAL (estende a agenda por mais 12 semanas)
-- Ative a extensão pg_cron em Database > Extensions e rode:
--   select cron.schedule('estender-agenda', '0 3 * * 1', 'select public.extend_all_sessions()');
-- ---------------------------------------------------------------------
