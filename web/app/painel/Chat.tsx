"use client";
import { useEffect, useRef, useState } from "react";
import { sb } from "../../lib/supabase";
import { markMessagesRead, messages as loadMessages, patient, sendMessage } from "../../lib/db";
import { errorMessage, relative } from "../../lib/fmt";
import type { Message } from "../../lib/types";
import type { Go } from "./Painel";
import { Err } from "./ui";

/** Chat em tempo real com o paciente (o paciente recebe push no celular). */
export function Chat({ patientId, myId, go }: { patientId: string; myId: string; go: Go }) {
  const [list, setList] = useState<Message[]>([]);
  const [name, setName] = useState("");
  const [text, setText] = useState("");
  const [error, setError] = useState<string | null>(null);
  const bottom = useRef<HTMLDivElement>(null);

  useEffect(() => {
    patient(patientId).then((p) => setName(p.full_name)).catch(() => {});
    loadMessages(patientId).then(setList).catch((e) => setError(errorMessage(e)));
    markMessagesRead(patientId).catch(() => {});
    const ch = sb().channel(`chat-${patientId}`)
      .on("postgres_changes", { event: "INSERT", schema: "public", table: "messages", filter: `patient_id=eq.${patientId}` }, (payload) => {
        const m = payload.new as Message;
        setList((l) => (l.some((x) => x.id === m.id) ? l : [...l, m]));
        if (m.sender_id !== myId) markMessagesRead(patientId).catch(() => {});
      }).subscribe();
    return () => { sb().removeChannel(ch); };
  }, [patientId, myId]);

  useEffect(() => { bottom.current?.scrollIntoView({ behavior: "smooth" }); }, [list.length]);

  const send = async () => {
    const body = text.trim();
    if (!body) return;
    setText("");
    try { await sendMessage(patientId, body); setList(await loadMessages(patientId)); }
    catch (e) { setError(errorMessage(e)); setText(body); }
  };

  return (
    <>
      <div className="p-head">
        <button className="icon-btn" onClick={() => go({ name: "patient", id: patientId })}>←</button>
        <h1>{name || "Mensagens"}</h1>
      </div>
      <div className="p-chat">
        <div className="p-chat-list">
          {!list.length && <p className="muted" style={{ textAlign: "center" }}>Nenhuma mensagem ainda.</p>}
          {list.map((m) => (
            <div key={m.id} className={`bubble ${m.sender_id === myId ? "mine" : ""}`}>
              {m.body}<small>{relative(m.created_at)}{m.sender_id === myId && m.read_at ? " ✓✓" : ""}</small>
            </div>
          ))}
          <div ref={bottom} />
        </div>
        <Err msg={error} />
        <div className="p-chat-input">
          <textarea rows={2} value={text} placeholder="Mensagem (Enter envia, Shift+Enter quebra linha)" onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); send(); } }} />
          <button className="pbtn" onClick={send} disabled={!text.trim()}>Enviar</button>
        </div>
      </div>
    </>
  );
}
