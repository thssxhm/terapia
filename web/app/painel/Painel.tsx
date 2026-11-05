"use client";
import { useCallback, useEffect, useState } from "react";
import type { Session as AuthSession } from "@supabase/supabase-js";
import { loadSupabaseConfig, sb } from "../../lib/supabase";
import { hasDesktopAccess, myProfile, notifications } from "../../lib/db";
import type { Profile } from "../../lib/types";
import ThemeToggle from "../ThemeToggle";
import { CompleteProfile, Login } from "./Login";
import { Paywall } from "./Paywall";
import { Patients } from "./Patients";
import { PatientDetail } from "./PatientDetail";
import { Week, Agenda } from "./Schedule";
import { Notices } from "./Notices";
import { Chat } from "./Chat";
import { ProfilePage } from "./ProfilePage";
import { PrintView } from "./PrintView";
import { EmergencyWatcher } from "./Emergency";

export type View =
  | { name: "patients" } | { name: "week" } | { name: "agenda" } | { name: "notices" } | { name: "profile" }
  | { name: "patient"; id: string; tab?: number } | { name: "chat"; id: string } | { name: "print"; id: string };

export type Go = (v: View) => void;

export default function Painel() {
  const [session, setSession] = useState<AuthSession | null | undefined>(undefined);
  const [configured, setConfigured] = useState<boolean | undefined>(undefined);

  useEffect(() => {
    let unsub: (() => void) | undefined;
    loadSupabaseConfig().then((ok) => {
      setConfigured(ok);
      if (!ok) return;
      sb().auth.getSession().then(({ data }) => setSession(data.session));
      const { data } = sb().auth.onAuthStateChange((_e, s) => setSession(s));
      unsub = () => data.subscription.unsubscribe();
    });
    return () => unsub?.();
  }, []);

  if (configured === undefined) return <div className="p-loading">Carregando…</div>;
  if (!configured) {
    return <div className="p-auth"><div className="p-card"><h1>Painel em configuração</h1>
      <p className="sub">Configure as variáveis do Supabase na Cloudflare (veja o README).</p></div></div>;
  }
  if (session === undefined) return <div className="p-loading">Carregando…</div>;
  if (!session) return <Login />;
  return <Gate key={session.user.id} />;
}

/** Verifica perfil e assinatura antes de mostrar o painel. */
function Gate() {
  const [profile, setProfile] = useState<Profile | null | undefined>(undefined);
  const [access, setAccess] = useState<boolean | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const p = await myProfile();
      setProfile(p);
      setAccess(p?.role === "psychologist" ? await hasDesktopAccess() : false);
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
  }, []);
  useEffect(() => { load(); }, [load]);

  // Voltando do pagamento: o webhook do Stripe pode levar alguns segundos — verifica de novo
  useEffect(() => {
    if (access !== false || typeof window === "undefined") return;
    if (new URLSearchParams(window.location.search).get("pagamento") !== "ok") return;
    const t = setInterval(load, 4000);
    const stop = setTimeout(() => clearInterval(t), 60000);
    return () => { clearInterval(t); clearTimeout(stop); };
  }, [access, load]);

  if (error) return <div className="p-error">{error} <button className="pbtn" onClick={load}>Tentar novamente</button></div>;
  if (profile === undefined || access === undefined) return <div className="p-loading">Carregando…</div>;
  if (!profile || !profile.role) return <CompleteProfile onDone={load} />;
  if (profile.role !== "psychologist") {
    return <div className="p-auth"><div className="p-card"><h1>Área do paciente</h1>
      <p className="sub">O painel desktop é exclusivo para psicólogos. Use o app Terapia no celular.</p>
      <button className="pbtn" onClick={() => sb().auth.signOut()}>Sair</button></div></div>;
  }
  if (!access) return <Paywall profile={profile} onAccess={load} />;
  return <Shell profile={profile} reloadProfile={load} />;
}

function Shell({ profile, reloadProfile }: { profile: Profile; reloadProfile: () => void }) {
  const [view, setView] = useState<View>({ name: "patients" });
  const [unread, setUnread] = useState(0);
  const go: Go = useCallback((v) => { setView(v); window.scrollTo(0, 0); }, []);

  const refreshUnread = useCallback(() => {
    notifications().then((l) => setUnread(l.filter((n) => !n.read_at).length)).catch(() => {});
  }, []);
  useEffect(() => {
    refreshUnread();
    // Limpa ?pagamento=ok da barra de endereço
    if (window.location.search) window.history.replaceState(null, "", "/painel");
    const ch = sb().channel("web-notices")
      .on("postgres_changes", { event: "INSERT", schema: "public", table: "notifications" }, () => refreshUnread())
      .subscribe();
    return () => { sb().removeChannel(ch); };
  }, [refreshUnread]);

  if (view.name === "print") return <PrintView patientId={view.id} profile={profile} onBack={() => go({ name: "patient", id: view.id })} />;

  const nav: [View["name"], string, string][] = [
    ["patients", "👥", "Pacientes"], ["week", "🗓️", "Semana"], ["agenda", "📅", "Agenda"], ["notices", "🔔", "Avisos"], ["profile", "⚙️", "Perfil"],
  ];
  const trialDays = profile.desktop_status !== "active" && profile.desktop_trial_until
    ? Math.ceil((new Date(profile.desktop_trial_until).getTime() - Date.now()) / 86400000) : null;

  return (
    <div className="p-app">
      <aside className="p-side">
        <div className="p-brand"><img src="/logo.svg" alt="" /><div><b>{profile.full_name}</b><small>CRP {profile.crp}</small></div></div>
        {nav.map(([name, icon, label]) => (
          <button key={name} className={`p-nav ${view.name === name || (name === "patients" && (view.name === "patient" || view.name === "chat")) ? "active" : ""}`}
            onClick={() => go({ name } as View)}>
            <span>{icon}</span>{label}{name === "notices" && unread > 0 && <span className="badge">{unread}</span>}
          </button>
        ))}
        <div className="spacer" />
        <div style={{ display: "flex", gap: 8, alignItems: "center" }}><ThemeToggle /><button className="pbtn ghost small" onClick={() => sb().auth.signOut()}>Sair</button></div>
      </aside>
      <main className="p-main">
        {trialDays !== null && trialDays > 0 && (
          <div className="p-banner">🎁 Teste grátis: {trialDays} dia(s) restante(s).
            <button className="pbtn small" onClick={() => go({ name: "profile" })}>Assinar</button></div>
        )}
        {view.name === "patients" && <Patients profile={profile} go={go} />}
        {view.name === "week" && <Week go={go} />}
        {view.name === "agenda" && <Agenda go={go} />}
        {view.name === "notices" && <Notices go={go} onChanged={refreshUnread} />}
        {view.name === "profile" && <ProfilePage profile={profile} onChanged={reloadProfile} />}
        {view.name === "patient" && <PatientDetail id={view.id} initialTab={view.tab ?? 0} go={go} />}
        {view.name === "chat" && <Chat patientId={view.id} myId={profile.id} go={go} />}
      </main>
      <EmergencyWatcher go={go} />
    </div>
  );
}
