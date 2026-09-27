import Link from "next/link";

export default function AgendamentoNaoEncontrado() {
  return (
    <div className="cartao mx-auto max-w-lg p-8 text-center">
      <h1 className="text-lg font-semibold">Agendamento não encontrado.</h1>
      <Link href="/agendamentos" className="botao-primario mt-6">Voltar para a agenda</Link>
    </div>
  );
}
