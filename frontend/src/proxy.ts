import { NextResponse, type NextRequest } from "next/server";
import { lerSessao, SESSION_COOKIE } from "@/lib/jwt";

/**
 * Checagem otimista (só lê o cookie): manda para /login quem não tem sessão
 * válida e tira do /login quem já tem. A autorização real acontece na API.
 */
export function proxy(req: NextRequest) {
  const { pathname, search } = req.nextUrl;
  const sessao = lerSessao(req.cookies.get(SESSION_COOKIE)?.value);
  const naLogin = pathname === "/login";

  if (!sessao && !naLogin) {
    const url = new URL("/login", req.nextUrl);
    if (pathname !== "/") url.searchParams.set("next", pathname + search);
    const res = NextResponse.redirect(url);
    res.cookies.delete(SESSION_COOKIE);
    return res;
  }
  if (sessao && naLogin) {
    return NextResponse.redirect(new URL("/dashboard", req.nextUrl));
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|.*\\.(?:png|jpg|jpeg|svg|webp|ico)$).*)"],
};
