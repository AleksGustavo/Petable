# Como contribuir

Obrigado por ajudar. Este guia resume o fluxo; os detalhes técnicos estão em
[docs/arquitetura.md](docs/arquitetura.md) e [docs/github.md](docs/github.md).

## Antes de começar

1. Rode o sistema local: [README → Rodando localmente](README.md#rodando-localmente) (Docker Desktop basta).
2. Procure uma **issue** no quadro *PetConnect Web — Roadmap*. Para algo novo, abra uma issue antes de codificar.
3. **Nunca** inclua segredo, `.env`, dump de banco ou dado pessoal real em commit, issue ou PR.

## Fluxo

1. Crie uma branch a partir da `main`: `feat/<modulo>-<resumo>`, `fix/...`, `docs/...` ou `chore/...`.
2. Faça commits pequenos no padrão **Conventional Commits** (`feat(estoque): lote por produto`).
3. Abra um **Pull Request** para a `main` usando o modelo do repositório. A `main` não aceita push direto.
4. Todos os checks do CI precisam passar (backend, frontend, E2E, segurança, CodeQL).
5. Faça *Squash and merge* depois da aprovação.

## Regras do projeto

- **Banco:** migration Flyway nova para qualquer mudança (`V<n>__descricao.sql`); **nunca edite uma migration já aplicada**.
  Depois de mudar o schema, atualize o dicionário: `python scripts/gerar-dicionario.py` (com o MySQL de dev no ar) e o diagrama em `docs/banco-de-dados.md`.
- **Estoque e financeiro:** só `EstoqueService` altera saldo e só `FinanceiroService` grava lançamento. Não contorne.
- **Dinheiro:** `BigDecimal` (nunca `double`/`float`); sem exclusão física (use o campo `ativo`).
- **Front:** TypeScript estrito, sem `any`; ações do servidor pelo BFF (o navegador nunca vê o JWT).
- **Testes:** todo código novo vem com teste. Não desabilite teste nem lint para passar.

## Antes de abrir o PR

```bash
cd backend  && ./mvnw -B verify                                # unitários + integração (precisa do Docker)
cd frontend && npm run lint && npm run typecheck && npm test   # e npm run e2e se mexeu em fluxos de tela
```

## Documentação

Mudou contrato, regra de negócio ou arquitetura? Atualize `docs/` no mesmo PR. Etapas grandes têm
spec e relatório em `docs/next-stage/`.

## Licença

O código é proprietário (veja [LICENSE](LICENSE)). Ao contribuir, você concorda que a contribuição
passa a fazer parte do projeto sob os mesmos termos.
