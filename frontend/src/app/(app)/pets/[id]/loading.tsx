export default function Loading() {
  return (
    <div className="animate-pulse space-y-6" aria-busy="true" aria-label="Carregando pet">
      <div className="flex items-center gap-4">
        <div className="size-24 rounded-full bg-border/60" />
        <div className="flex-1 space-y-2">
          <div className="h-7 w-48 rounded bg-border/60" />
          <div className="h-4 w-64 rounded bg-border/40" />
        </div>
      </div>
      <div className="h-10 rounded-lg bg-border/40" />
      <div className="h-48 rounded-xl bg-border/40" />
    </div>
  );
}
