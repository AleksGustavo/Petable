export default function Loading() {
  return (
    <div className="animate-pulse space-y-4" aria-busy="true" aria-label="Carregando pets">
      <div className="h-8 w-40 rounded-lg bg-border/60" />
      <div className="h-10 rounded-lg bg-border/40" />
      {[0, 1, 2, 3].map((i) => (
        <div key={i} className="flex items-center gap-3 rounded-xl border border-border p-4">
          <div className="size-12 rounded-full bg-border/60" />
          <div className="flex-1 space-y-2">
            <div className="h-4 w-1/3 rounded bg-border/60" />
            <div className="h-3 w-1/2 rounded bg-border/40" />
          </div>
        </div>
      ))}
    </div>
  );
}
