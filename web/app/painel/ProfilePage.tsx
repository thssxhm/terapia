"use client";
import { useState } from "react";
import { sb } from "../../lib/supabase";
import { stripeRedirect, updateMyProfile } from "../../lib/db";
import { dateBR } from "../../lib/fmt";
import type { Profile } from "../../lib/types";
import { PRICE } from "./Paywall";
import { patientLimit } from "./Patients";
import { Err, Field, useAction } from "./ui";

const STATUS: Record<string, string> = {
  active: "Ativa", trialing: "Em teste", past_due: "Pagamento pendente", canceled: "Cancelada", unpaid: "Não paga", incomplete: "Incompleta", paused: "Pausada",
};

export function ProfilePage({ profile, onChanged }: { profile: Profile; onChanged: () => void }) {
  const [name, setName] = useState(profile.full_name ?? "");
  const [crp, setCrp] = useState(profile.crp ?? "");
  const [phone, setPhone] = useState(profile.phone ?? "");
  const [pw, setPw] = useState("");
  const [ok, setOk] = useState<string | null>(null);
  const act = useAction();
  const pay = useAction();
  const subscribed = profile.desktop_status && ["active", "trialing", "past_due"].includes(profile.desktop_status);

  return (
    <>
      <div className="p-head"><h1>Perfil e assinatura</h1></div>
      <div className="p-grid">
        <div className="p-card">
          <h3>Seus dados</h3>
          <Field label="Nome"><input value={name} onChange={(e) => setName(e.target.value)} /></Field>
          <Field label="CRP"><input value={crp} onChange={(e) => setCrp(e.target.value)} /></Field>
          <Field label="Telefone"><input value={phone} onChange={(e) => setPhone(e.target.value)} /></Field>
          <p className="muted">E-mail: {profile.email}</p>
          <button className="pbtn" disabled={act.busy} onClick={async () => { if (await act.run(() => updateMyProfile(name, crp, phone || null))) { setOk("Dados salvos ✔"); onChanged(); } }}>Salvar</button>
          <hr style={{ border: 0, borderTop: "1px solid var(--border)", margin: "18px 0" }} />
          <Field label="Nova senha"><input type="password" value={pw} onChange={(e) => setPw(e.target.value)} autoComplete="new-password" /></Field>
          <button className="pbtn ghost" disabled={act.busy || pw.length < 6} onClick={async () => {
            if (await act.run(async () => { const { error } = await sb().auth.updateUser({ password: pw }); if (error) throw error; })) { setPw(""); setOk("Senha alterada ✔"); }
          }}>Alterar senha</button>
          {ok && <p className="p-ok">{ok}</p>}
          <Err msg={act.error} />
        </div>

        <div className="p-card">
          <h3>Assinatura Desktop — {PRICE}/mês</h3>
          {subscribed ? (
            <p>Status: <b>{STATUS[profile.desktop_status!] ?? profile.desktop_status}</b>
              {profile.desktop_period_end && <> • renova em {dateBR(profile.desktop_period_end)}</>}</p>
          ) : profile.desktop_trial_until ? (
            <p>Teste grátis até <b>{dateBR(profile.desktop_trial_until)}</b>.</p>
          ) : null}
          {profile.desktop_status === "past_due" && <p className="p-err">Não conseguimos cobrar seu cartão. Atualize a forma de pagamento para não perder o acesso.</p>}
          <Err msg={pay.error} />
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            {!subscribed && <button className="pbtn" disabled={pay.busy} onClick={() => pay.run(() => stripeRedirect("checkout"))}>Assinar agora</button>}
            {profile.stripe_customer_id && <button className="pbtn ghost" disabled={pay.busy} onClick={() => pay.run(() => stripeRedirect("portal"))}>
              Gerenciar: cartão, faturas e cancelamento</button>}
          </div>
          <p className="muted" style={{ marginTop: 12 }}>Pagamento seguro pelo Stripe. A assinatura desktop é separada do plano do app.</p>
          <hr style={{ border: 0, borderTop: "1px solid var(--border)", margin: "18px 0" }} />
          <h3>Plano de pacientes (app)</h3>
          <p>Limite atual: <b>{patientLimit(profile)} pacientes</b>. Para ampliar (de 10 em 10), use o app no celular: Perfil → Plano.</p>
        </div>
      </div>
    </>
  );
}
