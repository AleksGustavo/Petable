/**
 * Cabeçalhos por requisição (etapa 09): CSP com nonce (DA-16) e X-Request-Id (DA-11).
 * Funções puras para o proxy.ts e para os testes.
 */

export const CABECALHO_REQUEST_ID = "x-request-id";
const REQUEST_ID_VALIDO = /^[A-Za-z0-9-]{8,64}$/;

/** Nonce aleatório (128 bits) em base64, novo a cada requisição. */
export function gerarNonce(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return btoa(String.fromCharCode(...bytes));
}

/** Reaproveita o id vindo do proxy reverso (Caddy) se for bem formado; senão gera um. */
export function garantirRequestId(recebido: string | null | undefined): string {
  return recebido && REQUEST_ID_VALIDO.test(recebido) ? recebido : crypto.randomUUID();
}

export function politicaCsp(nonce: string, opcoes: { dev: boolean; seguro: boolean }): string {
  const diretivas = [
    "default-src 'self'",
    // 'strict-dynamic': os scripts do Next (com nonce) podem carregar os chunks deles.
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${opcoes.dev ? " 'unsafe-eval'" : ""}`,
    // Estilos inline vêm do próprio framework (atributo style); scripts inline não.
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' blob: data:",
    "font-src 'self'",
    `connect-src 'self'${opcoes.dev ? " ws:" : ""}`,
    "frame-ancestors 'none'",
    "form-action 'self'",
    "base-uri 'self'",
    "object-src 'none'",
  ];
  if (opcoes.seguro && !opcoes.dev) diretivas.push("upgrade-insecure-requests");
  return diretivas.join("; ");
}

/** Produção atrás de HTTPS: mesmo critério do cookie de sessão. */
export function opcoesDoAmbiente(): { dev: boolean; seguro: boolean } {
  return { dev: process.env.NODE_ENV === "development", seguro: process.env.COOKIE_SECURE !== "false" };
}
