import { describe, expect, it } from "vitest";
import { cpfValido, formatarData, mascaraCep, mascaraCpf, mascaraTelefone, somenteDigitos } from "./format";

describe("format", () => {
  it("valida CPF pelos dígitos verificadores", () => {
    expect(cpfValido("529.982.247-25")).toBe(true);
    expect(cpfValido("529.982.247-24")).toBe(false);
    expect(cpfValido("111.111.111-11")).toBe(false);
  });

  it("aplica máscaras progressivas", () => {
    expect(mascaraCpf("5299822")).toBe("529.982.2");
    expect(mascaraCpf("52998224725")).toBe("529.982.247-25");
    expect(mascaraTelefone("19998765432")).toBe("(19) 99876-5432");
    expect(mascaraTelefone("1934567890")).toBe("(19) 3456-7890");
    expect(mascaraCep("13616140")).toBe("13616-140");
  });

  it("normaliza e formata", () => {
    expect(somenteDigitos("(19) 9-88")).toBe("19988");
    expect(formatarData("2025-03-09")).toBe("09/03/2025");
    expect(formatarData(null)).toBe("—");
  });
});
