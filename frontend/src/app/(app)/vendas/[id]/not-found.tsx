import Link from "next/link";

export default function VendaNaoEncontrada() {
  return (
    <div className="cartao space-y-3 p-10 text-center">
      <p className="font-medium">Venda não encontrada.</p>
      <Link href="/vendas" className="text-sm font-medium text-brand hover:underline">← Voltar para vendas</Link>
    </div>
  );
}
