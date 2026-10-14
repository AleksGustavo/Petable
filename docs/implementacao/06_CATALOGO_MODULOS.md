# Etapa 06 — Catálogo de módulos e dependências

> **Desenho**, não imposição: nada aqui bloqueia rota ainda (isso é a etapa 07). O que existe de código nesta etapa é o **catálogo legível por máquina** ([`modulos/catalogo.json`](../../backend/src/main/resources/modulos/catalogo.json)) e os **testes que o mantêm honesto**: toda rota da API, tabela do banco, tela, rota do BFF, Server Action e item de menu tem **exatamente um dono**, e o catálogo não promete nada que não exista. Base: etapas 01 a 05 (lotes) e o código da `main` em `ace982e`.

## 1. Princípios

1. **Uma instalação lógica por cliente**: um banco, um MongoDB, um volume de fotos, segredos e backups **por cliente**. Não há multi-tenancy por linha nem microsserviços. Domínio e servidor podem ser compartilhados; o resto, não (a etapa 08 detalha o provisionamento).
2. **Um produto, um código, um schema completo.** O banco de todo cliente tem **todas** as tabelas; ligar um módulo não migra schema por cliente. As migrations gerais seguem a versão do produto (Flyway roda sempre inteiro).
3. **Módulo comercial decide exposição, não existência de código.** O código é um monólito cujos pacotes são **fortemente acoplados** (§3): não dá para "tirar" um pacote da compilação. O módulo liga ou desliga **rotas, telas, ações, cartões do painel e relatórios**; as regras internas continuam executando (§4).
4. **Três camadas de acesso, sempre separadas:** ao **módulo** (a instalação contratou?), às **ações** (papel do usuário: RBAC) e ao **registro** (escopo do usuário/empresa). Hoje só existe a segunda (§7).
5. **Nenhuma proteção só no frontend.** O frontend esconde botões; a API decide. O catálogo vale para a API primeiro (a etapa 07 aplica o módulo na API antes de qualquer menu).

## 2. Inventário do que existe (do código, não de hipótese)

| Camada | Quantidade (verificada por teste) |
|---|---|
| Rotas da API (Spring MVC) | 132 mapeamentos: 126 da aplicação e 6 de infraestrutura (`/error`, OpenAPI, Swagger). Todos com dono no catálogo; o IT `CatalogoModulosRotasIT` reprova rota sem dono |
| Tabelas MySQL | 33 (todas com dono; o mesmo IT confere contra o `information_schema`) |
| Telas Next.js (`page.tsx`) | 47 |
| Rotas do BFF (`/api/*`) | 7 |
| Server Actions | 45 em 8 arquivos |
| Itens de menu | 15 (14 fixos + "Lotes e validade", só com a flag) |
| Coleção MongoDB | auditoria (núcleo) |
| Jobs internos | **nenhum `@Scheduled`**. Só *runners* de partida: `AdminBootstrap` (núcleo, sempre), `FinanceiroBackfillRunner` (sob `--petconnect.financeiro.backfill`) e `MigracaoLegadoRunner` (ferramenta offline, flag desligada em produção). Backup, teste de restore e monitoramento são **scripts de cron do servidor** (`scripts/`) |
| Papéis | `ADMIN`, `VENDAS`, `SERVICO` (RBAC), em duas camadas na API: regras de URL em `SecurityConfig` **e** `@PreAuthorize` nos controllers (valem os dois) |

## 3. Dependências técnicas reais (quem importa quem)

Extraído dos `import` do `main` (pacotes de primeiro nível). **O grafo tem ciclos**, por isso a modularização é de exposição e não de empacotamento:

```
financeiro ⇄ venda        financeiro ⇄ compra        financeiro ⇄ agendamento
produto ⇄ compra          produto ⇄ venda            produto ⇄ pet   (vacina ligada a produto, etapa 04)
agendamento → pet, servico, funcionario, venda        venda → cliente, funcionario, usuario, pet
compra → fornecedor, funcionario, pet                  fornecedor → compra        servico → pet
relatorio → (SQL direto sobre as tabelas de todos os módulos)
```

