# Status — Lotes e módulos

> Arquivo de retomada: leia só este para continuar em outra sessão. Plano de origem: `00_LEIA_PRIMEIRO_ORDEM_E_REGRAS.md` (ChatGPT; cópia nesta pasta).
> Regras fixas: migration aditiva, nada de merge na `main`, deploy ou migração destrutiva sem autorização; relatório curto ao fim de cada etapa.

## Situação atual

| Ordem | Etapa | Estado | Evidência |
|---|---|---|---|
| 01 | Diagnóstico e contratos | **concluída** (mesclada, PR #14) | [01_MAPA_ATUAL.md](01_MAPA_ATUAL.md), [01_DECISOES_LOTES.md](01_DECISOES_LOTES.md) |
| 02 | Domínio e banco de lotes (`V11`) | **implementada, aguardando revisão/merge do PR** | [02_DOMINIO_E_BANCO.md](02_DOMINIO_E_BANCO.md), `V11__lotes_estoque.sql` |
| 03 | Movimentações, FEFO e concorrência | pendente | — |
| 04 | Telas, alertas e operação | pendente | — |
| 05 | Testes de lotes e homologação | pendente | — |
| 06 | Catálogo de módulos e dependências | pendente | — |
| 07 | Autorização e isolamento modular | pendente | — |
| 08 | Provisionamento e operação por cliente | pendente | — |
| 09 | Testes modulares e go-live | pendente | — |
| 10 (futura) | Unidade de medida e quantidade decimal (granel) | planejada, **pré-requisito do pacote agropecuário**; fora deste plano de 9 etapas | D24 em [01_DECISOES_LOTES.md](01_DECISOES_LOTES.md) |

## Etapa 01 — checklist

- [x] Repositório, branch, stack, migrations (V1–V10), testes e CI confirmados no código
- [x] Fluxo compra → recebimento → saldo → venda PDV → cancelamento → financeiro/caixa mapeado (classes, transações, ordem de travas, endpoints, DTOs, auditoria)
- [x] Cadastro, unidade, local, reservas, ajustes, perdas, transferências, custos e "lote" de vacina verificados (o que existe e o que não existe)
- [x] Multiempresa, feature flags, RBAC e deploy da etapa 09 verificados (não há multiempresa nem flag de módulo)
- [x] Integrações verificadas: leitor de código de barras existe; NF-e, offline e lotes legados **não existem**
- [x] Modelo de lote proposto sem duplicar o livro-razão (chave produto + lote; justificativa para não ter local)
- [x] Contrato de migração do saldo legado sem lote (sem inventar lote nem validade)
- [x] Matriz de regras (22 decisões) com justificativa e impacto
- [x] Reconciliação do saldo verificada no banco de desenvolvimento: 10 de 10 produtos conferem, 0 encadeamentos quebrados
- [x] Nenhuma alteração no comportamento de produção (somente `docs/implementacao/`)

## Evidências da etapa 01

| Verificação | Comando / fonte | Resultado |
|---|---|---|
| Estado do repositório | `git log`, `gh pr view` | `main` em `7ee3936`; PR #13 mesclado |
| Quem escreve o saldo | `grep` de `estoque.(ajustar\|registrar*)` e `.movimentar(` | só `EstoqueService` (4 chamadores) |
| Reconciliação | SQL em `01_MAPA_ATUAL.md` §7 | 10/10 conferem |
| Multiempresa / NF-e / offline | `grep` por termos exatos em `backend` e `frontend` | 0 ocorrências |
| Testes existentes | listados em `01_MAPA_ATUAL.md` §8 | **não executados nesta etapa** (nenhum código mudou); a linha de base vem do CI da `main` (346 + 267 + 249 + 6 E2E, tudo verde) |

## Decisões fechadas (dono)

| # | Pergunta | Decisão |
|---|---|---|
| Q1 | Granel (kg/litro) neste ciclo? | **Não.** Lotes com quantidade `INT`. Etapa futura de unidade e decimal (uma migration para todas as colunas de quantidade), pré-requisito do pacote agropecuário (D7, D24) |
| Q2 | Dias de bloqueio antes do vencimento | **Global (padrão 0) + sobrescrita por produto**; **lote vencido sempre bloqueado, sem exceção**; alertas de 30/60/90 dias na etapa 04 (D4, D23) |
| Q3 | Cancelamento com produto danificado | **Escolha explícita** `RETORNAR` ou `DESCARTAR` (sem padrão) para produto com lote; `DESCARTAR` grava `PERDA`; dinheiro estornado nos dois casos; produto sem lote não muda (D9) |

Sem pontos abertos bloqueadores para a etapa 02.

## Etapa 02 — checklist

- [x] `V11__lotes_estoque.sql` aditiva: colunas em `produto` e `movimentacao_estoque`, tabelas `lote_estoque`, `item_venda_lote`, `item_compra_lote`, CHECK de tipo ampliado, índice FEFO
- [x] Entidades, repositórios, `LoteEstoqueService` e `LotesProperties` (flag `petconnect.estoque.lotes.habilitado` = falso; margem global 0)
- [x] Nenhuma rota nova; nenhum fluxo de venda/compra/ajuste alterado
- [x] Testes: migration V10 → V11 com dados legados, CRUD técnico, constraints, índices, FEFO, rastreio, reprocessamento (ver `02_DOMINIO_E_BANCO.md` §6)
- [x] Dicionário de dados e ER atualizados (33 tabelas); `scripts/gerar-dicionario.py` conhece as tabelas novas
- [x] Rollback operacional documentado (`02_DOMINIO_E_BANCO.md` §5)
- [ ] Revisão e merge do PR pelo dono (regra fixa: nunca mesclar na `main` sem autorização)

## Evidências da etapa 02

| Verificação | Comando | Resultado |
|---|---|---|
| Unitários do domínio | `./mvnw -q -Dtest=LoteEstoqueTest test` | 9/9 |
| ITs novos | `./mvnw verify -Dit.test='LoteEstoqueIT,MigracaoLotesV11IT'` | 22/22 + 1/1 |
| Suíte completa do backend | `./mvnw verify` | 355 unitários + 290 ITs, BUILD SUCCESS |

## Próximo passo

Revisão e merge do PR da etapa 02. Depois, o dono envia `03_MOVIMENTACOES_FEFO_E_CONCORRENCIA.md`: entrada no recebimento, baixa FEFO na venda, cancelamento com `RETORNAR`/`DESCARTAR`, ajuste/perda/vencimento, regularização do saldo legado e testes de concorrência.
