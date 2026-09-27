import { defineConfig, devices } from "@playwright/test";

/**
 * E2E dos fluxos críticos (etapa 09). Roda contra uma stack já no ar (compose de dev/CI
 * ou a produção local atrás do Caddy). Variáveis:
 *   E2E_BASE_URL        web (padrão http://localhost:3000)
 *   E2E_API_URL         API, só para preparar/limpar dados (padrão http://localhost:8081)
 *   E2E_ADMIN_USERNAME / E2E_ADMIN_PASSWORD  ADMIN do bootstrap (nunca impressos)
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : [["list"], ["html", { open: "never" }]],
  globalSetup: "./e2e/global-setup.ts",
  globalTeardown: "./e2e/global-teardown.ts",
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    ignoreHTTPSErrors: true,
    locale: "pt-BR",
    timezoneId: "America/Sao_Paulo",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