Achados que mudam o catálogo:
- **O caixa é exigido por venda, atendimento e pagamento de conta** (`caixas.exigirAberto`, `travarAberto`). Logo `pdv`, `agenda` e `financeiro` **dependem do caixa**. A hipótese comercial não previa o módulo `caixa`; foi acrescentado.
- **O livro de lançamentos financeiros é escrito por venda, serviço, compra e caixa**, sempre. Isso é **núcleo técnico** (sempre executa); o módulo comercial `financeiro` só expõe lançamentos manuais, fluxo, contas a pagar e relatórios.
- **O saldo de estoque é escrito por compra, venda, cancelamento, ajuste, vacina e migração**, sempre pelo serviço interno de estoque. Também **núcleo técnico**.
- **`produto` junta cadastro e estoque** na mesma tabela. O módulo `produtos` é o cadastro; `estoque` é a exposição gerencial (aba Estoque, ajuste, histórico, mínimo, reconciliação).
- **O serviço "Vacinação" da agenda grava na carteira (clínica)** e, com vacina ligada a produto, baixa o lote. Há dependência **condicional** `agenda → clinica` quando existe serviço de vacinação ativo.
- **Relatórios** são SQL direto: cada relatório só faz sentido com o módulo de origem (`porRelatorio` no catálogo).

## 3.1. Núcleo técnico (sempre executa, mesmo sem o módulo comercial)

| Capacidade interna | Quem a usa | Por quê não é desligável |
|---|---|---|
| Identidade, sessão, papéis, auditoria | tudo | segurança |
| Serviço de estoque (saldo e livro de movimentos) | compras, pdv, lotes, vacina | saldo coerente e rastreável |
| **FEFO e regras de lote de produto já controlado** | pdv, compras, vacina | rastreabilidade e segurança sanitária: **desligar o acesso gerencial a lotes não desliga o FEFO** (§4.3) |
| Livro de lançamentos financeiros e conta a pagar automática | pdv, agenda, compras, caixa | contabilidade fiel mesmo sem as telas |
| Idempotência, transações, travas de concorrência | pdv, compras, caixa | integridade |

## 4. Catálogo comercial validado contra o código

Legenda de **status**: `EXISTE` (implementado e testado), `PARCIAL` (existe só parte do que o nome sugere). Dependência **`requer`** = obrigatória (o boot recusa sem ela); **`recomenda`** = vale a pena, mas funciona sem.

