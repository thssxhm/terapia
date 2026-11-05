"use client";
import { useCallback, useEffect, useState, type ReactNode } from "react";
import { errorMessage } from "../../lib/fmt";

/** Carrega dados assíncronos com estado de carregando/erro e função de recarregar. */
export function useAsync<T>(fn: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [version, setVersion] = useState(0);
  useEffect(() => {
    let alive = true;
    setLoading(true);
    fn().then((d) => { if (alive) { setData(d); setError(null); } })
      .catch((e) => { if (alive) setError(errorMessage(e)); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, version]);
  const reload = useCallback(() => setVersion((v) => v + 1), []);
  return { data, error, loading, reload, setData };
}

/** Executa uma ação mostrando "salvando…" e o erro, se houver. */
export function useAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const run = useCallback(async (fn: () => Promise<unknown>) => {
    setBusy(true); setError(null);
    try { await fn(); return true; } catch (e) { setError(errorMessage(e)); return false; } finally { setBusy(false); }
  }, []);
  return { busy, error, run, setError };
}

export function Async<T>({ state, children }: { state: { data: T | null; error: string | null; loading: boolean; reload: () => void }; children: (d: T) => ReactNode }) {
  if (state.data !== null) return <>{children(state.data)}</>;
  if (state.loading) return <div className="p-loading">Carregando…</div>;
  return (
    <div className="p-error">
      <p>{state.error}</p>
      <button className="pbtn" onClick={state.reload}>Tentar novamente</button>
    </div>
  );
}

export function Modal({ title, onClose, children, footer, wide, tone }: {
  title: ReactNode; onClose: () => void; children: ReactNode; footer?: ReactNode; wide?: boolean; tone?: "yellow" | "red";
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape" && !tone) onClose(); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose, tone]);
  return (
    <div className="p-overlay" onMouseDown={(e) => { if (e.target === e.currentTarget && !tone) onClose(); }}>
      <div className={`p-modal ${wide ? "wide" : ""} ${tone ?? ""}`} role="dialog" aria-modal="true">
        <div className="p-modal-head"><h3>{title}</h3>{!tone && <button className="icon-btn" onClick={onClose} aria-label="Fechar">✕</button>}</div>
        <div className="p-modal-body">{children}</div>
        {footer && <div className="p-modal-foot">{footer}</div>}
      </div>
    </div>
  );
}

export function Field({ label, children, hint }: { label: string; children: ReactNode; hint?: string }) {
  return (
    <label className="p-field">
      <span>{label}</span>
      {children}
      {hint && <small>{hint}</small>}
    </label>
  );
}

export const Empty = ({ children }: { children: ReactNode }) => <div className="p-empty">{children}</div>;
export const Err = ({ msg }: { msg: string | null }) => (msg ? <p className="p-err">{msg}</p> : null);

export function Confirm({ title, text, confirm = "Confirmar", danger, onConfirm, onClose }: {
  title: string; text: string; confirm?: string; danger?: boolean; onConfirm: () => Promise<unknown> | void; onClose: () => void;
}) {
  const act = useAction();
  return (
    <Modal title={title} onClose={onClose} footer={<>
      <button className="pbtn ghost" onClick={onClose}>Cancelar</button>
      <button className={`pbtn ${danger ? "danger" : ""}`} disabled={act.busy}
        onClick={async () => { if (await act.run(async () => onConfirm())) onClose(); }}>{confirm}</button>
    </>}>
      <p>{text}</p>
      <Err msg={act.error} />
    </Modal>
  );
}
