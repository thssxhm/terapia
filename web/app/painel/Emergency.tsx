"use client";
import { useEffect, useState } from "react";
import { sb } from "../../lib/supabase";
import { acknowledgeEmergency, emergency, pendingEmergencies } from "../../lib/db";
import { relative } from "../../lib/fmt";
import type { Emergency } from "../../lib/types";
import type { Go } from "./Painel";
import { Modal } from "./ui";

/** Escuta emergências em tempo real e mostra o pop-up amarelo/vermelho (+ notificação do navegador). */
export function EmergencyWatcher({ go }: { go: Go }) {
  const [queue, setQueue] = useState<Emergency[]>([]);

  useEffect(() => {
    const add = (e: Emergency) => setQueue((q) => (q.some((x) => x.id === e.id) ? q : [...q, e]));
    pendingEmergencies().then((l) => l.forEach(add)).catch(() => {});
    if ("Notification" in window && Notification.permission === "default") Notification.requestPermission().catch(() => {});
    const ch = sb().channel("web-emergencies")
      .on("postgres_changes", { event: "INSERT", schema: "public", table: "emergencies" }, async (payload) => {
        const e = (await emergency((payload.new as Emergency).id).catch(() => null)) ?? (payload.new as Emergency);
        add(e);
        if ("Notification" in window && Notification.permission === "granted") {
          new Notification(e.level === "red" ? "🔴 EMERGÊNCIA — muito urgente" : "🟡 Emergência — pouco urgente", {
            body: `${e.patients?.full_name ?? "Paciente"}: ${e.message}`, requireInteraction: true, tag: e.id,
          });
        }
      }).subscribe();
    return () => { sb().removeChannel(ch); };
  }, []);

  const e = queue[0];
  if (!e) return null;
  const done = () => { acknowledgeEmergency(e.id).catch(() => {}); setQueue((q) => q.slice(1)); };
  const red = e.level === "red";
  return (
    <Modal tone={red ? "red" : "yellow"} title={red ? "⚠️ EMERGÊNCIA — muito urgente" : "⚠️ Emergência — pouco urgente"} onClose={done}
      footer={<>
        <button className="pbtn ghost" onClick={done}>Ciente</button>
        <button className="pbtn ghost" style={{ fontWeight: 800 }} onClick={() => { done(); go({ name: "chat", id: e.patient_id }); }}>💬 Responder</button>
      </>}>
      <p style={{ fontSize: "1.15rem", margin: 0 }}><b>{e.patients?.full_name ?? "Paciente"}</b></p>
      <p className="muted" style={{ marginTop: 0 }}>{relative(e.created_at)}</p>
      <p style={{ fontSize: "1.1rem", whiteSpace: "pre-wrap" }}>{e.message}</p>
      {queue.length > 1 && <p className="muted">+{queue.length - 1} outra(s) emergência(s) na fila</p>}
    </Modal>
  );
}
