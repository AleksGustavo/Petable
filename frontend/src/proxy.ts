import { NextResponse, type NextRequest } from "next/server";
import { lerSessao, SESSION_COOKIE } from "@/lib/jwt";
import { CABECALHO_REQUEST_ID, garantirRequestId, gerarNonce, opcoesDoAmbiente, politicaCsp } from "@/lib/seguranca";

/**
 * 1) Segurança por requisição (etapa 09): nonce de CSP e X-Request-Id. O Next lê a CSP
 *    do cabeçalho da requisição e aplica o nonce aos próprios scripts.
 * 2) Checagem otimista de sessão (só lê o cookie): manda para /login quem não tem sessão
 *    válida e tira do /login quem já tem. A autorização real acontece na API.
 */
export function proxy(req: NextRequest) {
  const nonce = gerarNonce();
  const csp = politicaCsp(nonce, opcoesDoAmbiente());
  const requestId = garantirRequestId(req.headers.get(CABECALHO_REQUEST_ID));
  const cabecalhos = new Headers(req.headers);
  cabecalhos.set("x-nonce", nonce);
  cabecalhos.set("content-security-policy", csp);
  cabecalhos.set(CABECALHO_REQUEST_ID, requestId);

  const seguir = () => NextResponse.next({ request: { headers: cabecalhos } });
  const assinar = (res: NextResponse) => {
    res.headers.set("Content-Security-Policy", csp);
    res.headers.set("X-Request-Id", requestId);
    return res;
  };

  const { pathname, search } = req.nextUrl;
  const sessao = lerSessao(req.cookies.get(SESSION_COOKIE)?.value);
  const naLogin = pathname === "/login";
  // Recuperação de senha é pública (com ou sem sessão).
  if (pathname === "/esqueci-senha" || pathname === "/redefinir-senha") {
    return assinar(seguir());
  }

  if (!sessao && !naLogin) {
    const url = new URL("/login", req.nextUrl);
    if (pathname !== "/") url.searchParams.set("next", pathname + search);
    const res = NextResponse.redirect(url);
    res.cookies.delete(SESSION_COOKIE);
    return assinar(res);
  }
  // A API recusou o token (sessão revogada): apaga o cookie aqui, senão o
  // cookie ainda "válido" mandaria de volta ao painel e entraria em loop.
  if (naLogin && req.nextUrl.searchParams.has("expirada")) {
    const res = seguir();
    res.cookies.delete(SESSION_COOKIE);
    return assinar(res);
  }
  if (sessao && naLogin) {
    return assinar(NextResponse.redirect(new URL("/dashboard", req.nextUrl)));
  }
  return assinar(seguir());
}

export const config = {
  matcher: [
    {
      source: "/((?!_next/static|_next/image|favicon.ico|.*\\.(?:png|jpg|jpeg|svg|webp|ico)$).*)",
      // Prefetch do Link não precisa de nonce novo.
      missing: [
        { type: "header", key: "next-router-prefetch" },
        { type: "header", key: "purpose", value: "prefetch" },
      ],
    },
  ],
};
