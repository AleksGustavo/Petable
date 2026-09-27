# 07 — Relatórios gerenciais — Relatório de implementação

Especificação: [07-relatorios.md](07-relatorios.md). Branch: `feat/relatorios` (a partir de `feat/financeiro`). Nada foi commitado.

**Resultado: PASS.**
- unitários: **286/286**
- integração: **232/232**
- frontend: **240/240**

## Banco

- `V9__indices_relatorios.sql` cria dois índices:
  - `idx_servico_realizado_data (data_servico)`;
  - `idx_conta_pagar_status_pagamento (status, paga_em)`.
- Os demais filtros por período já tinham índice (V6–V8).
- V1–V8 não foram tocadas. No banco local do Docker, o Flyway aplicou V9 sobre V8 (`success = 1`), e os dois índices foram conferidos com `SHOW INDEX`.

## Backend (módulo `relatorio`)

**Leitura.**
- `RelatorioService` é `@Transactional(readOnly = true)`.
- Cada relatório é um `SELECT` agregado com `NamedParameterJdbcTemplate`; nenhuma entidade JPA é carregada para somar.
- `RelatorioCalculos` faz só as contas finais, em BigDecimal HALF_UP: ticket médio, percentuais, curva ABC, giro, cobertura, margem % e o preenchimento com zero dos dias sem movimento.

**Período.**
- O record `Periodo` valida as datas: ambas obrigatórias, fim ≥ início, no máximo 366 dias.
- Violação → 400 com `errors.dataInicio` ou `errors.dataFim`.

**Endpoints** (todos `GET /api/v1/relatorios/{tipo}?dataInicio&dataFim`):

| Relatório | O que traz |
|---|---|
| `vendas` | quantidade, bruto, descontos, líquido e ticket médio; canceladas à parte; série por dia sem buracos; por forma de pagamento e por operador |
| `produtos-mais-vendidos` | ranking; `limite` de 1 a 100, padrão 20 |
| `curva-abc` | classe pelo acumulado **antes** do item: < 80% A, < 95% B, resto C |
| `estoque` | valor em estoque, giro e cobertura em dias |
| `margem` | receita menos custo estimado |
| `servicos` | por serviço e por funcionário |
| `despesas` | saídas das categorias de despesa menos estornos; ignora `CAIXA_%` |
| `caixas` | caixas fechados, faltas e sobras por operador |
| `contas` | pagas no período; vencidas e a vencer em aberto na data de hoje |

- O custo usado em `estoque` e `margem` é o **custo médio atual**. A resposta traz um `aviso` dizendo isso e o total de produtos sem custo.

**CSV.**
- Rota: `GET /api/v1/relatorios/{tipo}/csv`.
- Formato: BOM UTF-8, `;`, CRLF, vírgula decimal, datas `dd/MM/aaaa`, com linha de total quando faz sentido.
- Nome do arquivo: `relatorio-<tipo>-<inicio>-<fim>.csv`.
- Tipo desconhecido → 404.

## Segurança

- **ADMIN** vê os 9 relatórios.
- **VENDAS** vê só `vendas`, `produtos-mais-vendidos` e `curva-abc`. Não vê custo, margem, despesas, caixas nem contas.
- **SERVICO** não acessa nenhum.
- O bloqueio vale **por URL** no `SecurityConfig` (403 antes de validar o período) e também por `@PreAuthorize` no controller.
- O CSV aplica a mesma regra: VENDAS em `margem/csv` recebe 403.
- O download pelo navegador passa por um route handler do Next (`/api/relatorios/[tipo]/csv`):
  - o token fica no cookie httpOnly;
  - o handler repassa `Content-Type` e `Content-Disposition`;
  - antes de chamar a API, ele já barra tipo inexistente (404), papel sem permissão (403) e período inválido (400).

## Auditoria

