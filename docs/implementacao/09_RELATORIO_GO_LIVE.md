# Etapa 09 — Relatório de homologação final e go-live

Data: 2026-10-15 · Base: `main` com as etapas 01 a 08 · Ambiente: Windows 11 + Docker Desktop (Testcontainers MySQL 8.4 / MongoDB 7 para os ITs; duas instalações reais do `compose.prod.yaml` para a homologação). Evidências brutas: [09_EVIDENCIAS_HOMOLOGACAO.txt](09_EVIDENCIAS_HOMOLOGACAO.txt).

**Veredito:** os cenários críticos **de software** passaram (26 ITs de pacote, 44 verificações com duas instalações reais, 17 de resiliência). **Não é um "pronto para vender tudo"**: Agropecuária **não está pronta** (sem granel/decimal) e Clínica é **parcial**; e o go-live em produção depende de itens que dependem de pessoas e de infraestrutura real, listados em "Pendências". Recomendação: **canário com 1 cliente piloto nos pacotes PDV ou Pet Shop**. Merge deste PR é autorizado pela regra permanente (CI verde); **tag/release e deploy exigem autorização explícita do dono**, que **não** foi dada.

Legenda: **PASS** executado e passou · **FAIL** executado e falhou · **NA** não aplicável · **NÃO EXECUTADO** não foi possível aqui.

## 1. Matriz de pacotes (catálogo da etapa 06)

| Perfil | Situação comercial | Teste | Resultado | Evidência |
|---|---|---|---|---|
| **PDV enxuto** (produtos, estoque, caixa, pdv, relatórios) | pronto | módulos = pacote do catálogo | PASS | `PacotePdvIT` (6 testes) |
| | | venda, caixa, troco, lançamento, **estorno** recompõe saldo e financeiro | PASS | idem |
| | | **FEFO sem tela de lotes** e vencido recusado | PASS | idem + `ModulosSemLotesIT` |
| | | gestão avançada (compras, financeiro, clientes, pets, agenda, lotes, relatórios de contas/despesas/serviços) negada p/ todos os papéis | PASS | idem |
| | | papel insuficiente com módulo contratado (cancelar/ajustar) → 403 do RBAC | PASS | idem |
| **Pet shop** (11 módulos) | pronto | venda + serviço (banho) + entrada (compra recebida, conta a pagar) no mesmo dia; reconciliação consistente | PASS | `PacotePetShopIT` (5) |
| | | permissões (VENDAS não recebe compra; SERVICO não vê vendas/compras) | PASS | idem |
| | | parte clínica (carteira/consultas/vacinas de estoque) fora da oferta → 403 de módulo | PASS | idem |
| | | lotes de ração/medicamento com FEFO e alertas | PASS | idem |
| **Agropecuária** (9 módulos) | **NÃO PRONTA** (granel/decimal D24, venda a prazo, NF) | recebimento com lote, validade, FEFO, alertas, devolução (RETORNAR e DESCARTAR) | PASS (técnico) | `PacoteAgropecuariaIT` (5) |
| | | catálogo mantém `pronto: false` | PASS | idem |
| | | venda a granel, quantidade decimal, fiado, NF-e | **NA — não existe** | [oferta](../operacao/09_OFERTA_COMERCIAL.md) |
| **Clínica** (7 módulos) | **PARCIAL** | atendimento de vacinação, dose do lote que vence primeiro, carteira com o lote, **rastreio** do lote até o animal | PASS | `PacoteClinicaIT` (4), pacote + produtos/estoque/lotes |
| | | sem dose vendável o atendimento não conclui e nada fica gravado | PASS | idem |
| | | pacote exato do catálogo (sem estoque): vacina registrada sem baixa; integração `vacina-estoque` ausente | PASS | `PacoteClinicaSemEstoqueIT` (2) |
| | | prontuário completo, prescrição, exames, internação | **NA — não existe** | catálogo, ausências |
| **Completo** (12 módulos) | pronto (clínica parcial) | fluxo ponta a ponta: compra com lote → venda FEFO → vacinação consumindo lote → cancelamento → financeiro → **9 relatórios + CSV**; reconciliação consistente | PASS | `PacoteCompletoIT` (4) |

