"use client";
import { useEffect } from "react";
import { anamnesis, attachments, evolutions, importantInfo, patient, psyNotes, sessionsOf, sharedMoods } from "../../lib/db";
import { dateBR, fullDate, hhmm, statusLabel, WEEKDAYS, ymd } from "../../lib/fmt";
import { ANAMNESIS_FIELDS, MOODS, type Profile } from "../../lib/types";
import { Async, useAsync } from "./ui";

/** Prontuário completo para imprimir ou "Salvar como PDF" pelo navegador. */
export function PrintView({ patientId, profile, onBack }: { patientId: string; profile: Profile; onBack: () => void }) {
  const state = useAsync(async () => {
    const [p, an, ev, info, ss, notes, files, mood] = await Promise.all([
      patient(patientId), anamnesis(patientId), evolutions(patientId), importantInfo(patientId),
      sessionsOf(patientId), psyNotes(patientId), attachments(patientId), sharedMoods(patientId),
    ]);
    return { p, an, ev, info, ss, notes: Object.fromEntries(notes.map((n) => [n.session_id, n])), files, mood };
  }, [patientId]);

  useEffect(() => {
    if (!state.data) return;
    const t = setTimeout(() => window.print(), 400);
    return () => clearTimeout(t);
  }, [state.data]);

  return (
    <div>
      <div className="print-bar">
        <button className="pbtn ghost" onClick={onBack}>← Voltar</button>
        <button className="pbtn" onClick={() => window.print()}>Imprimir / Salvar PDF</button>
        <span className="muted" style={{ alignSelf: "center" }}>Na janela de impressão escolha “Salvar como PDF”.</span>
      </div>
      <Async state={state}>{(d) => {
        const filled = ANAMNESIS_FIELDS.filter(([k]) => d.an[k]?.trim());
        const past = d.ss.filter((s) => new Date(s.starts_at) < new Date() || d.notes[s.id]);
        return (
          <div className="print-doc">
            <h1>Prontuário — {d.p.full_name}</h1>
            <p style={{ color: "#666" }}>Psicólogo(a): {profile.full_name} • CRP {profile.crp}</p>
            <p style={{ color: "#888", fontSize: ".85rem" }}>Gerado em {dateBR(ymd(new Date()))} pelo Terapia. Documento sigiloso (Resolução CFP nº 001/2009).</p>

            <h2>Dados do paciente</h2>
            <p>Nome: {d.p.full_name}</p>
            {d.p.age != null && <p>Idade: {d.p.age} anos</p>}
            {d.p.phone && <p>Telefone: {d.p.phone}</p>}
            <p>E-mail: {d.p.email}</p>
            {(d.p.schedule_slots ?? []).map((s) => <p key={s.id}>Horário fixo: {WEEKDAYS[s.weekday]} às {hhmm(s.start_time)} ({s.duration_minutes} min)</p>)}

            <h2>Anamnese</h2>
            {!filled.length && <p>Não preenchida.</p>}
            {filled.map(([k, label]) => <div key={k}><p><b>{label}</b></p><p>{d.an[k]}</p></div>)}

            <h2>Informações importantes</h2>
            {!d.info.length && <p>Nenhuma.</p>}
            {d.info.map((i) => <p key={i.id}>• [{i.category === "nome" ? "Nome" : i.category === "data" ? "Data" : "Outro"}] {i.title}{i.ref_date && ` (${dateBR(i.ref_date)})`}{i.details && ` — ${i.details}`}</p>)}

            <h2>Evolução</h2>
            {!d.ev.length && <p>Nenhum registro.</p>}
            {[...d.ev].reverse().map((e) => <div key={e.id}><p><b>{dateBR(e.entry_date)}</b></p><p>{e.content}</p></div>)}

            <h2>Sessões e anotações</h2>
            {!past.length && <p>Nenhuma sessão realizada.</p>}
            {past.map((s) => (
              <div key={s.id}>
                <p><b>{fullDate(s.starts_at)} — {statusLabel(s.status)}</b></p>
                {d.notes[s.id]?.pre_note && <p>Pré-sessão: {d.notes[s.id].pre_note}</p>}
                {d.notes[s.id]?.post_note && <p>Pós-sessão: {d.notes[s.id].post_note}</p>}
              </div>
            ))}

            <h2>Humor (semanas enviadas pelo paciente)</h2>
            {!d.mood.moods.length && <p>Nenhuma semana compartilhada.</p>}
            {d.mood.moods.map((m) => <p key={m.entry_date}>{dateBR(m.entry_date)}: {MOODS[m.mood][1]}{m.note && ` — ${m.note}`}</p>)}

            <h2>Anexos</h2>
            {!d.files.length && <p>Nenhum.</p>}
            {d.files.map((f) => <p key={f.id}>• {f.file_name}</p>)}
          </div>
        );
      }}</Async>
    </div>
  );
}
