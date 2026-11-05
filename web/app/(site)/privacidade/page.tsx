import type { Metadata } from "next";
import { site } from "../../../site.config";

export const metadata: Metadata = { title: "Política de privacidade" };

export default function Privacidade() {
  return (
    <div className="wrap doc">
      <h1>Política de privacidade</h1>
      <p className="meta">Última atualização: {site.updatedAt}</p>

      <p>
        Esta política explica como o aplicativo <b>{site.name}</b> e este site tratam dados pessoais, em conformidade com a
        Lei Geral de Proteção de Dados (Lei nº 13.709/2018 — LGPD). Responsável: {site.controller}. Contato do encarregado
        (DPO): <a href={`mailto:${site.contactEmail}`}>{site.contactEmail}</a>.
      </p>

      <h2>1. Papéis</h2>
      <p>
        O <b>psicólogo</b> é o controlador dos registros clínicos dos seus pacientes (prontuário, anotações e anexos), conforme
        as normas do Conselho Federal de Psicologia. O {site.name} atua como <b>operador</b>, armazenando e processando esses
        dados em nome do psicólogo. Para os dados da própria conta (login, assinatura), o {site.name} é controlador.
      </p>

      <h2>2. Dados que coletamos</h2>
      <table>
        <tbody>
          <tr><th>Psicólogo</th><td>Nome, CRP, e-mail, telefone, dados das assinaturas (via Google Play e Stripe) e token de notificação do aparelho.</td></tr>
          <tr><th>Paciente (cadastrado pelo psicólogo)</th><td>Nome, idade, telefone, e-mail, dias e horários das sessões.</td></tr>
          <tr><th>Registros clínicos (dados sensíveis de saúde)</th><td>Anamnese, evolução, informações importantes, anotações pré/pós sessão do psicólogo e arquivos anexados.</td></tr>
          <tr><th>Registros do paciente</th><td>Humor diário e anotações, anotações pré/pós sessão, pedidos de confirmação/cancelamento/ajuste, mensagens de emergência e chat.</td></tr>
        </tbody>
      </table>

      <h2>3. Para que usamos</h2>
      <ul>
        <li>Prestar o serviço: prontuário, agenda, comunicação, lembretes e notificações.</li>
        <li>Base legal: execução de contrato (art. 7º, V) e, para dados de saúde, tutela da saúde por profissional de saúde (art. 11, II, “f”) e consentimento do titular quando aplicável (art. 11, I).</li>
        <li>Não vendemos dados, não usamos dados de saúde para publicidade e o app não exibe anúncios.</li>
      </ul>

      <h2>4. Quem vê o quê</h2>
      <ul>
        <li>Cada psicólogo acessa somente os pacientes que cadastrou.</li>
        <li>O paciente não acessa o prontuário nem as anotações do psicólogo.</li>
        <li>As anotações pré/pós sessão do paciente são visíveis apenas para ele.</li>
        <li>O humor diário só fica visível ao psicólogo nas semanas que o paciente decidir enviar.</li>
      </ul>

      <h2>5. Onde os dados ficam e com quem compartilhamos</h2>
      <ul>
        <li><b>Supabase</b> (banco de dados, autenticação e arquivos), com criptografia em trânsito (TLS) e em repouso.</li>
        <li><b>Google Firebase Cloud Messaging</b> (entrega de notificações) e <b>Google Play</b> (pagamentos de assinatura).</li>
        <li><b>Resend</b> (envio do e-mail com o código de acesso).</li>
        <li><b>Stripe</b> (pagamento da assinatura do painel desktop). Os dados do cartão são tratados diretamente pelo Stripe e nunca ficam armazenados no {site.name}.</li>
        <li>Esses fornecedores podem processar dados fora do Brasil, com salvaguardas contratuais (art. 33 da LGPD).</li>
      </ul>

      <h2>6. Retenção</h2>
      <p>
        Os dados ficam armazenados enquanto a conta existir. O psicólogo é responsável pela guarda dos registros pelo prazo
        mínimo exigido pelo CFP (Resolução CFP nº 001/2009: 5 anos) — por isso o app permite exportar o prontuário em PDF
        antes de excluir um paciente ou a conta.
      </p>

      <h2>7. Seus direitos</h2>
      <p>
        Você pode solicitar confirmação, acesso, correção, portabilidade e exclusão dos seus dados, e revogar o consentimento
        (art. 18 da LGPD), pelo app (Ajustes &gt; Excluir minha conta) ou pelo e-mail{" "}
        <a href={`mailto:${site.contactEmail}`}>{site.contactEmail}</a>. Veja também <a href="/excluir-conta">como excluir sua conta</a>.
      </p>

      <h2>8. Segurança</h2>
      <p>
        Controle de acesso por linha no banco (Row Level Security), conexões criptografadas, bloqueio opcional por biometria
        e backup do aparelho desativado para os dados do app.
      </p>

      <h2>9. Crianças e adolescentes</h2>
      <p>O cadastro de pacientes menores de idade deve ser feito pelo psicólogo com o consentimento de um responsável legal.</p>

      <h2>10. Alterações</h2>
      <p>Avisaremos no app sobre mudanças relevantes nesta política.</p>
    </div>
  );
}
