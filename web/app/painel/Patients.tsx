"use client";
import { useState } from "react";
import { invitePatient, myPatients, type InviteResult } from "../../lib/db";
import { hhmm, WEEKDAYS, WEEKDAYS_SHORT } from "../../lib/fmt";
import type { Patient, Profile, Slot } from "../../lib/types";
import type { Go } from "./Painel";
import { Async, Empty, Err, Field, Modal, useAction, useAsync } from "./ui";

/** Limite de pacientes: plano do app (Play Store) ou 10 no teste grátis do app. */
export function patientLimit(p: Profile) {
  const trial = p.trial_until && new Date(p.trial_until) > new Date() ? 10 : 0;
  return Math.max(p.patient_limit ?? 0, trial);
}

export function Patients({ profile, go }: { profile: Profile; go: Go }) {
  const state = useAsync(myPatients, []);
  const [query, setQuery] = useState("");
  const [adding, setAdding] = useState(false);
  const [invite, setInvite] = useState<InviteResult | null>(null);
  const limit = patientLimit(profile);

  return (
    <>
      <div className="p-head">
        <h1>Pacientes</h1><div className="grow" />
        <input className="p-input" style={{ maxWidth: 260 }} placeholder="Buscar paciente" value={query} onChange={(e) => setQuery(e.target.value)} />
        <button className="pbtn" onClick={() => setAdding(true)} disabled={(state.data?.length ?? 0) >= limit}>+ Novo paciente</button>
      </div>
      <Async state={state}>{(list) => (
        <>
          <div className="p-card" style={{ marginBottom: 16 }}>
            <b>{list.length} de {limit} pacientes do seu plano</b>
            <div className="p-progress"><div style={{ width: `${limit ? Math.min(100, (list.length / limit) * 100) : 100}%` }} /></div>
            {list.length >= limit && <p className="muted">Limite atingido. O plano de pacientes é ampliado pelo app (Perfil → Plano).</p>}
          </div>
          {list.length === 0 && <Empty>Nenhum paciente ainda. Clique em “Novo paciente”.</Empty>}
          <div className="p-grid">
            {list.filter((p) => p.full_name.toLowerCase().includes(query.toLowerCase())).map((p) => (
              <div key={p.id} className="p-row click" onClick={() => go({ name: "patient", id: p.id })}>
                <div className="grow">
                  <b>{p.full_name}</b>
                  <div className="muted">{slotsText(p.schedule_slots)}</div>
                </div>
                <button className="icon-btn" title="Mensagens" onClick={(e) => { e.stopPropagation(); go({ name: "chat", id: p.id }); }}>💬</button>
              </div>
            ))}
          </div>
        </>
      )}</Async>
      {adding && <PatientForm onClose={() => setAdding(false)} onCreated={(r) => { setAdding(false); setInvite(r); state.reload(); }} />}
      {invite && <InviteDone res={invite} profile={profile} onClose={() => setInvite(null)} />}
    </>
  );
}

export function slotsText(slots?: Slot[]) {
  const s = [...(slots ?? [])].sort((a, b) => a.weekday - b.weekday || a.start_time.localeCompare(b.start_time));
  return s.length ? s.map((x) => `${WEEKDAYS_SHORT[x.weekday]} ${hhmm(x.start_time)}`).join(" • ") : "Sem horário fixo";
}

