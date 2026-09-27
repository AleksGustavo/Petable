import { describe, expect, it } from "vitest";
import { garantirRequestId, gerarNonce, politicaCsp } from "./seguranca";

describe("CSP", () => {
  it("contém o nonce, strict-dynamic e bloqueia enquadramento", () => {
    const csp = politicaCsp("abc123", { dev: false, seguro: true });
    expect(csp).toContain("script-src 'self' 'nonce-abc123' 'strict-dynamic'");
    expect(csp).toContain("frame-ancestors 'none'");
    expect(csp).toContain("object-src 'none'");
    expect(csp).not.toContain("unsafe-eval");
    expect(csp).toContain("upgrade-insecure-requests");
  });

  it("dev libera eval (HMR) e não força HTTPS", () => {
    const csp = politicaCsp("n", { dev: true, seguro: true });
    expect(csp).toContain("'unsafe-eval'");
    expect(csp).not.toContain("upgrade-insecure-requests");
  });

  it("sem cookie seguro (compose local em HTTP) não força HTTPS", () => {
    expect(politicaCsp("n", { dev: false, seguro: false })).not.toContain("upgrade-insecure-requests");
  });

  it("nonce muda a cada chamada e é base64 de 16 bytes", () => {
    const a = gerarNonce();
    expect(a).not.toBe(gerarNonce());
    expect(atob(a)).toHaveLength(16);
  });
});

describe("X-Request-Id", () => {
  it("reaproveita um id bem formado", () => {
    expect(garantirRequestId("caddy-1234-abcd")).toBe("caddy-1234-abcd");
  });

  it("gera quando ausente, curto ou malicioso", () => {
    for (const recebido of [null, undefined, "abc", "<script>", "x".repeat(65)]) {
      expect(garantirRequestId(recebido)).toMatch(/^[0-9a-f-]{36}$/);
    }
  });
});
