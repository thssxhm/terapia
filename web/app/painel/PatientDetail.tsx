"use client";
import { useRef, useState } from "react";
import {
  anamnesis, attachments, deleteAttachment, deleteEvolution, deleteInfo, deletePatient, evolutions, importantInfo,
  invitePatient, openAttachment, patient, psyNotes, requestsOf, saveAnamnesis, saveEvolution, saveInfo, savePsyNote,
  sessionsOf, sharedMoods, updatePatient, uploadAttachment,
} from "../../lib/db";
import { addDays, dateBR, dayMonth, fullDate, hhmm, relative, shortDateTime, statusLabel, WEEKDAYS, ymd } from "../../lib/fmt";
import { ANAMNESIS_FIELDS, MOODS, type Evolution, type Info, type MoodEntry, type Patient, type PsyNote, type Session, type Slot } from "../../lib/types";
import type { Go } from "./Painel";
import { SessionModal, SlotModal } from "./Schedule";
import { Async, Confirm, Empty, Err, Field, Modal, useAction, useAsync } from "./ui";

const TABS = ["Sessões", "Anamnese", "Evolução", "Informações", "Anexos", "Pedidos", "Humor"];

export function PatientDetail({ id, initialTab, go }: { id: string; initialTab: number; go: Go }) {
  const state = useAsync(() => patient(id), [id]);
  const [tab, setTab] = useState(initialTab);
  const [editing, setEditing] = useState(false);
  const [confirmDel, setConfirmDel] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  return (
    <Async state={state}>{(p) => (
      <>
        <div className="p-head">
          <button className="icon-btn" onClick={() => go({ name: "patients" })} aria-label="Voltar">←</button>
          <div>
            <h1>{p.full_name}</h1>
            <div className="muted">{[p.age && `${p.age} anos`, p.phone, p.email].filter(Boolean).join(" • ")}</div>
          </div>
          <div className="grow" />
          <button className="pbtn ghost small" onClick={() => go({ name: "chat", id })}>💬 Mensagens</button>
          <button className="pbtn ghost small" onClick={() => go({ name: "print", id })}>📄 Exportar PDF</button>
          <button className="pbtn ghost small" onClick={() => setEditing(true)}>Editar</button>
          <button className="pbtn ghost small" onClick={async () => {
            try { const r = await invitePatient({ resend_patient_id: id }); setMsg(r.error ?? (r.email_sent ? "Código reenviado por e-mail." : "Não foi possível enviar o e-mail.")); }
            catch (e) { setMsg(e instanceof Error ? e.message : String(e)); }
          }}>Reenviar código</button>
          <button className="pbtn danger small" onClick={() => setConfirmDel(true)}>Excluir</button>
        </div>
        {msg && <p className="p-ok">{msg}</p>}
        <div className="p-tabs">{TABS.map((t, i) => <button key={t} className={`p-tab ${tab === i ? "active" : ""}`} onClick={() => setTab(i)}>{t}</button>)}</div>
        {tab === 0 && <SessionsTab p={p} reloadPatient={state.reload} />}
        {tab === 1 && <AnamnesisTab id={id} />}
        {tab === 2 && <EvolutionTab id={id} />}
        {tab === 3 && <InfoTab id={id} />}
        {tab === 4 && <AttachmentsTab id={id} />}
        {tab === 5 && <RequestsTab id={id} />}
        {tab === 6 && <MoodTab id={id} />}
        {editing && <EditPatient p={p} onClose={() => setEditing(false)} onSaved={() => { setEditing(false); state.reload(); }} />}
        {confirmDel && <Confirm danger confirm="Excluir" title="Excluir paciente?"
          text="Todos os dados, anotações, anexos e mensagens deste paciente serão apagados permanentemente. Dica: exporte o PDF antes."
          onConfirm={async () => { await deletePatient(p); go({ name: "patients" }); }} onClose={() => setConfirmDel(false)} />}
      </>
    )}</Async>
  );
}

