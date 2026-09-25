import { decodeJwt } from "jose";

export type Papel = "ADMIN" | "VENDAS" | "SERVICO";

export type Sessao = { token: string; id: number; username: string; papel: Papel; expiraEm: number };

/**
 * Lê as claims do JWT emitido pela API SEM verificar a assinatura: serve só
 * para decisões de interface (redirecionar, mostrar menu). Quem garante a
 * segurança é a API, que valida o token em toda requisição.
 */
export function lerSessao(token: string | undefined, agoraMs = Date.now()): Sessao | null {
  if (!token) return null;
  try {
    const claims = decodeJwt(token);
    const roles = claims.roles as string[] | undefined;
    if (!claims.sub || !claims.exp || !roles?.length) return null;
    if (claims.exp * 1000 <= agoraMs) return null;
    return {
      token,
      id: Number(claims.uid),
      username: claims.sub,
      papel: roles[0] as Papel,
      expiraEm: claims.exp * 1000,
    };
  } catch {
    return null;
  }
}

export const SESSION_COOKIE = "pc_session";
