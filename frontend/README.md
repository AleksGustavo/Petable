# PetConnect Web — frontend

Next.js 16 (App Router) + React 19 + Tailwind 4. Arquitetura e convenções: [../docs/arquitetura.md](../docs/arquitetura.md).

```bash
cp .env.example .env.local   # API_URL aponta para a API Spring
npm install
npm run dev                  # http://localhost:3000 (use -- -p 3010 se a 3000 estiver ocupada)
npm run lint && npm run typecheck && npm test && npm run build
```

> Next 16 tem APIs novas (`proxy.ts` no lugar de `middleware.ts`, `params`/`cookies()` assíncronos). Consulte `node_modules/next/dist/docs/` antes de escrever código (ver `AGENTS.md`).
