import { site } from "../site.config";
import SiteShell from "./SiteShell";

const psyFeatures = [
  ["📋", "Prontuário completo", "Anamnese, evolução, informações importantes (nomes e datas), anotações pré e pós sessão e anexos em PDF/DOC."],
  ["🗓️", "Semana e agenda", "Visão de todos os pacientes por dia da semana e agenda geral. Edite horários e adicione mais atendimentos."],
  ["🔔", "Avisos e lembretes", "Receba confirmações, cancelamentos e pedidos de ajuste de horário. Ligue o sininho para ser lembrado(a)."],
  ["🚨", "Pop-up de emergência", "Alertas amarelos (pouco urgente) e vermelhos (muito urgente) aparecem na hora, com atalho para responder."],
  ["💬", "Mensagens", "Canal direto com cada paciente, com notificação para os dois lados."],
  ["📄", "Exportar em PDF", "Baixe todas as informações e anotações do paciente em um único PDF."],
];

const patientFeatures = [
  "Um bloco para cada sessão, com data e nome opcional",
  "Anotações pré e pós sessão (privadas)",
  "Confirmar presença ou pedir alteração/cancelamento",
  "Humor do dia com 5 emojis e anotações",
  "Agenda mensal de humor e gráfico semanal para enviar ao psicólogo",
  "Botão de emergência amarelo e vermelho",
  "Entrada por código no e-mail, Google ou biometria",
];

export default function Home() {
  return (
    <SiteShell>
    <>
      <div className="wrap">
        <div className="hero">
          <div>
            <h1>O cuidado <span>entre as sessões</span>.</h1>
            <p className="lead">
              O <b>Terapia</b> conecta psicólogo e paciente: prontuário, agenda, humor diário, mensagens e botão de
              emergência em um app simples, seguro e acolhedor.
            </p>
            <div className="cta">
              <a className="btn primary" href={site.playStoreUrl}>▶ Baixar na Google Play</a>
              <a className="btn ghost" href="#psicologos">Conhecer recursos</a>
            </div>
          </div>
          <div className="hero-art"><img src="/logo.svg" alt="Logo do Terapia: dois balões de fala" /></div>
        </div>
      </div>

      <section id="psicologos">
        <div className="wrap">
          <h2>Para psicólogos</h2>
          <p className="sub">Tudo de cada paciente em um só lugar, com seu nome e CRP.</p>
          <div className="grid">
            {psyFeatures.map(([icon, title, text], i) => (
              <div className="card" key={title}>
                <div className={`icon ${i % 2 ? "sky" : ""}`}>{icon}</div>
                <h3>{title}</h3>
                <p>{text}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section id="pacientes">
        <div className="wrap split">
          <div className="panel sky">
            <h2>Para pacientes</h2>
            <p className="sub" style={{ margin: 0 }}>O acesso é liberado pelo seu psicólogo.</p>
            <ul>{patientFeatures.map((f) => <li key={f}>{f}</li>)}</ul>
          </div>
          <div className="panel lilac">
            <h2>Como começar</h2>
            <ol className="steps">
              <li>O psicólogo cria a conta no app e informa nome e CRP.</li>
              <li>Cadastra o paciente (nome, idade, telefone, e-mail e horários).</li>
              <li>O paciente recebe por e-mail um código e o link da Play Store.</li>
              <li>No app, o paciente toca em “Sou paciente”, digita o código e pronto.</li>
            </ol>
          </div>
        </div>
      </section>

      <section id="planos">
        <div className="wrap">
          <h2>Planos</h2>
          <p className="sub">Assinatura mensal pela Google Play, vendida de 10 em 10 pacientes. Os primeiros 14 dias são grátis (até 10 pacientes). Pacientes não pagam.</p>
          <div className="plans">
            {[10, 20, 30, 40, 50].map((n) => (
              <div className="plan" key={n}><strong>{n}</strong>pacientes</div>
            ))}
          </div>
          <div className="card" style={{ marginTop: 16, display: "flex", gap: 16, alignItems: "center", flexWrap: "wrap" }}>
            <div className="icon sky">💻</div>
            <div style={{ flex: 1, minWidth: 220 }}>
              <h3 style={{ margin: 0 }}>Terapia Desktop — R$ 14,99/mês</h3>
              <p>Use o painel completo do psicólogo no computador. 7 dias grátis, sem cartão. Pagamento seguro pelo Stripe; cancele quando quiser.</p>
            </div>
            <a className="btn primary" href="/painel">Acessar o painel</a>
          </div>
        </div>
      </section>

      <section>
        <div className="wrap split">
          <div className="card">
            <div className="icon">🔒</div>
            <h3>Privacidade em primeiro lugar</h3>
            <p>
              Dados de saúde são dados sensíveis (LGPD, art. 11). Cada psicólogo só acessa os próprios pacientes; as
              anotações do paciente são só dele; o humor só é compartilhado quando o paciente decide enviar.
              Leia a <a href="/privacidade">política de privacidade</a>.
            </p>
          </div>
          <div className="notice">
            <h3 style={{ marginTop: 0 }}>O Terapia não substitui atendimento de emergência</h3>
            <p style={{ margin: 0 }}>
              Em risco imediato ligue <b>192 (SAMU)</b> ou <b>188 (CVV — 24h, gratuito)</b>, ou vá ao pronto-socorro mais próximo.
            </p>
          </div>
        </div>
      </section>
    </>
    </SiteShell>
  );
}
