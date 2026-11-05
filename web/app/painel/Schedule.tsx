"use client";
import { useState } from "react";
import { addSession, addSlot, deleteSession, deleteSlot, myPatients, sessionsBetween, updateSession, updateSlot } from "../../lib/db";
import { addDays, dateBR, hhmm, statusLabel, time, WEEKDAYS, ymd } from "../../lib/fmt";
import type { Session, Slot } from "../../lib/types";
import type { Go } from "./Painel";
import { Async, Confirm, Empty, Err, Field, Modal, useAction, useAsync } from "./ui";

// ---------- Visão semanal: dia da semana + horário de cada paciente ----------
export function Week({ go }: { go: Go }) {
  const state = useAsync(myPatients, []);
  return (
    <>
      <div className="p-head"><h1>Semana</h1></div>
      <Async state={state}>{(patients) => {
        const entries = patients.flatMap((p) => (p.schedule_slots ?? []).filter((s) => s.active !== false).map((s) => ({ p, s })));
        if (!entries.length) return <Empty>Cadastre horários fixos nos pacientes para ver a semana aqui.</Empty>;
        return (
          <div className="p-grid">
            {[1, 2, 3, 4, 5, 6, 7].map((d) => {
              const list = entries.filter((e) => e.s.weekday === d).sort((a, b) => a.s.start_time.localeCompare(b.s.start_time));
              if (!list.length) return null;
              return (
                <div className="p-card" key={d}>
                  <h3>{WEEKDAYS[d]}</h3>
                  {list.map(({ p, s }) => (
                    <div key={s.id} className="p-row click" style={{ marginBottom: 6 }} onClick={() => go({ name: "patient", id: p.id })}>
                      <b style={{ color: "var(--primary-strong)", width: 52 }}>{hhmm(s.start_time)}</b>
                      <span className="grow">{p.full_name}</span><span className="muted">{s.duration_minutes} min</span>
                    </div>
                  ))}
                </div>
              );
            })}
          </div>
        );
      }}</Async>
    </>
  );
}

// ---------- Agenda geral ----------
export function Agenda({ go }: { go: Go }) {
  const [past, setPast] = useState(false);
  const [editing, setEditing] = useState<Session | null>(null);
  const state = useAsync(async () => {
    const now = Date.now();
    return past
      ? (await sessionsBetween(new Date(now - 90 * 864e5), new Date(now))).reverse()
      : sessionsBetween(new Date(now - 2 * 36e5), new Date(now + 90 * 864e5));
  }, [past]);

  return (
    <>
      <div className="p-head">
        <h1>Agenda</h1><div className="grow" />
        <button className={`pbtn small ${past ? "ghost" : ""}`} onClick={() => setPast(false)}>Próximas</button>
        <button className={`pbtn small ${past ? "" : "ghost"}`} onClick={() => setPast(true)}>Anteriores</button>
      </div>
      <Async state={state}>{(list) => {
        if (!list.length) return <Empty>Nenhum atendimento.</Empty>;
        const days = new Map<string, Session[]>();
        list.forEach((s) => { const d = ymd(new Date(s.starts_at)); days.set(d, [...(days.get(d) ?? []), s]); });
        const today = ymd(new Date());
        return [...days.entries()].map(([d, ss]) => (
          <div className="p-day" key={d}>
            <h4>{d === today ? "Hoje" : d === addDays(today, 1) ? "Amanhã" : `${WEEKDAYS[((new Date(d + "T12:00").getDay() + 6) % 7) + 1]}, ${dateBR(d)}`}</h4>
            <div className="p-list">
              {ss.map((s) => (
                <div key={s.id} className={`p-row click ${s.status === "cancelled" ? "cancelled" : ""}`} onClick={() => setEditing(s)}>
                  <b style={{ width: 56 }}>{time(s.starts_at)}</b>
                  <div className="grow"><b>{s.patients?.full_name}</b><div className="muted">{statusLabel(s.status)} • {s.duration_minutes} min</div></div>
                  <button className="pbtn ghost small" onClick={(e) => { e.stopPropagation(); go({ name: "patient", id: s.patient_id }); }}>Ver paciente</button>
                </div>
              ))}
            </div>
          </div>
        ));
      }}</Async>
      {editing && <SessionModal session={editing} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); state.reload(); }} />}
    </>
  );
}

