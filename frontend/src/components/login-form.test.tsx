// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("@/app/actions/auth", () => ({ entrar: vi.fn() }));

import { LoginForm } from "./login-form";

describe("LoginForm", () => {
  it("renderiza campos acessíveis e repassa o destino", () => {
    const { container } = render(<LoginForm next="/clientes" />);
    expect(screen.getByLabelText(/usuário ou e-mail/i)).toBeDefined();
    expect(screen.getByLabelText(/senha/i)).toHaveProperty("type", "password");
    expect(screen.getByRole("button", { name: "Entrar" })).toBeDefined();
    expect(container.querySelector('input[name="next"]')).toHaveProperty("value", "/clientes");
  });
});
