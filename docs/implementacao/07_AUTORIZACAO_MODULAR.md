# Etapa 07 — Enforcement modular de API, menu e permissões

> Aplica o catálogo da etapa 06 (`backend/src/main/resources/modulos/catalogo.json`). **Módulo (licença) ≠ papel (RBAC):** o acesso exige os dois, inclusive em chamada direta por `curl`. A matriz endpoint → módulo → papel está versionada em [07_MATRIZ_ACESSO.md](07_MATRIZ_ACESSO.md) e é verificada por teste.

## 1. Fonte de verdade

- **Variável `PETCONNECT_MODULOS`** (por instalação, lida no boot). `todos` (padrão do ambiente de desenvolvimento e das instalações existentes) ou lista de ids separada por vírgula (`produtos,caixa,pdv`).
- Vazio, id desconhecido ou dependência faltando (`pdv` sem `caixa`, `lotes` sem `estoque`...) **recusa o boot** com mensagem que diz o que corrigir. Em produção (`compose.prod.yaml`) a variável é **obrigatória**: omitir não abre tudo sem querer.
- **Nada vem do cliente.** Payload, cabeçalho, cookie, parâmetro e até claim do token são ignorados; o token não carrega módulos.
- **Sem hot reload:** mudar a lista exige reiniciar a API. Em troca não existe janela em que duas instâncias discordem nem cache de licença a invalidar. Sessões (tokens) continuam válidas: o que muda é a resposta da API, avaliada a cada requisição.
- **Histórico e auditoria:** a cada partida em que a lista mudou, grava-se uma linha em `modulo_instalacao_historico` (V13, tabela geral, sem migration por cliente) e a auditoria `MODULOS_ALTERADOS` (ator `sistema`).

## 2. Enforcement na API

| Camada | Onde | Resultado |
|---|---|---|
| 1. Autenticação | Spring Security (JWT) | 401 sem token |
| 2. **Módulo** | `ModulosFilter` (após autenticar, antes do RBAC) | **403** `MODULO_INDISPONIVEL`, corpo fixo |
| 3. Papel | `RegrasDeAcesso` (URL) + `@PreAuthorize` | 403 padrão |

- **403 uniforme, sem citar o módulo.** 404 não é segurança (o mesmo caminho existe para quem contratou). Sem token continua 401, então a API não revela quais módulos existem.
- O caminho é **decodificado e normalizado** antes de resolver o dono (`%66inanceiro`, barra final, `HEAD`→`GET`); `;` e `..` já são barrados pelo firewall do Spring Security.
- Toda rota tem dono no catálogo (teste de arquitetura da etapa 06); rota sem dono, ou dono inexistente, é falha de build.
- **Relatórios e exportação (CSV)** dependem do módulo de origem: com `produtos,estoque,caixa,pdv,relatorios` e sem `financeiro`, os relatórios `contas`, `despesas` e `servicos` ficam 403 também no CSV, e `GET /api/v1/modulos` não os lista.

## 3. O que continua funcionando sem o módulo (núcleo técnico)

Desligar um módulo desliga **exposição** (rotas, telas, ações), nunca o invariante:

| Sem... | Continua |
|---|---|
| `estoque` (com `pdv`) | A venda baixa o saldo, grava movimentação e lança a receita; cancelar recompõe. Ajuste, histórico, reconciliação e resumo ficam 403. |
| `lotes` (produto já controlado) | FEFO na venda, recomposição do lote original no cancelamento, ajuste com `loteId` pelo estoque. Consulta, alertas, controle, bloqueio, perda e vencimento ficam 403. |
| `financeiro` | O PDV continua gravando o lançamento e o vínculo com o caixa. As APIs financeiras (lançamentos, fluxo, contas a pagar, resumo) ficam 403. |

Um módulo contratado **não** autoriza ação privilegiada: `VENDAS`/`SERVICO` recebem 403 do RBAC (sem `codigo`) em ajuste e cancelamento mesmo com estoque e PDV ativos.

## 4. Frontend

- `GET /api/v1/modulos` (qualquer usuário autenticado): `{ todos, modulos[], integracoes[], relatorios[], versaoCatalogo }`. Uma consulta por requisição (`lib/modulos.ts`), nunca em cache entre usuários, nunca em página estática.
- **Menu** (`app-shell`): o item aparece se o módulo da rota (`lib/capacidades.ts#moduloDaRota`) está ativo **e** o papel permite.
- **Guardas por segmento:** cada segmento de rota tem `layout.tsx` com `ModuloExigido`; URL direta mostra "Recurso indisponível" (sem citar módulo) e o conteúdo da página nem é renderizado.
- **Cards e abas:** o painel só chama o endpoint dos módulos ativos; produto sem `estoque` não tem aba de estoque, sem `lotes` não tem aba de lotes, vacinas dependem da integração `vacina-estoque`; pet sem `clinica` só tem a aba de dados; cliente sem `pets` não lista pets; relatórios filtram pela origem.
- **Isso é conveniência, não fronteira:** o SSR/middleware não decide nada de segurança; a API é a única barreira. Se a consulta de capacidades falhar, a interface não esconde nada e a API continua barrando.
- Erros: `mensagemDeErro(403, detail)` mostra o texto amigável quando o 403 é o de módulo desligado.

## 5. Testes

| Arquivo | Cobre |
|---|---|
| `ModulosAtivosTest`, `ModulosFilterTest` | análise da lista, dependências, resolução do dono, corpo do 403 |
| `ModulosSemEstoqueIT` | `produtos,caixa,pdv`: venda baixa saldo e lança receita; inventário e demais módulos 403; sem token 401; cabeçalho/claim/cookie não reabilitam; variações de caminho |
| `ModulosSemLotesIT` | FEFO e cancelamento continuam; rotas de lotes 403; papel insuficiente |
| `ModulosSemFinanceiroIT` | PDV mantém o registro no núcleo; APIs financeiras e relatórios de origem desligada 403 (inclusive CSV) |
| `ModulosRegistroIT` | histórico/auditoria, capacidades, downgrade bloqueado por pendência (consultas do catálogo executadas no MySQL) |
| `MatrizAcessoIT` | gera a matriz e compara com `07_MATRIZ_ACESSO.md` (`MATRIZ_ATUALIZAR=true` regenera) |
| `capacidades.test.ts` | menu/rota → módulo no frontend |

## 6. Operação

Ver [runbook de módulos](../runbooks/modulos-por-cliente.md).

## 7. Fora desta etapa

Provisionamento por cliente, cobrança e troca em produção ficam para a etapa 08. Nenhum deploy, DNS ou dado foi tocado.
