"use client";
import { useState } from "react";
import { sb } from "../../lib/supabase";
import { becomePsychologist } from "../../lib/db";
import { Err, Field, useAction } from "./ui";

/** Entrar com e-mail + senha, código por e-mail ou Google. */
export function Login() {
  const [mode, setMode] = useState<"password" | "code" | "signup">("password");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [codeSent, setCodeSent] = useState(false);
  const [name, setName] = useState("");
  const [crp, setCrp] = useState("");
  const [info, setInfo] = useState<string | null>(null);
  const act = useAction();

  const google = () => act.run(async () => {
    const { error } = await sb().auth.signInWithOAuth({ provider: "google", options: { redirectTo: `${window.location.origin}/painel` } });
    if (error) throw error;
  });

  return (
    <div className="p-auth">
      <div className="p-card">
        <img src="/logo.svg" alt="" />
        <h1>Terapia — Desktop</h1>
        <p className="sub">Painel do psicólogo</p>
        {info && <p className="p-ok">{info}</p>}
        <Err msg={act.error} />

        <Field label="E-mail"><input type="email" value={email} onChange={(e) => setEmail(e.target.value.trim())} autoComplete="email" /></Field>

        {mode === "password" && <>
          <Field label="Senha"><input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password"
            onKeyDown={(e) => { if (e.key === "Enter") act.run(async () => { const { error } = await sb().auth.signInWithPassword({ email, password }); if (error) throw error; }); }} /></Field>
          <button className="pbtn" disabled={act.busy || !email || !password}
            onClick={() => act.run(async () => { const { error } = await sb().auth.signInWithPassword({ email, password }); if (error) throw error; })}>Entrar</button>
          <button className="link" onClick={() => { setMode("code"); setInfo(null); }}>Esqueci a senha / entrar com código</button>
          <button className="link" onClick={() => { setMode("signup"); setInfo(null); }}>Criar conta de psicólogo(a)</button>
        </>}

        {mode === "code" && <>
          {codeSent && <Field label="Código de 6 dígitos"><input inputMode="numeric" value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))} /></Field>}
          {!codeSent ? (
            <button className="pbtn" disabled={act.busy || !email} onClick={() => act.run(async () => {
              const { error } = await sb().auth.signInWithOtp({ email, options: { shouldCreateUser: false } });
              if (error) throw error;
              setCodeSent(true); setInfo(`Enviamos um código para ${email}`);
            })}>Enviar código</button>
          ) : (
            <button className="pbtn" disabled={act.busy || code.length !== 6} onClick={() => act.run(async () => {
              const r = await sb().auth.verifyOtp({ email, token: code, type: "email" });
              if (r.error) { const r2 = await sb().auth.verifyOtp({ email, token: code, type: "magiclink" }); if (r2.error) throw r2.error; }
            })}>Entrar com código</button>
          )}
          <button className="link" onClick={() => { setMode("password"); setCodeSent(false); }}>Usar senha</button>
        </>}

        {mode === "signup" && <>
          <Field label="Nome completo"><input value={name} onChange={(e) => setName(e.target.value)} /></Field>
          <Field label="CRP"><input value={crp} onChange={(e) => setCrp(e.target.value)} placeholder="06/123456" /></Field>
          <Field label="Senha (mín. 6 caracteres)"><input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" /></Field>
          <button className="pbtn" disabled={act.busy || !name || !crp || !email || password.length < 6} onClick={() => act.run(async () => {
            const { data, error } = await sb().auth.signUp({ email, password, options: { data: { full_name: name }, emailRedirectTo: `${window.location.origin}/painel` } });
            if (error) throw error;
            if (data.session) await becomePsychologist(name, crp);
            else { setInfo("Conta criada! Confirme pelo link enviado ao seu e-mail e depois entre."); setMode("password"); }
          })}>Criar conta</button>
          <button className="link" onClick={() => setMode("password")}>Já tenho conta</button>
        </>}

        <hr style={{ border: 0, borderTop: "1px solid var(--border)", margin: "14px 0" }} />
        <button className="pbtn ghost" onClick={google} disabled={act.busy}>G&nbsp; Entrar com o Google</button>
        <p className="muted" style={{ textAlign: "center", marginTop: 14 }}><a href="/">← Voltar ao site</a></p>
      </div>
    </div>
  );
}

/** Conta sem perfil definido: completar como psicólogo. */
export function CompleteProfile({ onDone }: { onDone: () => void }) {
  const [name, setName] = useState("");
  const [crp, setCrp] = useState("");
  const act = useAction();
  return (
    <div className="p-auth">
      <div className="p-card">
        <img src="/logo.svg" alt="" />
        <h1>Complete seu cadastro</h1>
        <p className="sub">O painel desktop é exclusivo para psicólogos. Pacientes usam o app no celular.</p>
        <Err msg={act.error} />
        <Field label="Nome completo"><input value={name} onChange={(e) => setName(e.target.value)} /></Field>
        <Field label="CRP"><input value={crp} onChange={(e) => setCrp(e.target.value)} /></Field>
        <button className="pbtn" disabled={act.busy || !name || !crp} onClick={async () => { if (await act.run(() => becomePsychologist(name, crp))) onDone(); }}>Continuar</button>
        <button className="link" onClick={() => sb().auth.signOut()}>Sair</button>
      </div>
    </div>
  );
}
