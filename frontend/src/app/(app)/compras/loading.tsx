export default function Loading() {
  return (
    <div className="animate-pulse space-y-4" aria-busy="true" aria-label="Carregando compras">
      <div className="h-8 w-48 rounded-lg bg-border/60" />
      <div className="h-10 rounded-lg bg-border/40" />
      {[0, 1, 2, 3, 4].map((i) => (
        <div key={i} className="h-16 rounded-xl border border-border bg-border/20" />
      ))}
    </div>
  );
}
