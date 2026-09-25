# Etapas (`docs/next-stage/`)

Fluxo de trabalho do projeto:

1. O **ChatGPT** escreve a especificação da etapa em `.md`, a partir do prompt de handoff da etapa anterior.
2. O arquivo é salvo aqui como `NN-nome-curto.md` (ex.: `01-pets.md`).
3. O **Claude Code** executa a etapa no repositório, abre a branch e o PR, e no fim devolve o **prompt de handoff** da próxima etapa.

## Formato esperado de cada especificação

```markdown
# NN — Título da etapa

## Objetivo
## Escopo (entra / não entra)
## Referência no legado        ← quais arquivos de legacy/php e tabelas antigas
## Banco                       ← migration Flyway V<n>__..., tabelas/colunas, índices
## API                         ← endpoints, DTOs, validações, papéis, códigos de erro
## Frontend                    ← telas, rotas, comportamento no celular, estados vazio/erro
## Regras de negócio
## Testes                      ← unitários, integração (*IT) e front
## Critérios de aceite          ← checklist verificável
## Fora de escopo / riscos
```

## Histórico

| Nº | Etapa | Status |
|---|---|---|
| 00 | [Fundação](00-fundacao.md) | ✅ concluída |
