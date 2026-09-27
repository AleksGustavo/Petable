export default function Loading() {
  return (
    <div className="animate-pulse space-y-4" aria-busy="true" aria-label="Carregando">
      <div className="h-8 w-48 rounded-lg bg-border/60" />
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="h-28 rounded-xl border border-border bg-border/20" />
        ))}
      </div>
      {[0, 1, 2].map((i) => (
        <div key={i} className="h-16 rounded-xl border border-border bg-border/20" />
      ))}
    </div>
  );
}
