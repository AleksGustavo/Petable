import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  resolve: { tsconfigPaths: true },
  test: {
    // Padrão "node" (rápido); testes de componente declaram `@vitest-environment jsdom`.
    environment: "node",
    include: ["src/**/*.test.{ts,tsx}"],
    coverage: { reporter: ["text", "lcov"], include: ["src/lib/**", "src/components/**"] },
  },
});
