import { readdirSync, readFileSync, statSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

/**
 * Rastreabilidade do catálogo de módulos ao frontend real (etapa 06): toda tela, rota do BFF, Server Action e item de menu
 * tem exatamente um dono em `backend/src/main/resources/modulos/catalogo.json`, e o catálogo não promete tela, rota ou ação que
 * não existe. Tela ou ação nova sem dono reprova o teste.
 */
const aqui = path.dirname(fileURLToPath(import.meta.url));
const raizFront = path.resolve(aqui, "..", "..");
const caminhoCatalogo = path.resolve(raizFront, "..", "backend", "src", "main", "resources", "modulos", "catalogo.json");

type Dono = { tipo: "nucleo" | "modulo" | "integracao"; id: string; telas: string[]; bff: string[]; acoes: string[] };
type Catalogo = {
  nucleo: { telas: string[]; bff: string[]; acoes: string[] };
  modulos: { id: string; telas: string[]; bff: string[]; acoes: string[] }[];
  integracoes: { id: string; acoes: string[] }[];
};

const catalogo = JSON.parse(readFileSync(caminhoCatalogo, "utf-8")) as Catalogo;
const donos: Dono[] = [
  { tipo: "nucleo", id: "nucleo", telas: catalogo.nucleo.telas, bff: catalogo.nucleo.bff, acoes: catalogo.nucleo.acoes },
  ...catalogo.modulos.map((m): Dono => ({ tipo: "modulo", id: m.id, telas: m.telas, bff: m.bff, acoes: m.acoes })),
  ...catalogo.integracoes.map((i): Dono => ({ tipo: "integracao", id: i.id, telas: [], bff: [], acoes: i.acoes })),
];

function arquivos(pasta: string, nome: string): string[] {
  const achados: string[] = [];
  for (const item of readdirSync(pasta)) {
    const completo = path.join(pasta, item);
    if (statSync(completo).isDirectory()) achados.push(...arquivos(completo, nome));
    else if (item === nome) achados.push(completo);
  }
  return achados;
}

/** Caminho da rota a partir do arquivo: remove grupos "(app)" e o nome do arquivo. */
function rotaDe(arquivo: string, base: string): string {
  const partes = path.relative(base, path.dirname(arquivo)).split(path.sep).filter((p) => p && !(p.startsWith("(") && p.endsWith(")")));
  return "/" + partes.join("/");
}

function combina(padrao: string, rota: string): boolean {
  if (padrao.endsWith("/**")) {
    const prefixo = padrao.slice(0, -3);
    return rota === prefixo || rota.startsWith(prefixo + "/");
  }
  return padrao === rota;
}

/** O dono mais específico (padrão exato ganha de prefixo; prefixo maior ganha de menor). */
function donoDa(rota: string, campo: "telas" | "bff"): string[] {
  let melhor = -1;
  let donosDaRota: string[] = [];
  for (const d of donos) {
    for (const p of d[campo]) {
      if (!combina(p, rota)) continue;
      const peso = p.endsWith("/**") ? p.length - 3 : p.length + 1000;
      if (peso > melhor) {
        melhor = peso;
        donosDaRota = [d.id];
      } else if (peso === melhor && !donosDaRota.includes(d.id)) {
        donosDaRota.push(d.id);
      }
    }
  }
  return donosDaRota;
}

const app = path.join(raizFront, "src", "app");
const telasReais = arquivos(app, "page.tsx").map((a) => rotaDe(a, app));
const bffReal = arquivos(path.join(app, "api"), "route.ts").map((a) => rotaDe(a, app));
const acoesReais = new Map<string, string>();
for (const arq of readdirSync(path.join(app, "actions")).filter((a) => a.endsWith(".ts"))) {
  const fonte = readFileSync(path.join(app, "actions", arq), "utf-8");
  for (const m of fonte.matchAll(/export async function (\w+)/g)) acoesReais.set(m[1], arq);
}

describe("catálogo de módulos x frontend real", () => {
  it("encontra telas, rotas do BFF e ações (o teste não está vazio)", () => {
    expect(telasReais.length).toBeGreaterThan(30);
    expect(bffReal.length).toBeGreaterThanOrEqual(7);
    expect(acoesReais.size).toBeGreaterThan(40);
  });

  it("toda tela tem exatamente um dono", () => {
    const semDono = telasReais.filter((r) => donoDa(r, "telas").length === 0);
    const ambiguas = telasReais.filter((r) => donoDa(r, "telas").length > 1).map((r) => `${r} -> ${donoDa(r, "telas")}`);
    expect(semDono, "telas sem dono no catálogo").toEqual([]);
    expect(ambiguas).toEqual([]);
  });

  it("toda rota do BFF tem exatamente um dono", () => {
    const semDono = bffReal.filter((r) => donoDa(r, "bff").length === 0);
    const ambiguas = bffReal.filter((r) => donoDa(r, "bff").length > 1);
    expect(semDono, "rotas do BFF sem dono no catálogo").toEqual([]);
    expect(ambiguas).toEqual([]);
  });

  it("toda Server Action tem exatamente um dono e o catálogo não cita ação inexistente", () => {
    const dosDonos = new Map<string, string[]>();
    for (const d of donos) for (const a of d.acoes) dosDonos.set(a, [...(dosDonos.get(a) ?? []), d.id]);
    const semDono = [...acoesReais.keys()].filter((a) => !dosDonos.has(a));
    const repetidas = [...dosDonos].filter(([, ids]) => ids.length > 1).map(([a, ids]) => `${a}: ${ids}`);
    const fantasmas = [...dosDonos.keys()].filter((a) => !acoesReais.has(a));
    expect(semDono, "Server Actions sem dono no catálogo").toEqual([]);
    expect(repetidas).toEqual([]);
    expect(fantasmas, "ações do catálogo que não existem no código").toEqual([]);
  });

  it("o catálogo não promete tela nem rota do BFF que não existe", () => {
    const telasFantasma: string[] = [];
    const bffFantasma: string[] = [];
    for (const d of donos) {
      for (const p of d.telas) if (!telasReais.some((r) => combina(p, r))) telasFantasma.push(`${d.id}: ${p}`);
      for (const p of d.bff) if (!bffReal.includes(p)) bffFantasma.push(`${d.id}: ${p}`);
    }
    expect(telasFantasma).toEqual([]);
    expect(bffFantasma).toEqual([]);
  });

  it("todo item do menu leva a uma tela com dono", () => {
    const shell = readFileSync(path.join(raizFront, "src", "components", "app-shell.tsx"), "utf-8");
    const hrefs = [...shell.matchAll(/href: "(\/[^"]*)"/g)].map((m) => m[1]);
    expect(hrefs.length).toBeGreaterThanOrEqual(14);
    expect(hrefs.filter((h) => donoDa(h, "telas").length !== 1)).toEqual([]);
  });

  it("o item de menu de lotes pertence ao módulo lotes e o de usuários ao núcleo", () => {
    expect(donoDa("/estoque/lotes", "telas")).toEqual(["lotes"]);
    expect(donoDa("/usuarios/novo", "telas")).toEqual(["nucleo"]);
    expect(donoDa("/caixa/[id]/comprovante", "telas")).toEqual(["caixa"]);
    expect(donoDa("/vendas/[id]/comprovante", "telas")).toEqual(["pdv"]);
  });
});
