-- =====================================================================
-- Terapia — endurecimento de permissões (sugerido pelo Security Advisor do Supabase)
-- 1) Quem não está logado (anon) não acessa nenhuma tabela nem função.
-- 2) Funções internas (gatilhos e geração de agenda) não podem ser chamadas pela API.
--    Elas continuam funcionando normalmente dentro dos gatilhos.
-- =====================================================================

-- 1) Nada para visitantes não logados
revoke all on all tables    in schema public from anon;
revoke execute on all functions in schema public from anon;
alter default privileges in schema public revoke all on tables from anon;
alter default privileges in schema public revoke execute on functions from anon;

-- 2) Funções internas: só o próprio banco usa
revoke execute on function public.fill_slot_sessions(uuid, int)   from authenticated, public;
revoke execute on function public.extend_all_sessions()           from authenticated, public;
revoke execute on function public.effective_patient_limit(uuid)   from authenticated, public;
revoke execute on function public.handle_new_user()               from authenticated, public;
revoke execute on function public.check_patient_limit()           from authenticated, public;
revoke execute on function public.on_slot_changed()               from authenticated, public;
revoke execute on function public.detach_edited_session()         from authenticated, public;
revoke execute on function public.notify_on_change_request()      from authenticated, public;
revoke execute on function public.notify_on_emergency()           from authenticated, public;
revoke execute on function public.notify_on_message()             from authenticated, public;
revoke execute on function public.notify_on_mood_share()          from authenticated, public;

-- Funções que o app/painel chama (continuam liberadas só para usuários logados)
revoke execute on function public.become_psychologist(text, text) from public;
revoke execute on function public.delete_my_account()             from public;
revoke execute on function public.has_desktop_access()            from public;
revoke execute on function public.start_desktop_trial()           from public;
revoke execute on function public.is_psychologist_of(uuid)        from public;
revoke execute on function public.is_patient_self(uuid)           from public;
revoke execute on function public.can_access_patient(uuid)        from public;
grant execute on function public.become_psychologist(text, text) to authenticated;
grant execute on function public.delete_my_account()             to authenticated;
grant execute on function public.has_desktop_access()            to authenticated;
grant execute on function public.start_desktop_trial()           to authenticated;
grant execute on function public.is_psychologist_of(uuid)        to authenticated;  -- usadas nas regras de RLS
grant execute on function public.is_patient_self(uuid)           to authenticated;
grant execute on function public.can_access_patient(uuid)        to authenticated;