Todos os ITs de pacote conferem que os módulos ativos **são exatamente** os do pacote no `catalogo.json` (a oferta comercial e o teste não divergem em silêncio).

## 2. Testes negativos pedidos

| # | Cenário | Resultado | Evidência |
|---|---|---|---|
| 1 | URL/API de módulo não contratado → negar de forma consistente | PASS | 403 uniforme `MODULO_INDISPONIVEL` em todos os pacotes; `ModulosSemEstoqueIT`, `ModulosSemLotesIT`, `ModulosSemFinanceiroIT`, `Pacote*IT`; matriz de 136 rotas ([07_MATRIZ_ACESSO.md](07_MATRIZ_ACESSO.md)); variações de caminho (`%66inanceiro`, barra final, HEAD) |
| 2 | Alterar navegador/token → não ganhar direitos | PASS | cabeçalhos/parâmetro/cookie/claim `modulos` no token ignorados (`ModulosSemEstoqueIT`); token de A em B → 401 |
| 3 | Papel insuficiente mesmo com licença → negar | PASS | `PacotePdvIT`, `PacotePetShopIT`, `ModulosSemLotesIT` (403 do RBAC, sem código de módulo) |
| 4 | Dados e backups do cliente A invisíveis a B, **inclusive uploads** | PASS | `homologacao.sh`: volumes/redes distintos, A não alcança MySQL/Mongo de B, produto de A não existe em B, **foto de A: 200 em A, 404 em B**, volume de B sem arquivo; backup de A **recusado** ao restaurar em B |
| 5 | Ativar módulo → libera após atualização segura, **sem migration** | PASS (API) | A passou a ter `compras` (backup → valida → reinício → smoke), `/compras` 403→200, sem migration nova; **UI: NÃO EXECUTADO em navegador** (cobertura: `capacidades.test.ts`, build, guardas de segmento) |
| 6 | Desativar com obrigações abertas → preservar e negar downgrade inseguro | PASS | downgrade removendo `caixa`/`pdv` com caixa aberto **recusado pela API** ("não é possível remover 'caixa'…") e revertido sozinho; dados e histórico preservados; `ModulosRegistroIT` cobre lotes, caixa, PDV, compras, financeiro, agenda e clínica |
| 7 | PDV com gestão de lote oculta → FEFO e bloqueio de vencido | PASS | `PacotePdvIT`, `ModulosSemLotesIT` |
| 8 | Repetir operações e simular indisponibilidade → sem duplicação | PASS | `PacoteCompletoIT` (mesma chave → mesma venda; cancelar 2× → 409; 1 estorno) e `homologacao-resiliencia.sh` com **API parada e religada**: mesma venda (HTTP 200), 1 venda, saldo baixado 1×, 1 lançamento, 1 estorno |

## 3. Go-live: checklist (o que foi verificado aqui)