| ID | Módulo | Status | `requer` | `recomenda` | Telas | Dados | Observação |
|---|---|---|---|---|---|---|---|
| — | **Núcleo** (sempre) | EXISTE | — | — | `/`, `/login`, `/esqueci-senha`, `/redefinir-senha`, `/dashboard`, `/perfil`, `/usuarios/**` | `usuario`, `papel`, `token_redefinicao_senha`, `funcionario`, `endereco`, auditoria (Mongo) | identidade, funcionários, perfil, auditoria, casca do painel |
| `clientes` | Clientes | EXISTE | — | — | `/clientes/**` | `cliente` | inclui exportar/anonimizar titular (LGPD, só ADMIN) |
| `produtos` | Cadastro de produtos | EXISTE | — | — | `/produtos/**` | `produto`, `categoria_produto` | o saldo só muda pelo serviço interno |
| `estoque` | Gestão de estoque | EXISTE | `produtos` | — | aba Estoque do produto | `movimentacao_estoque` | ajuste manual, histórico, mínimo, reconciliação; entradas vêm de `compras` |
| `lotes` | Lotes e validade | EXISTE | `estoque` | — | `/estoque/lotes/**`, aba Lotes | `lote_estoque`, `item_venda_lote`, `item_compra_lote` | etapas 01 a 05; homologado; flag desligada por padrão |
| `caixa` | Caixa | EXISTE | — | — | `/caixa/**` | `caixa`, `caixa_movimento` | **novo**: exigido por pdv, agenda e financeiro |
| `pdv` | Ponto de venda | EXISTE | `produtos`, `caixa` | `clientes` | `/pdv`, `/vendas/**` | `venda`, `item_venda` | cliente é opcional na venda |
| `compras` | Compras e fornecedores | EXISTE | `produtos` | `estoque`, `financeiro` | `/compras/**`, `/fornecedores/**` | `compra`, `item_compra`, `fornecedor` | **fornecedores ficam aqui** (só a compra os usa) |
| `financeiro` | Gestão financeira | EXISTE | `caixa` | — | `/financeiro/**` | `conta_pagar`, `movimentacao_financeira` | pagar conta exige caixa aberto |
| `pets` | Pets | EXISTE | `clientes` | — | `/pets/**` | `pet`, `especie`, `raca` | foto em disco, isolada por instalação |
| `clinica` | Clínica veterinária | **PARCIAL** | `pets` | `agenda` | seções do detalhe do pet | `carteira_vacina`, `historico_consulta`, `vacina` | **existe:** carteira de vacinação e histórico de consultas. **não existe:** prontuário completo, prescrição, exames, internação |
| `agenda` | Agenda e serviços | EXISTE | `pets`, `caixa` | — (condicional: `clinica` se houver serviço de vacinação) | `/agendamentos/**`, `/servicos/**` | `agendamento`, `servico_realizado`, `servico`, `preco_servico_porte` | concluir exige caixa e lança a receita |
| `relatorios` | Relatórios | EXISTE | — (cada relatório requer o seu módulo) | — | `/relatorios/**` | (lê as tabelas dos outros) | vendas, mais vendidos, curva ABC e margem → `pdv`; estoque → `estoque`; serviços → `agenda`; despesas e contas → `financeiro`; caixas → `caixa` |
| — | Integração `vacina-estoque` | EXISTE | `clinica` e `lotes` | — | seção do produto | (colunas em `vacina` e `carteira_vacina`) | só existe com os dois módulos; aplicar baixa 1 dose pelo FEFO |

**Desvios da hipótese comercial (e por quê):** `cadastros` foi **dividido** em `clientes` e `produtos` (a clínica precisa de clientes sem produtos; o PDV de produtos sem clientes), `fornecedores` foi para `compras`, e acrescentou-se `caixa`. `clinica` ficou `PARCIAL` e `agropecuária` não é pronta (§6).

### 4.1. Pontos de composição (telas compartilhadas)

Uma tela pode misturar módulos; a etapa 07 esconde a **seção** e a API recusa a **rota**:

| Tela | Seção | Módulo |
|---|---|---|
| `/produtos/[id]` | aba Estoque / aba Lotes / vacinas do produto | `estoque` / `lotes` / `vacina-estoque` |
| `/pets/[id]` | carteira de vacinação e consultas / próximos agendamentos | `clinica` / `agenda` |
| `/dashboard` | clientes / meu caixa / vendas do dia / financeiro / estoque mínimo / validade | `clientes` / `caixa` / `pdv` / `financeiro` / `estoque` / `lotes` |
| `/vendas/[id]` | destino do estoque no cancelamento | `lotes` |
| `/pdv` | seleção de cliente | `clientes` |
| `/agendamentos/[id]` | conclusão de vacinação | `clinica` |

### 4.2. Regras especiais

1. **`pdv` consome o serviço de estoque sem habilitar `estoque`.** Vender baixa saldo e FEFO; o ajuste, o histórico e o relatório de estoque só aparecem com o módulo.
2. **`lotes` não é desligável com produto controlado** (§5). Desligar o **acesso gerencial** (telas, alertas, ações) não muda que o produto controlado vende por FEFO, aceita ajuste/perda/bloqueio pela API de serviço e a vacina vinculada baixa a dose. Isso já é como o código se comporta (achado A2 da homologação).
3. **`compras` sem `financeiro`:** a conta a pagar é criada (núcleo técnico), mas ninguém a paga pela tela. Por isso `financeiro` é *recomendado* a `compras`.
4. **`relatorios` não é um módulo de "tudo":** é uma coleção, e cada relatório depende do módulo que gera o dado.

## 5. Configuração, upgrade e downgrade

### 5.1. Onde fica a configuração (decisão)

**Variável de ambiente por cliente na primeira versão:** `PETCONNECT_MODULOS=pdv,caixa,produtos,estoque` (lista de ids).

