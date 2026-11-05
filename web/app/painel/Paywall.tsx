"use client";
import { useState } from "react";
import { sb } from "../../lib/supabase";
import { startDesktopTrial, stripeRedirect } from "../../lib/db";
import { dateBR } from "../../lib/fmt";
import type { Profile } from "../../lib/types";
import { Err, useAction } from "./ui";

export const PRICE = "R$ 14,99";

/** Tela de acesso: iniciar 7 dias grátis ou assinar. */
export function Paywall({ profile, onAccess }: { profile: Profile; onAccess: () => void }) {
  const act = useAction();
  const trialUsed = Boolean(profile.desktop_trial_until);
  const [returned] = useState(() => typeof window !== "undefined" ? new URLSearchParams(window.location.search).get("pagamento") : null);

  return (
    <div className="p-auth">
      <div className="p-card">
        <img src="/logo.svg" alt="" />
        <h1>Terapia Desktop</h1>
        <p className="sub">Olá, {profile.full_name ?? "psicólogo(a)"}! Use o Terapia no computador.</p>
        <div className="price">{PRICE}<small> /mês</small></div>
        <ul className="perks">
          <li>Todos os pacientes, prontuário, anexos e PDF na tela grande</li>
          <li>Agenda, visão semanal e avisos dos pacientes</li>
          <li>Chat e pop-up de emergência em tempo real</li>
          <li>Cancele quando quiser</li>
        </ul>
        {returned === "ok" && <p className="p-ok">Pagamento recebido! A liberação leva alguns segundos… <button className="link" style={{ width: "auto" }} onClick={onAccess}>Atualizar</button></p>}
        {returned === "cancelado" && <p className="muted">Pagamento não concluído.</p>}
        <Err msg={act.error} />
        {!trialUsed ? (
          <>
            <button className="pbtn" disabled={act.busy} onClick={async () => { if (await act.run(startDesktopTrial)) onAccess(); }}>
              Começar 7 dias grátis (sem cartão)
            </button>
            <button className="link" disabled={act.busy} onClick={() => act.run(() => stripeRedirect("checkout"))}>Assinar agora</button>
          </>
        ) : (
          <>
            <p className="muted" style={{ textAlign: "center" }}>Seu teste grátis terminou em {dateBR(profile.desktop_trial_until!)}.</p>
            <button className="pbtn" disabled={act.busy} onClick={() => act.run(() => stripeRedirect("checkout"))}>Assinar por {PRICE}/mês</button>
            {profile.stripe_customer_id && (
              <button className="link" disabled={act.busy} onClick={() => act.run(() => stripeRedirect("portal"))}>Gerenciar assinatura / atualizar cartão</button>
            )}
          </>
        )}
        <p className="muted" style={{ textAlign: "center", fontSize: ".8rem" }}>Pagamento seguro processado pelo Stripe. O app no celular continua funcionando normalmente.</p>
        <button className="link" onClick={() => sb().auth.signOut()}>Sair</button>
      </div>
    </div>
  );
}
