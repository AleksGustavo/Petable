export function Aviso({ children, tipo = "info" }: { children: React.ReactNode; tipo?: "info" | "erro" }) {
  const estilo =
    tipo === "erro"
      ? "border-danger/40 bg-danger-soft text-danger"
      : "border-brand/30 bg-brand-soft text-brand-strong";
  return (
    <p role={tipo === "erro" ? "alert" : "status"} className={`rounded-lg border px-4 py-3 text-sm ${estilo}`}>
      {children}
    </p>
  );
}