| Alternativa | Decisão |
|---|---|
| **Env por cliente (escolhida)** | simples, versionada junto do `.env` de cada instalação, sem migration por cliente, auditável pelo deploy |
| Tabela no banco do cliente | depois: exige tela de administração, bootstrap e proteção contra edição indevida; a tabela **geral** de histórico (abaixo) já prepara o caminho |
| Licença assinada | depois, quando houver cobrança recorrente automatizada; troca a *fonte* da lista, não o modelo |

Regras (**a implementar na etapa 07**; validadas no boot, com erro que explica):
1. **Padrão para instalação existente = todos os módulos** (nenhum cliente atual perde funcionalidade ao atualizar). Instalação nova do pacote comercial define a lista explicitamente.
2. **Id desconhecido recusa o boot** (digitação errada não vira módulo faltando em silêncio).
3. **Fecho de dependências:** todo `requer` precisa estar na lista, com a mensagem "o módulo X exige Y". `relatorios` só publica os relatórios cujo módulo de origem está ativo.
4. **Núcleo é implícito** e nunca aparece na lista.
5. A lista **efetiva** é registrada na auditoria e numa tabela **geral** (migration do produto, igual para todos) com o histórico de listas por boot. Isso permite detectar um downgrade **sem migration por cliente**.

### 5.2. Adicionar módulo (upgrade)

Basta incluir o id e reiniciar. **Sem migration por cliente**: o schema já está completo. As migrations gerais seguem a versão do produto. Se o módulo exige configuração própria (ex.: lotes: `PETCONNECT_ESTOQUE_LOTES`), o guia do módulo manda (`docs/runbooks/lotes-liberacao.md`).

### 5.3. Remover módulo (downgrade)

**Nunca apaga dado.** Tabelas, movimentos e histórico ficam intactos; o módulo apenas deixa de ser exposto. O boot **bloqueia** a remoção quando ela violar operação pendente ou compliance, com a mensagem do motivo (consultas sugeridas, já no catálogo em `bloqueiosDeRemocao`):

| Remover | Bloqueia enquanto | Verificação |
|---|---|---|
| `lotes` | houver produto que controla lote | `select count(*) from produto where controla_lote = true` |
| `caixa` | houver caixa aberto | `select count(*) from caixa where status = 'ABERTO'` |
| `pdv` | houver venda ainda cancelável (7 dias) | `select count(*) from venda where status = 'CONCLUIDA' and data_venda >= now() - interval 7 day` |
| `compras` | houver pedido em aberto | `select count(*) from compra where status = 'PEDIDO'` |
| `financeiro` | houver conta a pagar aberta | `select count(*) from conta_pagar where status = 'ABERTA'` |
| `agenda` | houver agendamento pendente | `select count(*) from agendamento where status in ('AGENDADO','CONFIRMADO','EM_ANDAMENTO')` |
| `clinica` | houver serviço de vacinação ativo | `select count(*) from servico where ativo = true and vacinacao = true` |
| `pets`, `clientes`, `produtos` | outro módulo ativo depender deles | o próprio `requer` |

**Automações obrigatórias continuam** (núcleo técnico, §3.1): saldo e livro de movimentos, FEFO de produto controlado, lançamentos financeiros automáticos, auditoria e idempotência. Remover `lotes` ou `financeiro` esconde telas; **não** para o rastreio nem a contabilidade.

## 6. Pacotes (bundles)

| Pacote | Módulos | Pronto? | Observação |
|---|---|---|---|
| **PDV** | `produtos`, `estoque`, `caixa`, `pdv`, `relatorios` | sim | balcão e caixa; sem clientes, compras, financeiro ou agenda |
| **Pet Shop** | `clientes`, `produtos`, `estoque`, `lotes`, `caixa`, `pdv`, `compras`, `financeiro`, `pets`, `agenda`, `relatorios` | sim | `lotes` é opcional no pacote (ração, medicamento) |
| **Agropecuária** | `clientes`, `produtos`, `estoque`, `lotes`, `caixa`, `pdv`, `compras`, `financeiro`, `relatorios` | **não** | venda a granel exige a etapa futura de **unidade e quantidade decimal (D24)**; também não existem venda a prazo, nota fiscal nem insumos agrícolas |
| **Clínica** | `clientes`, `pets`, `clinica`, `agenda`, `caixa`, `financeiro`, `relatorios` | **parcial** | só "agenda e carteira de vacinação"; prontuário completo, prescrição, exames e internação não existem. Para vacina baixar estoque, some `produtos`, `estoque`, `lotes` |
| **Completo** | todos os 12 módulos | sim | a parte clínica continua parcial |

