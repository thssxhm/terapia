"use client";
import { useEffect, useState } from "react";

// Alterna entre claro e escuro (padrão: segue o sistema)
export default function ThemeToggle() {
  const [theme, setTheme] = useState<string | null>(null);
  useEffect(() => {
    let saved: string | null = null;
    try { saved = localStorage.getItem("theme"); } catch {}
    const initial = saved ?? (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
    setTheme(initial);
  }, []);
  const toggle = () => {
    const next = theme === "dark" ? "light" : "dark";
    setTheme(next);
    document.documentElement.setAttribute("data-theme", next);
    try { localStorage.setItem("theme", next); } catch {}
  };
  return (
    <button className="theme-btn" onClick={toggle} aria-label="Alternar tema claro/escuro" title="Tema claro/escuro">
      {theme === "dark" ? "☀️" : "🌙"}
    </button>
  );
}