- Cada exportação gera `RELATORIO_EXPORTADO` com entidade `relatorio`, id = tipo, e nos detalhes o período e o número de linhas.
- Visualizar um relatório não é auditado.
- Nenhum dado pessoal vai para os detalhes.

## Frontend

**Rotas.**
- `/relatorios` — hub com um card para cada relatório que o papel pode ver.
- `/relatorios/[tipo]` — página do relatório:
  - período por GET (funciona sem JS);
  - atalhos Hoje, 7 dias, Mês e 30 dias;
  - "Exportar CSV" e "Imprimir";
  - erro de período mostrado em `role="alert"`, e aí o link de exportação some;
  - tipo inexistente → 404; papel sem permissão → redirecionado ao hub;
  - `loading.tsx` com esqueleto.
- `(impressao)/relatorios/[tipo]/imprimir` — versão A4 sem AppShell. Usa a classe `.tema-claro` (nova, em `globals.css`), porque o papel é sempre claro, mesmo com o sistema em modo escuro.

**Componentes e estrutura.**
- `relatorio-ui.tsx`:
  - `Indicadores`;
  - `BarrasHorizontais`: gráfico em CSS; as barras são `aria-hidden` e rótulo e valor ficam em texto;
  - `TabelaResponsiva`: cartões no celular e tabela a partir de md; também a tabela na impressão.
- `relatorio-conteudo.tsx`: um renderizador por tipo, com `switch` exaustivo sobre a união `DadosRelatorio`.
- `relatorio-filtros.tsx`: filtros e hub.
- `lib/relatorios.ts`: catálogo, permissões, regras do período e tipos das respostas.
- `lib/relatorios-server.ts`: `lerPeriodo` e `carregarRelatorio`.
- AppShell: "Relatórios" saiu de "Em breve" e foi para a nova seção **Gestão**, visível para ADMIN e VENDAS.

## Testes

**unitários: 286/286** (13 novos)
- `RelatorioCalculosTest` (10): ticket, percentual, limites da ABC, curva com empate e total zero, dias zerados, giro, cobertura e margem.
- `CsvTest` (3): BOM, separador, escape e formatação de número e data.

**integração: 232/232** (16 novos)
- `RelatorioControllerIT` monta os dados com `SimpleJdbcInsert` em datas isoladas (2031 e 2032). Assim os números esperados são exatos, sem interferência dos outros ITs.
- Casos cobertos:
  - vendas com cancelada fora do faturamento e dias zerados;
  - ranking e ABC;
  - estoque e margem com o aviso de custo atual;
  - serviços;
  - despesas sem sangria e suprimento, e com estorno descontado;
  - caixas;
  - contas;
  - período inválido e maior que 366 dias;
  - 401 sem token; 403 para VENDAS nos relatórios de custo e financeiro; 403 para SERVICO;
  - CSV com auditoria;
  - 404 de tipo inexistente.

**frontend: 240/240** (23 novos, nos 7 arquivos do §21)
- `lib/relatorio-periodo.test.ts`: padrão, atalhos (incluindo a virada de mês e o ano bissexto), limite de 366 dias e permissões por papel.
- `relatorio-graficos.test.tsx`: largura proporcional, tudo zerado, negativo e tabela responsiva.
- `relatorios-hub.test.tsx`: ADMIN 9 cards, VENDAS 3, vazio.
- `relatorio-vendas.test.tsx`: indicadores, canceladas à parte, dias zerados, rótulos de forma, vazio.
- `relatorio-abc.test.tsx`: classe por produto, resumo e ranking.
- `relatorio-margem.test.tsx`: aviso de custo, margem negativa em vermelho, giro e cobertura nulos exibidos como "—".
- `relatorio-filtros.test.tsx`: GET, atalho ativo, links de CSV e impressão, erro de período.

## Validações