O teste `CatalogoModulosTest` confere que cada pacote **fecha as dependências** (nenhum pacote inclui um módulo sem o que ele exige) e que o Completo tem todos os módulos.

## 7. Três camadas de acesso (estado atual e o que falta)

| Camada | Hoje | Falta |
|---|---|---|
| **Módulo** (a instalação contratou?) | **não existe** | etapa 07: filtro único na API que recusa a rota do módulo desligado (**404/403 não bastam sozinhos**: a verificação é na API, e o token nunca amplia módulo) |
| **Ação** (RBAC por papel) | existe: `ADMIN`, `VENDAS`, `SERVICO`; regras de URL no `SecurityConfig` + `@PreAuthorize` | manter; o módulo é **somado** ao papel (módulo E papel) |
| **Registro** (escopo do usuário/empresa) | **não existe** (sem multiempresa; o isolamento é por instalação). Dentro da instalação o usuário vê todos os registros do módulo; só o **caixa** é do próprio usuário | decisão futura, se algum cliente precisar de filiais |

**Verificação feita nesta etapa:** das rotas da API, **39 exigem apenas estar logado** (sem restrição de papel): leituras de listas e catálogos (clientes, pets, produtos, agendamentos, serviços, espécies), o caixa do próprio usuário, o perfil e a configuração de lotes. É esperado (leitura geral), mas **significa que o módulo é a única barreira** para elas: a etapa 07 precisa do filtro de módulo cobrindo *toda* rota, inclusive as de leitura. O papel já é conferido na API para as escritas sobre dados do negócio e para tudo que é gerencial; as exceções são o perfil e o caixa **do próprio usuário**, o registro de consulta e as transições de status do agendamento (que têm checagem por transição no serviço), todas listadas no apêndice como "qualquer usuário logado". O frontend (`permissoes.ts`) só espelha o que a API já exige, e nenhuma proteção depende dele.

## 8. Lacunas declaradas (o que **não** existe)

Multiempresa e escopo por registro; licenciamento assinado; nota fiscal; venda a prazo e conta a receber; venda a granel e quantidade decimal; transferência entre locais e inventário formal (o ajuste por lote faz o papel de contagem); prontuário completo, prescrição, exames, internação; notificação de vencimento por e-mail ou mensagem; operação offline; jobs agendados internos. Tudo isso consta em `ausencias` do catálogo e **não pode ser vendido como pronto**.

## 9. Como o catálogo é mantido honesto

| Verificação | Teste | O que reprova o build |
|---|---|---|
| Toda rota da API tem um único dono (mais específico ganha; empate é erro) | `CatalogoModulosRotasIT.todaRotaDaApiTemExatamenteUmDonoNoCatalogo` | endpoint novo sem módulo |
| O catálogo não promete rota inexistente | `…todaEntradaDeRotaDoCatalogoCorrespondeAUmaRotaReal` | rota citada que não existe |
| Toda tabela do banco tem dono e nenhuma do catálogo é fantasma | `…todaTabelaDoBancoTemDonoENenhumaTabelaDoCatalogoEFantasma` | migration com tabela nova sem módulo |
| Estrutura: ids, dependências sem ciclo, pacotes fecham o `requer`, ações sem repetição, bloqueios de remoção, ausências | `CatalogoModulosTest` (7) | módulo mal descrito |
| Toda tela, rota do BFF, Server Action e item de menu tem um único dono; nada fantasma | `frontend/src/lib/catalogo-modulos.test.ts` (7) | tela ou ação nova sem módulo |

