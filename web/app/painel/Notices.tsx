"use client";
import { useState } from "react";
import { answerRequest, deleteNotification, markAllRead, markNotificationRead, notifications, request, session, setSessionStatus } from "../../lib/db";
import { relative } from "../../lib/fmt";
import type { AppNotification, Session } from "../../lib/types";
import type { Go } from "./Painel";
import { SessionModal } from "./Schedule";
import { Async, Empty, Err, Modal, useAction, useAsync } from "./ui";

export function Notices({ go, onChanged }: { go: Go; onChanged: () => void }) {
  const state = useAsync(notifications, []);
  const [req, setReq] = useState<AppNotification | null>(null);
  const open = async (n: AppNotification) => {
    await markNotificationRead(n.id).catch(() => {});
    onChanged();
    if (n.type === "request") return setReq(n);
    if (!n.patient_id) return;
    if (n.type === "message" || n.type.startsWith("emergency")) go({ name: "chat", id: n.patient_id });
    else go({ name: "patient", id: n.patient_id, tab: n.type === "mood" ? 6 : 5 });
  };
  return (
    <>
      <div className="p-head"><h1>Avisos dos pacientes</h1><div className="grow" />
        <button className="pbtn ghost small" onClick={async () => { await markAllRead(); state.reload(); onChanged(); }}>✓ Marcar todas como lidas</button></div>
      <Async state={state}>{(list) => (
        <div className="p-list">
          {!list.length && <Empty>Nenhum aviso por enquanto.</Empty>}
          {list.map((n) => (
            <div key={n.id} className={`p-row click ${n.read_at ? "" : "unread"}`} onClick={() => open(n)}
              style={n.type === "emergency_red" ? { borderLeft: "5px solid var(--red)" } : n.type === "emergency_yellow" ? { borderLeft: "5px solid var(--yellow)" } : undefined}>
              <div className="grow">
                <b style={{ fontWeight: n.read_at ? 600 : 800 }}>{n.title}</b>
                {n.body && <div>{n.body}</div>}
                <div className="muted">{relative(n.created_at)}</div>
              </div>
              {n.type === "request" && <span className="chip">Responder</span>}
              <button className="icon-btn" title="Apagar" onClick={async (e) => { e.stopPropagation(); await deleteNotification(n.id); state.reload(); onChanged(); }}>🗑️</button>
            </div>
          ))}
        </div>
      )}</Async>
      {req && <RequestModal n={req} onClose={() => { setReq(null); state.reload(); }} />}
    </>
  );
}

/** Aceitar, recusar ou remarcar um pedido de cancelamento/ajuste. */
function RequestModal({ n, onClose }: { n: AppNotification; onClose: () => void }) {
  const state = useAsync(() => (n.ref_id ? request(n.ref_id) : Promise.resolve(null)), [n.ref_id]);
  const [resched, setResched] = useState<Session | null>(null);
  const act = useAction();
  const r = state.data;
  return (
    <>
      <Modal title={n.title} onClose={onClose} footer={<button className="pbtn ghost" onClick={onClose}>Fechar</button>}>
        {n.body && <p>{n.body}</p>}
        {r && <p><b>Situação:</b> {r.status === "accepted" ? "aceito" : r.status === "declined" ? "recusado" : "pendente"}</p>}
        <Err msg={act.error} />
        {r?.status === "pending" && (
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            {r.kind === "cancel" && <button className="pbtn" disabled={act.busy} onClick={async () => {
              if (await act.run(async () => { if (r.session_id) await setSessionStatus(r.session_id, "cancelled"); await answerRequest(r.id, true); })) onClose();
            }}>Aceitar e cancelar sessão</button>}
            {r.kind === "reschedule" && r.session_id && <button className="pbtn" disabled={act.busy}
              onClick={() => act.run(async () => setResched(await session(r.session_id!)))}>Remarcar sessão</button>}
            <button className="pbtn ghost" disabled={act.busy} onClick={async () => { if (await act.run(() => answerRequest(r.id, false))) onClose(); }}>Recusar</button>
          </div>
        )}
      </Modal>
      {resched && r && <SessionModal session={resched} onClose={() => setResched(null)}
        onSaved={async () => { setResched(null); await answerRequest(r.id, true); onClose(); }} />}
    </>
  );
}
