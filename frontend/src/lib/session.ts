import "server-only";
import { cache } from "react";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { lerSessao, SESSION_COOKIE, type Sessao } from "@/lib/jwt";

export const getSessao = cache(async (): Promise<Sessao | null> => {
  const token = (await cookies()).get(SESSION_COOKIE)?.value;
  return lerSessao(token);
});

export async function exigirSessao(): Promise<Sessao> {
  const sessao = await getSessao();
  if (!sessao) redirect("/login");
  return sessao;
}