Comandos: `cd backend && ./mvnw verify` e `cd frontend && npm test`.

## 10. Próximo passo (etapa 07)

Implementar a **autorização por módulo na API** lendo `PETCONNECT_MODULOS` e este catálogo: filtro único antes do `SecurityConfig`/`@PreAuthorize`, validação de boot (ids, fecho de dependências, bloqueios de remoção), `GET /api/v1/modulos` para o frontend esconder menu e seções, e testes de que **nenhuma rota de módulo desligado responde**, com token de qualquer papel.

---

## Apêndice A — Endpoints por dono (gerado do `RequestMappingHandlerMapping`)

"Quem pode" é a **regra efetiva**: a regra de URL do `SecurityConfig` **e** o `@PreAuthorize` do método. `qualquer usuário logado` = sem restrição de papel.

#### `núcleo` (21 rotas)

| Método | Rota | Quem pode |
|---|---|---|
| GET | `/api/v1/auditoria` | ADMIN |
| POST | `/api/v1/auth/esqueci-senha` | pública |
| POST | `/api/v1/auth/login` | pública |
| GET | `/api/v1/auth/me` | qualquer usuário logado |
| POST | `/api/v1/auth/redefinir-senha` | pública |
| GET | `/api/v1/estoque/configuracao` | qualquer usuário logado |
| GET | `/api/v1/funcionarios` | qualquer usuário logado |
| POST | `/api/v1/funcionarios` | ADMIN |
| GET | `/api/v1/funcionarios/{id}` | ADMIN |
| PUT | `/api/v1/funcionarios/{id}` | ADMIN |
| GET | `/api/v1/me` | qualquer usuário logado |
| PUT | `/api/v1/me` | qualquer usuário logado |
| PUT | `/api/v1/me/funcionario` | qualquer usuário logado |
| POST | `/api/v1/me/trocar-senha` | qualquer usuário logado |
| GET | `/api/v1/usuarios` | ADMIN |
| POST | `/api/v1/usuarios` | ADMIN |
| GET | `/api/v1/usuarios/{id}` | ADMIN |
| PUT | `/api/v1/usuarios/{id}` | ADMIN |
| PATCH | `/api/v1/usuarios/{id}/ativar` | ADMIN |
| PATCH | `/api/v1/usuarios/{id}/desativar` | ADMIN |
| PATCH | `/api/v1/usuarios/{id}/papel` | ADMIN |

#### `clientes` (7 rotas)

| Método | Rota | Quem pode |
|---|---|---|
| GET | `/api/v1/clientes` | qualquer usuário logado |
| POST | `/api/v1/clientes` | ADMIN, VENDAS |
| DELETE | `/api/v1/clientes/{id}` | ADMIN, VENDAS |
| GET | `/api/v1/clientes/{id}` | qualquer usuário logado |
| PUT | `/api/v1/clientes/{id}` | ADMIN, VENDAS |
| POST | `/api/v1/clientes/{id}/anonimizar` | ADMIN |
| GET | `/api/v1/clientes/{id}/dados-pessoais` | ADMIN |

#### `produtos` (8 rotas)

| Método | Rota | Quem pode |
|---|---|---|
| GET | `/api/v1/categorias-produto` | qualquer usuário logado |
| GET | `/api/v1/produtos` | qualquer usuário logado |
| POST | `/api/v1/produtos` | ADMIN, VENDAS |
| GET | `/api/v1/produtos/busca-rapida` | qualquer usuário logado |
| GET | `/api/v1/produtos/{id}` | qualquer usuário logado |
| PUT | `/api/v1/produtos/{id}` | ADMIN, VENDAS |
| PATCH | `/api/v1/produtos/{id}/ativar` | ADMIN |
| PATCH | `/api/v1/produtos/{id}/desativar` | ADMIN, VENDAS |

#### `estoque` (4 rotas)

| Método | Rota | Quem pode |
|---|---|---|
| GET | `/api/v1/estoque/reconciliacao` | ADMIN |
| GET | `/api/v1/produtos/resumo-estoque` | qualquer usuário logado |
| POST | `/api/v1/produtos/{id}/ajustes-estoque` | ADMIN |
| GET | `/api/v1/produtos/{id}/movimentacoes` | qualquer usuário logado |

