export default function Carregando() {
  return (
    <div className="space-y-4" aria-busy="true" aria-label="Carregando relatório">
      <div className="h-8 w-56 animate-pulse rounded bg-border/50" />
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="cartao h-20 animate-pulse" />
        ))}
      </div>
      <div className="cartao h-64 animate-pulse" />
    </div>
  );
}
