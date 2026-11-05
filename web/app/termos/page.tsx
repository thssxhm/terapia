import type { Metadata } from "next";
import { site } from "../../site.config";

export const metadata: Metadata = { title: "Termos de uso" };

export default function Termos() {
  return (
    <div className="wrap doc">
      <h1>Termos de uso</h1>
      <p className="meta">Última atualização: {site.updatedAt}</p>
      <h2>1. O serviço</h2>
      <p>O {site.name} é uma ferramenta de apoio à comunicação e organização entre psicólogo e paciente. Não presta atendimento psicológico e não substitui serviços de emergência.</p>
      <h2>2. Emergências</h2>
      <p>O botão de emergência avisa o psicólogo, mas não garante resposta imediata. Em risco de vida ligue 192 (SAMU) ou 188 (CVV).</p>
      <h2>3. Responsabilidades do psicólogo</h2>
      <ul>
        <li>Manter CRP ativo e informar dados verdadeiros.</li>
        <li>Seguir o Código de Ética Profissional do Psicólogo e as resoluções do CFP sobre registro documental e atendimento mediado por tecnologia.</li>
        <li>Obter o consentimento do paciente (ou responsável) para o uso do app.</li>
      </ul>
      <h2>4. Assinaturas</h2>
      <p>Os planos são cobrados mensalmente pela Google Play, em blocos de 10 pacientes, e podem ser cancelados a qualquer momento na Play Store. Ao reduzir o plano, não é possível cadastrar novos pacientes acima do novo limite.</p>
      <h2>5. Conta</h2>
      <p>Você pode excluir sua conta a qualquer momento pelo app. Contato: <a href={`mailto:${site.contactEmail}`}>{site.contactEmail}</a>.</p>
    </div>
  );
}