#### `lotes` (10 rotas)

| Método | Rota | Quem pode |
|---|---|---|
| GET | `/api/v1/estoque/alertas` | ADMIN, VENDAS |
| GET | `/api/v1/lotes` | ADMIN, VENDAS |
| GET | `/api/v1/lotes/{id}/rastreio` | ADMIN, VENDAS |
| POST | `/api/v1/produtos/{id}/controle-lote` | ADMIN |
| POST | `/api/v1/produtos/{id}/controle-lote/desativar` | ADMIN |
| GET | `/api/v1/produtos/{id}/lotes` | ADMIN, VENDAS |
| POST | `/api/v1/produtos/{id}/lotes/{loteId}/bloquear` | ADMIN |
| POST | `/api/v1/produtos/{id}/lotes/{loteId}/desbloquear` | ADMIN |
| POST | `/api/v1/produtos/{id}/lotes/{loteId}/perdas` | ADMIN |
| POST | `/api/v1/produtos/{id}/lotes/{loteId}/vencimentos` | ADMIN |

#### `caixa` (9 rotas)

| Método | Rota | Quem pode |
|---|---|---|
| GET | `/api/v1/caixas` | ADMIN |
| POST | `/api/v1/caixas/abrir` | qualquer usuário logado |
| GET | `/api/v1/caixas/atual` | qualquer usuário logado |
| POST | `/api/v1/caixas/atual/fechar` | qualquer usuário logado |
| GET | `/api/v1/caixas/atual/previa-fechamento` | qualquer usuário logado |
| POST | `/api/v1/caixas/atual/sangrias` | qualquer usuário logado |
| POST | `/api/v1/caixas/atual/suprimentos` | qualquer usuário logado |
| GET | `/api/v1/caixas/resumo-atual` | qualquer usuário logado |
| GET | `/api/v1/caixas/{id}` | qualquer usuário logado |

#### `pdv` (6 rotas)

| Método | Rota | Quem pode |
|---|---|---|
| GET | `/api/v1/vendas` | ADMIN, VENDAS |
| POST | `/api/v1/vendas` | ADMIN, VENDAS |
| POST | `/api/v1/vendas/previa` | ADMIN, VENDAS |
| GET | `/api/v1/vendas/resumo-dia` | ADMIN, VENDAS |
| GET | `/api/v1/vendas/{id}` | ADMIN, VENDAS |
| POST | `/api/v1/vendas/{id}/cancelar` | ADMIN |

#### `compras` (12 rotas)

| Método | Rota | Quem pode |
|---|---|---|
| GET | `/api/v1/compras` | ADMIN, VENDAS |
| POST | `/api/v1/compras` | ADMIN |
| GET | `/api/v1/compras/{id}` | ADMIN, VENDAS |
| POST | `/api/v1/compras/{id}/cancelar` | ADMIN |
| POST | `/api/v1/compras/{id}/receber` | ADMIN |
| GET | `/api/v1/fornecedores` | ADMIN, VENDAS |
| POST | `/api/v1/fornecedores` | ADMIN |
| GET | `/api/v1/fornecedores/opcoes` | ADMIN, VENDAS |
| GET | `/api/v1/fornecedores/{id}` | ADMIN, VENDAS |
| PUT | `/api/v1/fornecedores/{id}` | ADMIN |
| PATCH | `/api/v1/fornecedores/{id}/ativar` | ADMIN |
| PATCH | `/api/v1/fornecedores/{id}/desativar` | ADMIN |

#### `financeiro` (9 rotas)

| Método | Rota | Quem pode |
|---|---|---|
| GET | `/api/v1/financeiro/contas-a-pagar` | ADMIN |
| GET | `/api/v1/financeiro/contas-a-pagar/{id}` | ADMIN |
| POST | `/api/v1/financeiro/contas-a-pagar/{id}/pagar` | ADMIN |
| GET | `/api/v1/financeiro/fluxo` | ADMIN |
| GET | `/api/v1/financeiro/lancamentos` | ADMIN |
| POST | `/api/v1/financeiro/lancamentos` | ADMIN |
| GET | `/api/v1/financeiro/lancamentos/{id}` | ADMIN |
| POST | `/api/v1/financeiro/lancamentos/{id}/estornar` | ADMIN |
| GET | `/api/v1/financeiro/resumo-dia` | ADMIN |