/** Editar data/horário/duração/status de uma sessão, ou criar sessão avulsa (session = null). */
export function SessionModal({ session, patientId, onClose, onSaved }: {
  session: Session | null; patientId?: string; onClose: () => void; onSaved: () => void;
}) {
  const start = session ? new Date(session.starts_at) : null;
  const [date, setDate] = useState(start ? ymd(start) : ymd(new Date()));
  const [hour, setHour] = useState(start ? time(session!.starts_at) : "14:00");
  const [duration, setDuration] = useState(session?.duration_minutes ?? 50);
  const [status, setStatus] = useState(session?.status ?? "scheduled");
  const [confirmDel, setConfirmDel] = useState(false);
  const act = useAction();
  const save = () => act.run(async () => {
    const iso = new Date(`${date}T${hour}:00`).toISOString();
    if (session) await updateSession(session.id, iso, duration, status);
    else await addSession(patientId!, iso, duration);
    onSaved();
  });
  return (
    <>
      <Modal title={session ? (session.patients?.full_name ?? "Editar sessão") : "Nova sessão avulsa"} onClose={onClose} footer={<>
        {session && <button className="pbtn ghost" style={{ marginRight: "auto", color: "var(--red)" }} onClick={() => setConfirmDel(true)}>Excluir</button>}
        <button className="pbtn ghost" onClick={onClose}>Cancelar</button>
        <button className="pbtn" disabled={act.busy} onClick={save}>Salvar</button>
      </>}>
        <div className="p-two">
          <Field label="Data"><input type="date" value={date} onChange={(e) => setDate(e.target.value)} /></Field>
          <Field label="Horário"><input type="time" value={hour} onChange={(e) => setHour(e.target.value)} /></Field>
          <Field label="Duração"><select value={duration} onChange={(e) => setDuration(Number(e.target.value))}>
            {[30, 40, 45, 50, 60, 90].map((m) => <option key={m} value={m}>{m} min</option>)}</select></Field>
          {session && <Field label="Status"><select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="scheduled">Agendada</option><option value="confirmed">Confirmada</option>
            <option value="done">Realizada</option><option value="cancelled">Cancelada</option></select></Field>}
        </div>
        <Err msg={act.error} />
      </Modal>
      {confirmDel && session && <Confirm title="Excluir sessão?" text="As anotações desta sessão também serão apagadas." confirm="Excluir" danger
        onConfirm={async () => { await deleteSession(session.id); onSaved(); }} onClose={() => setConfirmDel(false)} />}
    </>
  );
}

/** Criar/editar horário fixo semanal. */
export function SlotModal({ patientId, slot, onClose, onSaved }: { patientId: string; slot: Slot | null; onClose: () => void; onSaved: () => void }) {
  const [s, setS] = useState<Slot>(slot ?? { weekday: 1, start_time: "14:00", duration_minutes: 50 });
  const [confirmDel, setConfirmDel] = useState(false);
  const act = useAction();
  const save = () => act.run(async () => {
    const row = { ...s, start_time: hhmm(s.start_time) + ":00" };
    if (slot) await updateSlot(row); else await addSlot({ ...row, patient_id: patientId });
    onSaved();
  });
  return (
    <>
      <Modal title={slot ? "Editar horário fixo" : "Novo horário fixo"} onClose={onClose} footer={<>
        {slot && <button className="pbtn ghost" style={{ marginRight: "auto", color: "var(--red)" }} onClick={() => setConfirmDel(true)}>Excluir</button>}
        <button className="pbtn ghost" onClick={onClose}>Cancelar</button>
        <button className="pbtn" disabled={act.busy} onClick={save}>Salvar</button>
      </>}>
        <Field label="Dia da semana"><select value={s.weekday} onChange={(e) => setS({ ...s, weekday: Number(e.target.value) })}>
          {WEEKDAYS.slice(1).map((d, i) => <option key={d} value={i + 1}>{d}</option>)}</select></Field>
        <div className="p-two">
          <Field label="Horário"><input type="time" value={hhmm(s.start_time)} onChange={(e) => setS({ ...s, start_time: e.target.value })} /></Field>
          <Field label="Duração"><select value={s.duration_minutes} onChange={(e) => setS({ ...s, duration_minutes: Number(e.target.value) })}>
            {[30, 40, 45, 50, 60, 90].map((m) => <option key={m} value={m}>{m} min</option>)}</select></Field>
        </div>
        <p className="muted">As próximas sessões ainda não confirmadas são ajustadas automaticamente.</p>
        <Err msg={act.error} />
      </Modal>
      {confirmDel && slot && <Confirm title="Excluir horário?" text="As sessões futuras deste horário que não foram confirmadas serão removidas." confirm="Excluir" danger
        onConfirm={async () => { await deleteSlot(slot.id!); onSaved(); }} onClose={() => setConfirmDel(false)} />}
    </>
  );
}