function PatientForm({ onClose, onCreated }: { onClose: () => void; onCreated: (r: InviteResult) => void }) {
  const [f, setF] = useState({ full_name: "", age: "", phone: "", email: "" });
  const [slots, setSlots] = useState<Slot[]>([]);
  const act = useAction();
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value });

  const submit = () => act.run(async () => {
    const r = await invitePatient({
      full_name: f.full_name.trim(), age: f.age ? Number(f.age) : null, phone: f.phone || null,
      email: f.email.trim().toLowerCase(), slots: slots.map((s) => ({ ...s, start_time: s.start_time.length === 5 ? s.start_time + ":00" : s.start_time })),
    });
    if (r.error) throw new Error(r.error);
    onCreated(r);
  });

  return (
    <Modal title="Novo paciente" wide onClose={onClose} footer={<>
      <button className="pbtn ghost" onClick={onClose}>Cancelar</button>
      <button className="pbtn" disabled={act.busy || !f.full_name || !f.email.includes("@")} onClick={submit}>
        {act.busy ? "Enviando…" : "Cadastrar e enviar convite"}</button>
    </>}>
      <div className="p-two">
        <Field label="Nome completo *"><input value={f.full_name} onChange={set("full_name")} /></Field>
        <Field label="E-mail *" hint="O paciente recebe aqui o código de acesso e o link do app"><input type="email" value={f.email} onChange={set("email")} /></Field>
        <Field label="Idade"><input type="number" min={0} value={f.age} onChange={set("age")} /></Field>
        <Field label="Telefone"><input value={f.phone} onChange={set("phone")} /></Field>
      </div>
      <h4 style={{ margin: "8px 0" }}>Dias e horários das sessões</h4>
      {slots.map((s, i) => (
        <div className="p-three" key={i}>
          <Field label="Dia"><select value={s.weekday} onChange={(e) => setSlots(slots.map((x, j) => j === i ? { ...x, weekday: Number(e.target.value) } : x))}>
            {WEEKDAYS.slice(1).map((d, k) => <option key={d} value={k + 1}>{d}</option>)}</select></Field>
          <Field label="Horário"><input type="time" value={hhmm(s.start_time)} onChange={(e) => setSlots(slots.map((x, j) => j === i ? { ...x, start_time: e.target.value } : x))} /></Field>
          <Field label="Duração"><select value={s.duration_minutes} onChange={(e) => setSlots(slots.map((x, j) => j === i ? { ...x, duration_minutes: Number(e.target.value) } : x))}>
            {[30, 40, 45, 50, 60, 90].map((m) => <option key={m} value={m}>{m} min</option>)}</select></Field>
          <button className="icon-btn" style={{ marginBottom: 14 }} onClick={() => setSlots(slots.filter((_, j) => j !== i))} title="Remover">🗑️</button>
        </div>
      ))}
      <button className="pbtn ghost small" onClick={() => setSlots([...slots, { weekday: 1, start_time: "14:00", duration_minutes: 50 }])}>+ Adicionar dia/horário</button>
      <Err msg={act.error} />
    </Modal>
  );
}

function InviteDone({ res, profile, onClose }: { res: InviteResult; profile: Profile; onClose: () => void }) {
  const link = res.play_store_url || "https://play.google.com/store/apps/details?id=app.terapia.mobile";
  const msg = `Olá, ${res.patient?.full_name ?? ""}! ${profile.full_name} (CRP ${profile.crp}) liberou seu acesso ao app Terapia.\n\n` +
    `1. Instale: ${link}\n2. Abra o app, toque em "Sou paciente" e informe seu e-mail (${res.patient?.email ?? ""}).\n3. Digite o código que chegou no seu e-mail.`;
  const [copied, setCopied] = useState("");
  const copy = async (t: string, what: string) => { await navigator.clipboard.writeText(t); setCopied(what); };
  return (
    <Modal title="Paciente cadastrado ✔" onClose={onClose} footer={<button className="pbtn" onClick={onClose}>Concluir</button>}>
      <p>{res.email_sent ? `Enviamos o código de acesso e o link do app para ${res.patient?.email}.`
        : "Não foi possível enviar o e-mail automaticamente. Envie a mensagem abaixo e peça para o paciente tocar em “Enviar novo código” no app."}</p>
      <Field label="Link de instalação"><input readOnly value={link} /></Field>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <button className="pbtn ghost small" onClick={() => copy(link, "link")}>Copiar link</button>
        <button className="pbtn ghost small" onClick={() => copy(msg, "mensagem")}>Copiar mensagem</button>
        {res.patient?.phone && <a className="pbtn small" target="_blank" rel="noopener"
          href={`https://wa.me/55${res.patient.phone.replace(/\D/g, "").replace(/^55/, "")}?text=${encodeURIComponent(msg)}`}>Enviar pelo WhatsApp</a>}
      </div>
      {copied && <p className="p-ok">Copiado: {copied}</p>}
    </Modal>
  );
}