#### `pets` (9 rotas)

| Método | Rota | Quem pode |
|---|---|---|
| GET | `/api/v1/especies` | qualquer usuário logado |
| GET | `/api/v1/especies/{especieId}/racas` | qualquer usuário logado |
| GET | `/api/v1/pets` | qualquer usuário logado |
| POST | `/api/v1/pets` | ADMIN, VENDAS |
| DELETE | `/api/v1/pets/{id}` | ADMIN, VENDAS |
| GET | `/api/v1/pets/{id}` | qualquer usuário logado |
| PUT | `/api/v1/pets/{id}` | ADMIN, VENDAS |
| GET | `/api/v1/pets/{id}/foto` | qualquer usuário logado |
| PUT | `/api/v1/pets/{id}/foto` | ADMIN, VENDAS |

#### `clinica` (6 rotas)

| Método | Rota | Quem pode |
|---|---|---|
| GET | `/api/v1/especies/{especieId}/vacinas` | qualquer usuário logado |
| GET | `/api/v1/pets/{petId}/consultas` | qualquer usuário logado |
| POST | `/api/v1/pets/{petId}/consultas` | qualquer usuário logado |
| GET | `/api/v1/pets/{petId}/vacinas` | qualquer usuário logado |
| POST | `/api/v1/pets/{petId}/vacinas` | ADMIN, VENDAS |
| DELETE | `/api/v1/pets/{petId}/vacinas/{lancamentoId}` | ADMIN, VENDAS |

#### `agenda` (12 rotas)

| Método | Rota | Quem pode |
|---|---|---|
| GET | `/api/v1/agendamentos` | qualquer usuário logado |
| POST | `/api/v1/agendamentos` | ADMIN, VENDAS |
| GET | `/api/v1/agendamentos/preco` | qualquer usuário logado |
| GET | `/api/v1/agendamentos/{id}` | qualquer usuário logado |
| PUT | `/api/v1/agendamentos/{id}` | ADMIN, VENDAS |
| POST | `/api/v1/agendamentos/{id}/status` | qualquer usuário logado |
| GET | `/api/v1/pets/{petId}/agendamentos/proximos` | qualquer usuário logado |
| GET | `/api/v1/servicos` | qualquer usuário logado |
| POST | `/api/v1/servicos` | ADMIN |
| DELETE | `/api/v1/servicos/{id}` | ADMIN |
| GET | `/api/v1/servicos/{id}` | qualquer usuário logado |
| PUT | `/api/v1/servicos/{id}` | ADMIN |

#### `relatorios` (10 rotas)

| Método | Rota | Quem pode |
|---|---|---|
| GET | `/api/v1/relatorios/caixas` | ADMIN |
| GET | `/api/v1/relatorios/contas` | ADMIN |
| GET | `/api/v1/relatorios/curva-abc` | ADMIN |
| GET | `/api/v1/relatorios/despesas` | ADMIN |
| GET | `/api/v1/relatorios/estoque` | ADMIN |
| GET | `/api/v1/relatorios/margem` | ADMIN |
| GET | `/api/v1/relatorios/produtos-mais-vendidos` | ADMIN |
| GET | `/api/v1/relatorios/servicos` | ADMIN |
| GET | `/api/v1/relatorios/vendas` | ADMIN |
| GET | `/api/v1/relatorios/{tipo}/csv` | ADMIN |

#### `vacina-estoque` (4 rotas)

| Método | Rota | Quem pode |
|---|---|---|
| GET | `/api/v1/produtos/{id}/vacinas` | ADMIN, VENDAS |
| GET | `/api/v1/vacinas/estoque` | ADMIN |
| DELETE | `/api/v1/vacinas/{id}/produto` | ADMIN |
| PUT | `/api/v1/vacinas/{id}/produto` | ADMIN |