| Item | Resultado |
|---|---|
| backend build + testes (`./mvnw -B clean verify`) | PASS (286 + 232) |
| frontend build (`npm run build`) | PASS (rotas `/relatorios`, `/relatorios/[tipo]`, `/relatorios/[tipo]/imprimir`, `/api/relatorios/[tipo]/csv`) |
| lint (`eslint --max-warnings=0`) | PASS |
| typecheck (`tsc --noEmit`) | PASS |
| Docker (`docker compose up -d --build`) | PASS (api healthy, Flyway V9 aplicada) |
| E2E API | PASS |
| Navegador | PASS |

**E2E pela API** (banco local, mês corrente):
- **Vendas bate com o banco.** O relatório deu R$ 154,60 em 3 vendas; um `SUM` direto no MySQL deu o mesmo.
- **Os 9 relatórios responderam 200.**
- **Diferença esperada entre vendas e ranking.** Produtos mais vendidos soma R$ 159,60 porque conta os itens antes do desconto de R$ 5,00 dado na venda.
- **CSV.** Veio com BOM e o nome de arquivo correto, e o "í" do cabeçalho está em UTF-8 válido.
- **Erros de período e acesso.** Período invertido → 400; período maior que 366 dias → 400; sem token → 401.
- **Usuário VENDAS.** `vendas` e `curva-abc` → 200; `margem`, `estoque`, `contas` e `margem/csv` → 403.
- **Auditoria.** Registrou `RELATORIO_EXPORTADO`.

**Navegador** (usuário ADMIN temporário, desativado ao final):
- Hub, vendas e margem conferem com a API.
- Em 375 px, a curva ABC, os caixas e a página com erro de período não geram rolagem horizontal (`scrollWidth` = 375).
- O CSV baixado pelo BFF veio 200, com BOM e `attachment`. O BFF devolveu 400 para período inválido e 404 para tipo inexistente.
- A impressão sai sem AppShell e legível.
- O menu mostra a seção "Gestão → Relatórios".

## Divergências

1. **Página de impressão.** A spec não previa tema. No modo escuro do sistema, os cartões saíam escuros com texto preto. Criei `.tema-claro`, que redefine só os tokens de cor, e apliquei na página.
2. **Vendas × ranking.** O faturamento dos rankings (mais vendidos e ABC) é a soma dos itens, antes do desconto da venda. O líquido do relatório de vendas é depois do desconto. Isso está documentado na tela (bruto × líquido) e neste relatório. Ratear o desconto por item exigiria regra de negócio nova.
3. **Série diária longa.** O gráfico "Faturamento por dia" mostra todos os dias do período, inclusive os zerados, como pede a spec. Em períodos longos fica comprido. Agregar por semana ou mês fica como melhoria futura.

## Pendências

- Nada foi commitado; as etapas 01–07 seguem sem commit nesta cadeia de branches. Quando quiser, eu faço os commits (um por etapa) e abro o PR.
- A tarefa sugerida antes (`task_a9d563e9`, `key` nos `<select>` dos formulários antigos) continua em aberto.
- No banco local, o caixa #1 do admin foi fechado na etapa 06 com falta de R$ 5,00 (dado de teste). É esse que aparece no relatório de caixas. O caixa #2 continua ABERTO.

## Handoff da etapa 08

A especificação completa da etapa 08 já está escrita em **[08-migracao-legado.md](08-migracao-legado.md)**. Não precisa passar pelo ChatGPT: basta pedir ao Claude Code "execute a etapa 08".

Se ainda assim quiser revisar a spec no ChatGPT, cole o arquivo inteiro com esta instrução no topo:

```
Revise a especificação abaixo da etapa 08 do PetConnect Web (migração dos dados do sistema PHP/MySQL legado para o novo
Spring Boot 4 + Next.js 16 + MySQL 8.4/Flyway V1–V9 + MongoDB de auditoria). NÃO reescreva do zero e NÃO devolva este
prompt: devolva o MESMO arquivo markdown, com as mesmas seções numeradas, apenas corrigindo inconsistências, riscos
não cobertos ou regras ambíguas. Mantenha as restrições da §2. Responda somente com o markdown final.
```
