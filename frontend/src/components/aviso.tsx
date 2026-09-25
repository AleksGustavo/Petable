export function Aviso({ children }: { children: React.ReactNode }) {
  return (
    <p role="status" className="rounded-lg border border-brand/30 bg-brand-soft px-4 py-3 text-sm text-brand-strong">
      {children}
    </p>
  );
}
