export default function Loading() {
  return (
    <div className="animate-pulse space-y-4" aria-busy="true" aria-label="Carregando">
      <div className="h-8 w-48 rounded-lg bg-border/60" />
      <div className="h-32 rounded-xl bg-border/40" />
      <div className="h-32 rounded-xl bg-border/40" />
    </div>
  );
}
