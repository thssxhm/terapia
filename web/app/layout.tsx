import type { Metadata, Viewport } from "next";
import Link from "next/link";
import "./globals.css";
import ThemeToggle from "./ThemeToggle";
import { site } from "../site.config";

export const metadata: Metadata = {
  title: { default: "Terapia — app para psicólogos e pacientes", template: "%s | Terapia" },
  description:
    "Prontuário, agenda, humor diário, mensagens e botão de emergência: o app Terapia conecta psicólogo e paciente entre as sessões.",
  openGraph: { title: "Terapia", description: "O cuidado entre sessões, conectando psicólogo e paciente.", locale: "pt_BR", type: "website" },
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#fbf9ff" },
    { media: "(prefers-color-scheme: dark)", color: "#17141f" },
  ],
};

// Aplica o tema salvo antes da página aparecer (evita "piscar")
const themeScript = `try{var t=localStorage.getItem('theme');if(t)document.documentElement.setAttribute('data-theme',t)}catch(e){}`;

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="pt-BR" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeScript }} />
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="" />
        <link href="https://fonts.googleapis.com/css2?family=Nunito:wght@400;600;700;800&display=swap" rel="stylesheet" />
      </head>
      <body>
        <header className="top">
          <div className="wrap">
            <Link href="/" className="brand"><img src="/logo.svg" alt="" />Terapia</Link>
            <nav className="links">
              <Link href="/#psicologos" className="hide-sm">Psicólogos</Link>
              <Link href="/#pacientes" className="hide-sm">Pacientes</Link>
              <Link href="/#planos" className="hide-sm">Planos</Link>
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
      </body>
    </html>
  );
}
