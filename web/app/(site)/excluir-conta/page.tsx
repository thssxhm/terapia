import type { Metadata } from "next";
import { site } from "../../../site.config";

export const metadata: Metadata = { title: "Excluir conta e dados" };

// Página exigida pela Google Play ("URL para exclusão de conta")
export default function ExcluirConta() {
  return (
    <div className="wrap doc">
      <h1>Como excluir sua conta do {site.name}</h1>
      <h2>Pelo aplicativo (imediato)</h2>
      <ol className="steps">
        <li>Abra o app {site.name} e entre na sua conta.</li>
        <li>Psicólogo: toque no ícone de perfil (canto superior direito). Paciente: abra a aba <b>Ajustes</b>.</li>
        <li>Toque em <b>Excluir minha conta</b> e confirme.</li>
      </ol>
      <h2>Por e-mail</h2>
      <p>
        Envie um e-mail para <a href={`mailto:${site.contactEmail}?subject=Excluir%20conta`}>{site.contactEmail}</a> a partir do
        endereço cadastrado, com o assunto “Excluir conta”. Atendemos em até 15 dias.
      </p>
      <h2>O que é apagado</h2>
      <ul>
        <li><b>Psicólogo:</b> conta, perfil, todos os pacientes, prontuários, anotações, mensagens e anexos.</li>
        <li><b>Paciente:</b> conta, registros de humor, anotações pessoais e mensagens enviadas.</li>
      </ul>
      <h2>O que pode ser mantido</h2>
      <p>
        Registros de pagamento mantidos pela Google Play conforme a política dela. Os registros clínicos feitos pelo psicólogo
        sobre um paciente pertencem ao prontuário do psicólogo (controlador), que deve guardá-los conforme as normas do CFP.
      </p>
    </div>
  );
}
