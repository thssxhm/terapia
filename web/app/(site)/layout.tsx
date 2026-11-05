import Link from "next/link";
import ThemeToggle from "../ThemeToggle";
import { site } from "../../site.config";

// Cabeçalho e rodapé das páginas públicas (o /painel tem layout próprio)
export default function SiteLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <header className="top">
        <div className="wrap">
          <Link href="/" className="brand"><img src="/logo.svg" alt="" />Terapia</Link>
          <nav className="links">
            <Link href="/#psicologos" className="hide-sm">Psicólogos</Link>
            <Link href="/#pacientes" className="hide-sm">Pacientes</Link>
            <Link href="/#planos" className="hide-sm">Planos</Link>
            <Link href="/painel" className="btn primary nav-cta">Entrar</Link>
            <ThemeToggle />
          </nav>
        </div>
      </header>
      <main>{children}</main>
      <footer>
        <div className="wrap">
          <span>© {new Date().getFullYear()} {site.name}. Em crise? Ligue 188 (CVV) ou 192 (SAMU).</span>
          <nav>
            <Link href="/privacidade">Privacidade</Link>
            <Link href="/termos">Termos de uso</Link>
            <Link href="/excluir-conta">Excluir conta</Link>
            <a href={`mailto:${site.contactEmail}`}>Contato</a>
          </nav>
        </div>
      </footer>
    </>
  );
}
