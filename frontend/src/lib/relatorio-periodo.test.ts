import { describe, expect, it } from "vitest";
import { atalhosPeriodo, diasNoPeriodo, erroPeriodo, periodoPadrao, podeVerRelatorio, relatoriosDoPapel, somarDias } from "./relatorios";

describe("período dos relatórios", () => {
  it("padrão é do primeiro dia do mês até hoje", () => {
    expect(periodoPadrao("2026-09-26")).toEqual({ dataInicio: "2026-09-01", dataFim: "2026-09-26" });
  });

  it("atalhos contam o dia de hoje (7 dias = hoje e 6 anteriores)", () => {
    const [hoje, sete, mes, trinta] = atalhosPeriodo("2026-03-02");
    expect(hoje).toMatchObject({ dataInicio: "2026-03-02", dataFim: "2026-03-02" });
    expect(sete).toMatchObject({ dataInicio: "2026-02-24", dataFim: "2026-03-02" });
    expect(mes).toMatchObject({ dataInicio: "2026-03-01" });
    expect(trinta.dataInicio).toBe("2026-02-01");
    expect(diasNoPeriodo(sete.dataInicio, sete.dataFim)).toBe(7);
  });

  it("somarDias atravessa ano bissexto sem fuso", () => {
    expect(somarDias("2028-02-28", 1)).toBe("2028-02-29");
    expect(somarDias("2026-01-01", -1)).toBe("2025-12-31");
  });

  it("valida datas, ordem e limite de 366 dias", () => {
    expect(erroPeriodo("", "2026-09-26")).toMatch(/Informe/);
    expect(erroPeriodo("2026-09-26", "2026-09-25")).toMatch(/anterior/);
    expect(erroPeriodo("2025-01-01", "2026-01-01")).toBeNull(); // 366 dias
    expect(erroPeriodo("2025-01-01", "2026-01-02")).toMatch(/366/);
    expect(erroPeriodo("2026-09-26", "2026-09-26")).toBeNull();
  });

  it("permissões por papel: VENDAS só vê vendas, ranking e ABC; SERVICO nada", () => {
    expect(relatoriosDoPapel("ADMIN")).toHaveLength(9);
    expect(relatoriosDoPapel("VENDAS").map((r) => r.tipo)).toEqual(["vendas", "produtos-mais-vendidos", "curva-abc"]);
    expect(relatoriosDoPapel("SERVICO")).toEqual([]);
    expect(podeVerRelatorio("VENDAS", "margem")).toBe(false);
    expect(podeVerRelatorio("ADMIN", "inexistente")).toBe(false);
  });
});