| Item | Resultado | Observação |
|---|---|---|
| CI verde | ver PR desta etapa | regra permanente: merge só com os checks verdes |
| Backup/restore | PASS | backup cifrado, `restore-test` (MySQL descartável, contagens), restore real; foto preservada; restore entre clientes recusado |
| Migrations | PASS | V1–V13 aplicam do zero em duas instalações; `MigracaoLotesV11IT` cobre aditividade |
| Monitoração | NÃO EXECUTADO | `monitorar.sh` existe e roda por instalação; alerta a webhook real não foi exercitado |
| Log de auditoria | PASS | `MODULOS_ALTERADOS`, histórico de módulos (2 linhas após upgrade+tentativa), auditoria de venda/ajuste nos ITs |
| Testes de segurança | PASS (escopo da aplicação) | negativos acima, CodeQL/Trivy no CI; **teste de intrusão externo: NÃO EXECUTADO** |
| Política LGPD | **PENDENTE (decisão jurídica)** | controlador/operador, retenção e encarregado: ver [checklist](../operacao/08_CHECKLIST_SEGURANCA_PRIVACIDADE.md) |
| Suporte e treinamento | PASS (material) | [guia](../operacao/09_GUIA_SUPORTE_E_TREINAMENTO.md); sessões de treinamento: NÃO EXECUTADO |
| Plano de canário e rollback | PASS (documentado) | [checklist e canário](../operacao/09_CHECKLIST_GO_LIVE_E_CANARIO.md); **não executado** com cliente real |
| Documentação comercial | PASS | [oferta](../operacao/09_OFERTA_COMERCIAL.md) com módulos, limites, dependências, inclusão/retirada e o que não está contratado |
| Servidor remoto, DNS, TLS público, GHCR | **NÃO EXECUTADO** | sem acesso; só certificado interno do Caddy em `*.localhost` |

## 4. Defeitos e riscos

**Defeitos encontrados nesta etapa:** apenas nos meus próprios testes/scripts (parsing de id, chave de idempotência que precisa ser UUID, período máximo de 7 dias da agenda, estado sobrando entre execuções); todos corrigidos e a evidência final foi refeita do zero. **Nenhum defeito de aplicação foi encontrado pelos cenários da etapa 09.** O defeito de aplicação mais relevante do ciclo foi o `compose.prod.yaml` inválido da etapa 07, achado e corrigido na 08, o que mostra que o CI não valida o compose (ver issue).

**Riscos residuais**
1. **Nunca rodou em produção real:** servidor, GHCR, DNS, e-mail SMTP real e cópia off-site não foram exercitados. É o principal risco do primeiro cliente; o canário existe para isso.
2. **Sem navegador:** a UI foi coberta por 312 testes de unidade/componente, typecheck e build, mas não por um E2E por pacote. Menu/guardas podem ter detalhe visual não visto.
3. **Desempenho com carga real:** o FEFO foi medido em `LoteDesempenhoIT` (volume sintético), não com dados de um cliente.
4. **Modelo de módulos é configuração, não licença:** quem tem acesso ao servidor pode ligar módulos; é decisão consciente (sem licenciamento online neste ciclo).
5. **LGPD contratual** pendente de jurídico.
6. **Um host com vários clientes** exige IP por cliente; limites somados precisam caber no host.

## 5. Pendências (convertidas em issues)

Ver as issues abertas no repositório com o prefixo `[Go-live]`; cada uma cita o item deste relatório. Nenhum recurso não testado foi declarado pronto.

## 6. Como reproduzir

```bash
# ITs por pacote (Docker precisa estar rodando)
cd backend && ./mvnw verify -Dit.test='Pacote*IT' -Dtest=NoSuch -Dsurefire.failIfNoSpecifiedTests=false

# duas instalações reais (usa clientes/ no git ignorado; ver o guia da etapa 08)
scripts/cliente.sh novo homolog-a --dominio a.localhost --modulos clientes,pets,produtos,estoque,caixa,pdv --tls internal --tag local --http-port 18801 --https-port 18441
scripts/cliente.sh novo homolog-b --dominio b.localhost --modulos todos --tls internal --tag local --http-port 18802 --https-port 18442
scripts/cliente.sh subir homolog-a && scripts/cliente.sh subir homolog-b
scripts/cliente.sh smoke-usuario homolog-a && scripts/cliente.sh smoke-usuario homolog-b
scripts/homologacao.sh homolog-a homolog-b && scripts/homologacao-resiliencia.sh homolog-b
```

(As imagens `ghcr.io/aleksgustavo/petconnect-web-{api,web}:local` foram construídas localmente de `backend/` e `frontend/` com `docker build`.)
