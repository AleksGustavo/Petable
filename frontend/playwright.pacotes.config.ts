import { defineConfig, devices } from "@playwright/test";

/**
 * E2E de interface por PACOTE (issue #42): menu, guardas de rota e cards conforme os módulos contratados.
 * Roda contra uma stack já no ar cuja API foi iniciada com PETCONNECT_MODULOS do pacote. Variáveis:
 *   E2E_PACOTE                    pdv | petshop | completo  (obrigatória)
 *   E2E_BASE_URL / E2E_API_URL    como na config principal
 *   E2E_ADMIN_USERNAME / E2E_ADMIN_PASSWORD  ADMIN do bootstrap (nunca impressos)
 * Os specs de fluxo (e2e/) exigem todos os módulos; por isso esta config é separada.
 */
export default defineConfig({
  testDir: "./e2e-pacotes",
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: process.env.CI ? [["github"], ["html", { open: "never", outputFolder: "playwright-report-pacotes" }]] : [["list"]],
  globalSetup: "./e2e-pacotes/global-setup.ts",
  globalTeardown: "./e2e-pacotes/global-teardown.ts",
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    ignoreHTTPSErrors: true,
    locale: "pt-BR",
    timezoneId: "America/Sao_Paulo",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  outputDir: "test-results-pacotes",
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
