-- =====================================================================
-- Terapia — acesso ao painel desktop (assinatura Stripe R$ 14,99/mês)
-- 7 dias grátis contados a partir do primeiro acesso ao painel.
-- Estas colunas NÃO podem ser alteradas pelo usuário (só pelo servidor/webhook),
-- porque a migração inicial só liberou UPDATE em full_name, crp, phone e timezone.
-- =====================================================================

alter table public.profiles
  add column if not exists desktop_trial_until  timestamptz,
  add column if not exists stripe_customer_id   text unique,
  add column if not exists desktop_status       text,          -- status da assinatura no Stripe
  add column if not exists desktop_period_end   timestamptz;   -- fim do período pago atual

-- Tem acesso ao desktop? (teste grátis ativo OU assinatura em dia)
create or replace function public.has_desktop_access()
returns boolean language sql stable security definer set search_path = public as $$
  select coalesce((
    select role = 'psychologist' and (
      desktop_trial_until > now()
      or (desktop_status in ('active', 'trialing', 'past_due')
          and coalesce(desktop_period_end, now() + interval '1 day') > now())
    )
    from public.profiles where id = auth.uid()
  ), false);
$$;

-- Inicia os 7 dias grátis (só uma vez por conta) e devolve a data de término
create or replace function public.start_desktop_trial()
returns timestamptz language plpgsql security definer set search_path = public as $$
declare v timestamptz;
begin
  update public.profiles
     set desktop_trial_until = now() + interval '7 days'
   where id = auth.uid() and role = 'psychologist' and desktop_trial_until is null;
  select desktop_trial_until into v from public.profiles where id = auth.uid();
  return v;
end $$;
