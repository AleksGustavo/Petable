// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StatusVacinaBadge } from "./status-vacina-badge";

describe("StatusVacinaBadge", () => {
  it.each([
    ["EM_DIA", "✓", "Em dia"],
    ["VENCE_EM_30_DIAS", "⚠", "Vence em até 30 dias"],
    ["VENCIDA", "!", "Vencida"],
    ["SEM_PROXIMA_DOSE", "—", "Sem próxima dose"],
  ] as const)("%s mostra símbolo e texto", (status, simbolo, texto) => {
    const { container } = render(<StatusVacinaBadge status={status} />);
    expect(screen.getByText(texto)).toBeDefined();
    expect(container.textContent).toBe(`${simbolo}${texto}`);
  });
});
