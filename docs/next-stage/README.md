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
| 01 | [Pets, carteira de vacinação e consultas](01-pets.md) | ✅ concluída ([relatório](01-pets-relatorio.md)) |
| 02 | [Agendamentos, serviços e execução](02-agendamentos.md) | ✅ concluída ([relatório](02-agendamentos-relatorio.md)) |
| 03 | [Usuários, funcionários, perfil e recuperação de senha](03-usuarios-funcionarios.md) | ✅ concluída ([relatório](03-usuarios-funcionarios-relatorio.md)) |
| 04 | [Produtos, estoque, fornecedores e compras](04-produtos-estoque.md) | ✅ concluída ([relatório](04-produtos-estoque-relatorio.md)) |
| 05 | [PDV e vendas](05-pdv-vendas.md) | ✅ concluída ([relatório](05-pdv-vendas-relatorio.md)) |
| 06 | [Financeiro](06-financeiro.md) | ✅ concluída ([relatório](06-financeiro-relatorio.md)) |
| 07 | [Relatórios gerenciais](07-relatorios.md) | ✅ concluída ([relatório](07-relatorios-relatorio.md)) |
| 08 | [Migração dos dados legados](08-migracao-legado.md) | ✅ concluída ([relatório](08-migracao-legado-relatorio.md)) |
| 09 | [Produção e operação](09-producao-operacao.md) | ✅ concluída ([relatório](09-producao-operacao-relatorio.md)) |
| 10 | App mobile (Flutter) consumindo a API | 🔜 próxima ([prompt para o ChatGPT](10-prompt-chatgpt.md)) |