function EditPatient({ p, onClose, onSaved }: { p: Patient; onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState(p.full_name);
  const [age, setAge] = useState(p.age?.toString() ?? "");
  const [phone, setPhone] = useState(p.phone ?? "");
  const act = useAction();
  return (
    <Modal title="Editar paciente" onClose={onClose} footer={<>
      <button className="pbtn ghost" onClick={onClose}>Cancelar</button>
      <button className="pbtn" disabled={act.busy || !name} onClick={async () => { if (await act.run(() => updatePatient(p.id, name, age ? Number(age) : null, phone || null))) onSaved(); }}>Salvar</button>
    </>}>
      <Field label="Nome"><input value={name} onChange={(e) => setName(e.target.value)} /></Field>
      <div className="p-two">
        <Field label="Idade"><input type="number" value={age} onChange={(e) => setAge(e.target.value)} /></Field>
        <Field label="Telefone"><input value={phone} onChange={(e) => setPhone(e.target.value)} /></Field>
      </div>
      <Field label="E-mail" hint="O e-mail não pode ser alterado"><input value={p.email} disabled /></Field>
      <Err msg={act.error} />
    </Modal>
  );
}

// ---------- Sessões + horários fixos + anotações pré/pós ----------
function SessionsTab({ p, reloadPatient }: { p: Patient; reloadPatient: () => void }) {
  const state = useAsync(async () => {
    const [s, n] = await Promise.all([sessionsOf(p.id), psyNotes(p.id)]);
    return { sessions: s, notes: Object.fromEntries(n.map((x) => [x.session_id, x])) as Record<string, PsyNote> };
  }, [p.id]);
  const [slot, setSlot] = useState<Slot | null | undefined>(undefined);
  const [newSession, setNewSession] = useState(false);
  const [edit, setEdit] = useState<Session | null>(null);
  const [notes, setNotes] = useState<Session | null>(null);
  const [showPast, setShowPast] = useState(false);
  const slots = [...(p.schedule_slots ?? [])].sort((a, b) => a.weekday - b.weekday || a.start_time.localeCompare(b.start_time));

  return (
    <>
      <div className="p-card" style={{ marginBottom: 16 }}>
        <h3>Horários fixos</h3>
        {!slots.length && <p className="muted">Nenhum horário fixo.</p>}
        {slots.map((s) => (
          <div key={s.id} className="p-row click" style={{ marginBottom: 6 }} onClick={() => setSlot(s)}>
            <span className="grow">🕑 {WEEKDAYS[s.weekday]} às {hhmm(s.start_time)} • {s.duration_minutes} min</span><span>✏️</span>
          </div>
        ))}
        <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
          <button className="pbtn ghost small" onClick={() => setSlot(null)}>+ Horário fixo</button>
          <button className="pbtn ghost small" onClick={() => setNewSession(true)}>+ Sessão avulsa</button>
        </div>
      </div>
      <Async state={state}>{({ sessions, notes: n }) => {
        const cut = Date.now() - 36e5;
        const upcoming = sessions.filter((s) => new Date(s.starts_at).getTime() > cut);
        const past = sessions.filter((s) => new Date(s.starts_at).getTime() <= cut).reverse();
        const row = (s: Session) => (
          <div key={s.id} className={`p-row click ${s.status === "cancelled" ? "cancelled" : ""}`} onClick={() => setNotes(s)}>
            <div className="grow">
              <b>{fullDate(s.starts_at)}</b> <span className="chip">{statusLabel(s.status)}</span>
              {n[s.id]?.pre_note && <div className="muted">Pré: {n[s.id].pre_note}</div>}
              {n[s.id]?.post_note && <div className="muted">Pós: {n[s.id].post_note}</div>}
              {!n[s.id] && <div className="muted">Clique para anotações pré e pós sessão</div>}
            </div>
            <button className="icon-btn" title="Editar data/horário" onClick={(e) => { e.stopPropagation(); setEdit(s); }}>✏️</button>
          </div>
        );
        return (
          <>
            <h3>Próximas sessões</h3>
            {!upcoming.length && <Empty>Nenhuma sessão futura.</Empty>}
            <div className="p-list">{upcoming.map(row)}</div>
            <button className="pbtn ghost small" style={{ margin: "16px 0" }} onClick={() => setShowPast(!showPast)}>
              {showPast ? "Ocultar sessões anteriores" : `Ver sessões anteriores (${past.length})`}</button>
            {showPast && <div className="p-list">{past.map(row)}</div>}
          </>
        );
      }}</Async>
      {slot !== undefined && <SlotModal patientId={p.id} slot={slot} onClose={() => setSlot(undefined)}
        onSaved={() => { setSlot(undefined); reloadPatient(); state.reload(); }} />}
      {newSession && <SessionModal session={null} patientId={p.id} onClose={() => setNewSession(false)} onSaved={() => { setNewSession(false); state.reload(); }} />}
      {edit && <SessionModal session={edit} onClose={() => setEdit(null)} onSaved={() => { setEdit(null); state.reload(); }} />}
      {notes && <NotesModal s={notes} note={state.data?.notes[notes.id]} onClose={() => setNotes(null)} onSaved={() => { setNotes(null); state.reload(); }} />}
    </>
  );
}

function NotesModal({ s, note, onClose, onSaved }: { s: Session; note?: PsyNote; onClose: () => void; onSaved: () => void }) {
  const [pre, setPre] = useState(note?.pre_note ?? "");
  const [post, setPost] = useState(note?.post_note ?? "");
  const [info, setInfo] = useState(false);
  const act = useAction();
  return (
    <>
      <Modal wide title={shortDateTime(s.starts_at)} onClose={onClose} footer={<>
        <button className="pbtn ghost" style={{ marginRight: "auto" }} onClick={() => setInfo(true)}>+ Informação importante</button>
        <button className="pbtn ghost" onClick={onClose}>Cancelar</button>
        <button className="pbtn" disabled={act.busy} onClick={async () => {
          if (await act.run(() => savePsyNote({ session_id: s.id, patient_id: s.patient_id, pre_note: pre, post_note: post }))) onSaved();
        }}>Salvar</button>
      </>}>
        <Field label="Anotações pré-sessão"><textarea rows={5} value={pre} onChange={(e) => setPre(e.target.value)} /></Field>
        <Field label="Anotações pós-sessão"><textarea rows={8} value={post} onChange={(e) => setPost(e.target.value)} /></Field>
        <Err msg={act.error} />
      </Modal>
      {info && <InfoModal patientId={s.patient_id} info={null} onClose={() => setInfo(false)} onSaved={() => setInfo(false)} />}
    </>
  );
}

// ---------- Anamnese ----------
function AnamnesisTab({ id }: { id: string }) {
  const state = useAsync(() => anamnesis(id), [id]);
  return <Async state={state}>{(data) => <AnamnesisForm id={id} initial={data} />}</Async>;
}
function AnamnesisForm({ id, initial }: { id: string; initial: Record<string, string> }) {
  const [v, setV] = useState(initial);
  const [ok, setOk] = useState(false);
  const act = useAction();
  return (
    <div className="p-card">
      <div className="p-two">
        {ANAMNESIS_FIELDS.map(([k, label]) => (
          <Field key={k} label={label}><textarea value={v[k] ?? ""} onChange={(e) => { setV({ ...v, [k]: e.target.value }); setOk(false); }} /></Field>
        ))}
      </div>
      <Err msg={act.error} />
      {ok && <p className="p-ok">Anamnese salva ✔</p>}
      <button className="pbtn" disabled={act.busy} onClick={async () => { if (await act.run(() => saveAnamnesis(id, v))) setOk(true); }}>Salvar anamnese</button>
    </div>
  );
}

// ---------- Evolução ----------
function EvolutionTab({ id }: { id: string }) {
  const state = useAsync(() => evolutions(id), [id]);
  const [edit, setEdit] = useState<Evolution | null>(null);
  return (
    <>
      <button className="pbtn" style={{ marginBottom: 14 }} onClick={() => setEdit({ patient_id: id, entry_date: ymd(new Date()), content: "" })}>+ Nova evolução</button>
      <Async state={state}>{(list) => (
        <div className="p-list">
          {!list.length && <Empty>Nenhum registro de evolução.</Empty>}
          {list.map((e) => (
            <div key={e.id} className="p-card" style={{ cursor: "pointer" }} onClick={() => setEdit(e)}>
              <h3>{dateBR(e.entry_date)}</h3><p style={{ margin: 0, whiteSpace: "pre-wrap" }}>{e.content}</p>
            </div>
          ))}
        </div>
      )}</Async>
      {edit && <EvolutionModal e={edit} onClose={() => setEdit(null)} onSaved={() => { setEdit(null); state.reload(); }} />}
    </>
  );
}
function EvolutionModal({ e, onClose, onSaved }: { e: Evolution; onClose: () => void; onSaved: () => void }) {
  const [date, setDate] = useState(e.entry_date);
  const [text, setText] = useState(e.content);
  const [del, setDel] = useState(false);
  const act = useAction();
  return (
    <>
      <Modal wide title={e.id ? "Editar evolução" : "Nova evolução"} onClose={onClose} footer={<>
        {e.id && <button className="pbtn ghost" style={{ marginRight: "auto", color: "var(--red)" }} onClick={() => setDel(true)}>Excluir</button>}
        <button className="pbtn ghost" onClick={onClose}>Cancelar</button>
        <button className="pbtn" disabled={act.busy || !text.trim()} onClick={async () => { if (await act.run(() => saveEvolution({ ...e, entry_date: date, content: text }))) onSaved(); }}>Salvar</button>
      </>}>
        <Field label="Data"><input type="date" value={date} onChange={(x) => setDate(x.target.value)} /></Field>
        <Field label="Evolução do paciente"><textarea rows={10} value={text} onChange={(x) => setText(x.target.value)} /></Field>
        <Err msg={act.error} />
      </Modal>
      {del && <Confirm danger title="Excluir evolução?" text="Esta ação não pode ser desfeita." confirm="Excluir"
        onConfirm={async () => { await deleteEvolution(e.id!); onSaved(); }} onClose={() => setDel(false)} />}
    </>
  );
}

// ---------- Informações importantes ----------
function InfoTab({ id }: { id: string }) {
  const state = useAsync(() => importantInfo(id), [id]);
  const [edit, setEdit] = useState<Info | null | undefined>(undefined);
  return (
    <>
      <button className="pbtn" style={{ marginBottom: 14 }} onClick={() => setEdit(null)}>+ Nova informação</button>
      <Async state={state}>{(list) => (
        <>
          {!list.length && <Empty>Registre nomes importantes, datas marcantes e outros dados relevantes.</Empty>}
          <div className="p-grid">
            {([["nome", "Nomes importantes"], ["data", "Datas importantes"], ["outro", "Outras informações"]] as const).map(([cat, title]) => {
              const g = list.filter((i) => i.category === cat);
              return g.length ? (
                <div key={cat} className="p-card"><h3>{title}</h3>
                  {g.map((i) => (
                    <div key={i.id} className="p-row click" style={{ marginBottom: 6 }} onClick={() => setEdit(i)}>
                      <div className="grow"><b>{i.title}{i.ref_date && ` — ${dateBR(i.ref_date)}`}</b>{i.details && <div className="muted">{i.details}</div>}</div>
                    </div>
                  ))}
                </div>
              ) : null;
            })}
          </div>
        </>
      )}</Async>
      {edit !== undefined && <InfoModal patientId={id} info={edit} onClose={() => setEdit(undefined)} onSaved={() => { setEdit(undefined); state.reload(); }} />}
    </>
  );
}
function InfoModal({ patientId, info, onClose, onSaved }: { patientId: string; info: Info | null; onClose: () => void; onSaved: () => void }) {
  const [i, setI] = useState<Info>(info ?? { patient_id: patientId, category: "nome", title: "", details: null, ref_date: null });
  const act = useAction();
  return (
    <Modal title={info ? "Editar informação" : "Nova informação importante"} onClose={onClose} footer={<>
      {info && <button className="pbtn ghost" style={{ marginRight: "auto", color: "var(--red)" }}
        onClick={async () => { if (await act.run(() => deleteInfo(info.id!))) onSaved(); }}>Excluir</button>}
      <button className="pbtn ghost" onClick={onClose}>Cancelar</button>
      <button className="pbtn" disabled={act.busy || !i.title} onClick={async () => { if (await act.run(() => saveInfo(i))) onSaved(); }}>Salvar</button>
    </>}>
      <Field label="Tipo"><select value={i.category} onChange={(e) => setI({ ...i, category: e.target.value })}>
        <option value="nome">Nome importante</option><option value="data">Data importante</option><option value="outro">Outro</option></select></Field>
      <Field label={i.category === "nome" ? "Nome (ex.: Maria — mãe)" : i.category === "data" ? "Evento (ex.: aniversário)" : "Título"}>
        <input value={i.title} onChange={(e) => setI({ ...i, title: e.target.value })} /></Field>
      <Field label="Detalhes"><textarea value={i.details ?? ""} onChange={(e) => setI({ ...i, details: e.target.value || null })} /></Field>
      <Field label="Data (opcional)"><input type="date" value={i.ref_date ?? ""} onChange={(e) => setI({ ...i, ref_date: e.target.value || null })} /></Field>
      <Err msg={act.error} />
    </Modal>
  );
}

// ---------- Anexos ----------
function AttachmentsTab({ id }: { id: string }) {
  const state = useAsync(() => attachments(id), [id]);
  const input = useRef<HTMLInputElement>(null);
  const act = useAction();
  const [del, setDel] = useState<string | null>(null);
  return (
    <>
      <input ref={input} type="file" hidden accept=".pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        onChange={async (e) => { const f = e.target.files?.[0]; e.target.value = ""; if (f && await act.run(() => uploadAttachment(id, f))) state.reload(); }} />
      <button className="pbtn" disabled={act.busy} onClick={() => input.current?.click()}>{act.busy ? "Enviando…" : "📎 Anexar PDF ou DOC"}</button>
      <Err msg={act.error} />
      <Async state={state}>{(list) => (
        <div className="p-list" style={{ marginTop: 14 }}>
          {!list.length && <Empty>Nenhum anexo.</Empty>}
          {list.map((a) => (
            <div key={a.id} className="p-row click" onClick={() => act.run(() => openAttachment(a))}>
              <span>📄</span>
              <div className="grow"><b>{a.file_name}</b><div className="muted">{relative(a.created_at)}{a.size_bytes ? ` • ${Math.round(a.size_bytes / 1024)} KB` : ""}</div></div>
              <button className="icon-btn" onClick={(e) => { e.stopPropagation(); setDel(a.id); }} title="Excluir">🗑️</button>
            </div>
          ))}
          {del && <Confirm danger title="Excluir anexo?" text={list.find((a) => a.id === del)?.file_name ?? ""} confirm="Excluir"
            onConfirm={async () => { await deleteAttachment(list.find((a) => a.id === del)!); state.reload(); }} onClose={() => setDel(null)} />}
        </div>
      )}</Async>
    </>
  );
}

// ---------- Pedidos ----------
function RequestsTab({ id }: { id: string }) {
  const state = useAsync(() => requestsOf(id), [id]);
  const kind = (k: string) => ({ confirm: "Confirmação de presença", cancel: "Pedido de cancelamento" } as Record<string, string>)[k] ?? "Pedido de ajuste de horário";
  return (
    <Async state={state}>{(list) => (
      <div className="p-list">
        {!list.length && <Empty>Nenhum pedido deste paciente.</Empty>}
        {list.map((r) => (
          <div key={r.id} className="p-row">
            <div className="grow"><b>{kind(r.kind)}</b>{r.message && <div>{r.message}</div>}
              <div className="muted">{relative(r.created_at)}</div></div>
            <span className={`chip ${r.status === "pending" ? "warn" : ""}`}>
              {r.status === "accepted" ? "Aceito" : r.status === "declined" ? "Recusado" : "Pendente — responda em Avisos"}</span>
          </div>
        ))}
      </div>
    )}</Async>
  );
}

// ---------- Humor compartilhado ----------
function MoodTab({ id }: { id: string }) {
  const state = useAsync(() => sharedMoods(id), [id]);
  return (
    <Async state={state}>{({ shares, moods }) => (
      <div className="p-grid">
        {!shares.length && <Empty>O paciente ainda não enviou nenhum gráfico de humor.</Empty>}
        {shares.map((s) => {
          const week = moods.filter((m) => m.entry_date >= s.week_start && m.entry_date <= addDays(s.week_start, 6));
          return (
            <div key={s.week_start} className="p-card">
              <h3>Semana de {dayMonth(s.week_start)} a {dayMonth(addDays(s.week_start, 6))}</h3>
              <MoodChart start={s.week_start} entries={week} />
              {week.filter((m) => m.note).map((m) => <p key={m.entry_date} className="muted" style={{ margin: "4px 0" }}>{dayMonth(m.entry_date)}: {m.note}</p>)}
            </div>
          );
        })}
      </div>
    )}</Async>
  );
}

export function MoodChart({ start, entries }: { start: string; entries: MoodEntry[] }) {
  const by = Object.fromEntries(entries.map((e) => [e.entry_date, e]));
  return (
    <div className="mood-chart">
      {[0, 1, 2, 3, 4, 5, 6].map((i) => {
        const d = addDays(start, i);
        const e = by[d];
        return (
          <div key={d} className="col">
            <span style={{ fontSize: "1.2rem" }} title={e ? MOODS[e.mood][1] : "sem registro"}>{e ? MOODS[e.mood][0] : "·"}</span>
            <div className={`bar ${e ? "" : "none"}`} style={{ height: e ? e.mood * 24 : 4 }} />
            <span>{["Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"][i]}</span>
            <span className="muted">{dayMonth(d)}</span>
          </div>
        );
      })}
    </div>
  );
}
