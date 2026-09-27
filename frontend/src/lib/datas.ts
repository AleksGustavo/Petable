/** Datas no fuso do petshop (mesmo "hoje" da API, que usa America/Sao_Paulo). */
const FUSO = "America/Sao_Paulo";

/** Hoje como "YYYY-MM-DD". */
export function hojeISO(agora = new Date()): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: FUSO }).format(agora);
}

/** Agora como "YYYY-MM-DDTHH:mm" (formato do input datetime-local). */
export function agoraLocalISO(agora = new Date()): string {
  const p = Object.fromEntries(
    new Intl.DateTimeFormat("en-CA", {
      timeZone: FUSO,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      hourCycle: "h23",
    })
      .formatToParts(agora)
      .map((x) => [x.type, x.value]),
  );
  return `${p.year}-${p.month}-${p.day}T${p.hour}:${p.minute}`;
}

/** Soma meses a uma data "YYYY-MM-DD" (sugestão da próxima dose). */
export function somarMeses(iso: string, meses: number): string {
  const [a, m, d] = iso.split("-").map(Number);
  const data = new Date(Date.UTC(a, m - 1 + meses, d));
  return data.toISOString().slice(0, 10);
}

export function formatarDataHora(iso: string | null | undefined): string {
  if (!iso) return "—";
  const [data, hora = ""] = iso.split("T");
  const [ano, mes, dia] = data.split("-");
  return `${dia}/${mes}/${ano}${hora ? ` ${hora.slice(0, 5)}` : ""}`;
}

/** Instante UTC da API (ex.: createdAt) → "dd/mm/aaaa hh:mm" no fuso do petshop. */
export function formatarInstante(iso: string | null | undefined): string {
  if (!iso) return "—";
  const data = new Date(iso);
  if (Number.isNaN(data.getTime())) return "—";
  return new Intl.DateTimeFormat("pt-BR", {
    timeZone: FUSO,
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  })
    .format(data)
    .replace(",", "");
}
