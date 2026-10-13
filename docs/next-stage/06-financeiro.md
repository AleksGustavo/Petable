# 06 — Financeiro

## 1. Objetivo

Implementar o módulo financeiro do PetConnect Web como fonte única, imutável e auditável de entradas, saídas, contas a pagar e operação diária de caixa, integrado aos módulos de vendas, compras e serviços.

A etapa deve entregar:

- lançamento automático de venda concluída como `ENTRADA / VENDA_PRODUTO`;
- estorno financeiro automático no cancelamento da venda;
- criação de conta a pagar no recebimento de compra;
- pagamento integral da conta gerando `SAIDA / COMPRA_MERCADORIA`;
- receita de serviço no momento da conclusão do agendamento;
- lançamentos manuais controlados;
- estorno em vez de edição/exclusão;
- abertura, suprimento, sangria e fechamento de caixa;
- fluxo financeiro por período;
- resumo financeiro no dashboard;
- backfill idempotente dos registros anteriores à V8;
- autorização, auditoria, idempotência e concorrência.

## 2. Análise obrigatória

Antes de implementar:

1. Ler V1–V7 e confirmar os nomes físicos das constraints.
2. Inspecionar `venda`, `compra`, `servico_realizado` e `movimentacao_financeira`.
3. Inspecionar `VendaService.registrar` e `VendaService.cancelar`.
4. Inspecionar o componente de idempotência de vendas.
5. Inspecionar `CompraService.receber`.
6. Inspecionar `AgendamentoService` na conclusão.
7. Inspecionar `AuditoriaService`.
8. Inspecionar `SecurityConfig`, `UsuarioAtual` e tratamento ProblemDetail.
9. Inspecionar `AppShell`, dashboard, PDV e route group `(impressao)`.
10. Rodar a suíte existente antes de qualquer alteração e registrar o baseline.

Se um nome físico de constraint divergir, adaptar somente o identificador técnico na V8 e documentar no relatório final.

## 3. Decisões arquiteturais justificadas

### DA-01 — Serviço financeiro central
Criar `FinanceiroService`. Ele é o único componente autorizado a persistir `movimentacao_financeira`. Venda, compra, serviço, caixa e lançamentos manuais delegam a ele.

### DA-02 — Regime de caixa
Adotar regime de caixa operacional:
- venda concluída: receita na hora;
- serviço concluído: receita na hora;
- compra recebida: cria conta a pagar;
- pagamento da conta: reconhece saída;
- estorno: reconhecido na data do estorno.

Competência contábil, taxas de cartão e conciliação ficam fora.

### DA-03 — Venda exige caixa aberto
Após V8, ADMIN e VENDAS só finalizam venda se o usuário atual possuir caixa `ABERTO`. Sem caixa: 409, sem venda, sem estoque e sem financeiro.

### DA-04 — Um caixa aberto por usuário
Garantir no banco um único caixa `ABERTO` por usuário através de coluna gerada + UNIQUE.

### DA-05 — Atomicidade da venda
Venda, baixa de estoque e lançamento financeiro pertencem à mesma transação. Falha em qualquer etapa desfaz tudo.

### DA-06 — Unicidade por origem
Todo lançamento novo recebe `chave_origem` UNIQUE:
- `VENDA:{id}`
- `VENDA_ESTORNO:{id}`
- `SERVICO:{id}`
- `CONTA_PAGAR:{id}`
- `MANUAL:{uuid}`
- `MANUAL_ESTORNO:{id}`
- `CAIXA_SUPRIMENTO:{id}`
- `CAIXA_SANGRIA:{id}`

### DA-07 — Cancelamento por lançamento inverso
Cancelar venda nunca altera/apaga a entrada original. Cria `SAIDA / VENDA_PRODUTO`, mesmo valor e forma, `estorno_de_id` apontando para a entrada.

Se o caixa original estiver fechado, o estorno entra no caixa atualmente aberto do ADMIN. Sem caixa aberto, cancelamento retorna 409 e não altera venda/estoque.

### DA-08 — Serviço vira receita na conclusão
Conclusão de agendamento passa a exigir `formaPagamento` e caixa aberto do usuário. Na mesma transação que cria `servico_realizado`, criar `ENTRADA / SERVICO`.

### DA-09 — Compra recebida cria conta a pagar
`CompraService.receber` cria exatamente uma `conta_pagar`, mas não gera saída financeira.

### DA-10 — Vencimento estruturado
Recebimento da compra passa a exigir `dataVencimento`, entre `dataEntrega` e `dataEntrega + 365 dias`. Não interpretar texto de `compra.forma_pagamento`.

### DA-11 — Pagamento integral
Não haverá pagamento parcial nesta etapa. Conta `ABERTA` é paga integralmente uma única vez, sob lock pessimista, gerando uma saída.

### DA-12 — Lançamentos manuais
Somente ADMIN. Categorias permitidas: `DESPESA_FUNCIONARIO`, `DESPESA_OPERACIONAL`, `OUTROS`. Exigem caixa aberto e `Idempotency-Key`.

### DA-13 — Imutabilidade
Lançamentos nunca são editados ou apagados. Somente lançamento manual pode ser estornado diretamente. Automáticos são corrigidos pelo módulo de origem.

### DA-14 — Caixa esperado
Dinheiro físico esperado:
`fundo + entradas DINHEIRO - saídas DINHEIRO + suprimentos - sangrias`.

Outras formas são apuradas separadamente.

### DA-15 — Sangria e suprimento
Geram `caixa_movimento` e lançamento financeiro `OUTROS`. Sangria não pode exceder o saldo esperado em dinheiro.

### DA-16 — Fechamento
Fechamento trava o caixa com `PESSIMISTIC_WRITE`, calcula esperado/contado/diferença por forma e é definitivo. Não existe reabertura.

### DA-17 — Formas
`DINHEIRO`, `CARTAO_CREDITO`, `CARTAO_DEBITO`, `PIX`, `TRANSFERENCIA`.

### DA-18 — Backfill
Não executar INSERT histórico automaticamente na migration. Criar `FinanceiroBackfillRunner`, habilitado apenas por `--petconnect.financeiro.backfill=true`, idempotente por `chave_origem`.

Vendas históricas usam a forma existente. Compras recebidas viram contas abertas com vencimento igual a `data_entrega`. Serviços históricos sem forma de pagamento não são inventados: ficam pendentes, salvo configuração explícita `petconnect.financeiro.backfill-servico-forma`.

### DA-19 — Auditoria
Lançamentos automáticos possuem auditoria financeira própria além da auditoria do evento de origem. Replay não audita novamente.

### DA-20 — Datas e dinheiro
Datas automáticas vêm do `Clock` em America/Sao_Paulo. Dinheiro usa `BigDecimal`, escala 2, `HALF_UP`. Nunca `double`/`float`.

## 4. Escopo

Backend: V8, módulo financeiro, caixa, conta a pagar, lançamentos automáticos/manuais, estorno, fluxo, dashboard, backfill e integrações.

Frontend: `/financeiro`, lançamentos, contas a pagar, `/caixa`, comprovante, integração com PDV/agendamento, AppShell e dashboard.

## 5. Fora de escopo

- pagamento parcial;
- parcelamento;
- contas a receber;
- múltiplas formas na mesma venda;
- taxas/adquirentes;
- conciliação bancária;
- Open Finance;
- emissão fiscal;
- DRE contábil;
- centro de custo;
- folha;
- reabertura de caixa;
- relatórios avançados da etapa 07;
- migração do legado da etapa 08.

## 6. Banco — migration V8

Criar `V8__financeiro_caixa_contas.sql`.

```sql
CREATE TABLE caixa (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ABERTO',
    aberto_em DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    fundo_abertura DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    fechado_em DATETIME(6) NULL,
    esperado_dinheiro DECIMAL(10,2) NULL,
    contado_dinheiro DECIMAL(10,2) NULL,
    diferenca_dinheiro DECIMAL(10,2) NULL,
    esperado_cartao_credito DECIMAL(10,2) NULL,
    contado_cartao_credito DECIMAL(10,2) NULL,
    diferenca_cartao_credito DECIMAL(10,2) NULL,
    esperado_cartao_debito DECIMAL(10,2) NULL,
    contado_cartao_debito DECIMAL(10,2) NULL,
    diferenca_cartao_debito DECIMAL(10,2) NULL,
    esperado_pix DECIMAL(10,2) NULL,
    contado_pix DECIMAL(10,2) NULL,
    diferenca_pix DECIMAL(10,2) NULL,
    esperado_transferencia DECIMAL(10,2) NULL,
    contado_transferencia DECIMAL(10,2) NULL,
    diferenca_transferencia DECIMAL(10,2) NULL,
    diferenca_total DECIMAL(10,2) NULL,
    motivo_diferenca VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    usuario_aberto_id BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN status = 'ABERTO' THEN usuario_id ELSE NULL END
        ) STORED,
    CONSTRAINT fk_caixa_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT ck_caixa_status CHECK (status IN ('ABERTO','FECHADO')),
    CONSTRAINT ck_caixa_fundo CHECK (fundo_abertura >= 0),
    CONSTRAINT ck_caixa_fechamento CHECK (
        (status='ABERTO' AND fechado_em IS NULL)
        OR
        (status='FECHADO' AND fechado_em IS NOT NULL)
    ),
    CONSTRAINT uk_caixa_usuario_aberto UNIQUE (usuario_aberto_id)
);

CREATE INDEX idx_caixa_usuario_abertura ON caixa(usuario_id, aberto_em);
CREATE INDEX idx_caixa_status_abertura ON caixa(status, aberto_em);

CREATE TABLE caixa_movimento (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    caixa_id BIGINT NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    valor DECIMAL(10,2) NOT NULL,
    motivo VARCHAR(500) NOT NULL,
    usuario_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_caixa_mov_caixa FOREIGN KEY (caixa_id) REFERENCES caixa(id),
    CONSTRAINT fk_caixa_mov_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT ck_caixa_mov_tipo CHECK (tipo IN ('SANGRIA','SUPRIMENTO')),
    CONSTRAINT ck_caixa_mov_valor CHECK (valor > 0)
);

CREATE INDEX idx_caixa_mov_caixa_data ON caixa_movimento(caixa_id, created_at);

CREATE TABLE conta_pagar (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    compra_id BIGINT NULL,
    descricao VARCHAR(100) NOT NULL,
    valor_original DECIMAL(10,2) NOT NULL,
    saldo DECIMAL(10,2) NOT NULL,
    data_vencimento DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ABERTA',
    paga_em DATETIME(6) NULL,
    forma_pagamento VARCHAR(20) NULL,
    usuario_pagamento_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_conta_pagar_compra FOREIGN KEY (compra_id) REFERENCES compra(id),
    CONSTRAINT fk_conta_pagar_usuario FOREIGN KEY (usuario_pagamento_id) REFERENCES usuario(id),
    CONSTRAINT uk_conta_pagar_compra UNIQUE (compra_id),
    CONSTRAINT ck_conta_pagar_valor CHECK (valor_original > 0 AND saldo >= 0),
    CONSTRAINT ck_conta_pagar_status CHECK (status IN ('ABERTA','PAGA')),
    CONSTRAINT ck_conta_pagar_forma CHECK (
        forma_pagamento IS NULL OR forma_pagamento IN
        ('DINHEIRO','CARTAO_CREDITO','CARTAO_DEBITO','PIX','TRANSFERENCIA')
    ),
    CONSTRAINT ck_conta_pagar_pagamento CHECK (
        (status='ABERTA' AND paga_em IS NULL AND forma_pagamento IS NULL
         AND usuario_pagamento_id IS NULL AND saldo=valor_original)
        OR
        (status='PAGA' AND paga_em IS NOT NULL AND forma_pagamento IS NOT NULL
         AND usuario_pagamento_id IS NOT NULL AND saldo=0)
    )
);

CREATE INDEX idx_conta_pagar_status_vencimento
    ON conta_pagar(status, data_vencimento);

ALTER TABLE venda
    ADD COLUMN caixa_id BIGINT NULL AFTER usuario_id,
    ADD CONSTRAINT fk_venda_caixa FOREIGN KEY (caixa_id) REFERENCES caixa(id);

CREATE INDEX idx_venda_caixa_data ON venda(caixa_id, data_venda);

ALTER TABLE movimentacao_financeira
    ADD COLUMN forma_pagamento VARCHAR(20) NULL AFTER valor,
    ADD COLUMN usuario_id BIGINT NULL AFTER forma_pagamento,
    ADD COLUMN caixa_id BIGINT NULL AFTER usuario_id,
    ADD COLUMN conta_pagar_id BIGINT NULL AFTER caixa_id,
    ADD COLUMN estorno_de_id BIGINT NULL AFTER conta_pagar_id,
    ADD COLUMN chave_origem VARCHAR(100) NULL AFTER estorno_de_id,
    ADD COLUMN motivo_estorno VARCHAR(500) NULL AFTER chave_origem,
    ADD CONSTRAINT fk_mov_fin_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    ADD CONSTRAINT fk_mov_fin_caixa FOREIGN KEY (caixa_id) REFERENCES caixa(id),
    ADD CONSTRAINT fk_mov_fin_conta FOREIGN KEY (conta_pagar_id) REFERENCES conta_pagar(id),
    ADD CONSTRAINT fk_mov_fin_estorno FOREIGN KEY (estorno_de_id) REFERENCES movimentacao_financeira(id),
    ADD CONSTRAINT ck_mov_fin_forma CHECK (
        forma_pagamento IS NULL OR forma_pagamento IN
        ('DINHEIRO','CARTAO_CREDITO','CARTAO_DEBITO','PIX','TRANSFERENCIA')
    ),
    ADD CONSTRAINT uk_mov_fin_chave UNIQUE (chave_origem),
    ADD CONSTRAINT uk_mov_fin_estorno UNIQUE (estorno_de_id);

CREATE INDEX idx_mov_fin_tipo_data
    ON movimentacao_financeira(tipo, data_movimentacao);
CREATE INDEX idx_mov_fin_categoria_data
    ON movimentacao_financeira(categoria, data_movimentacao);
CREATE INDEX idx_mov_fin_forma_data
    ON movimentacao_financeira(forma_pagamento, data_movimentacao);
CREATE INDEX idx_mov_fin_caixa
    ON movimentacao_financeira(caixa_id, id);
CREATE INDEX idx_mov_fin_conta
    ON movimentacao_financeira(conta_pagar_id);
CREATE INDEX idx_mov_fin_venda_tipo
    ON movimentacao_financeira(venda_id, tipo);
CREATE INDEX idx_mov_fin_compra_tipo
    ON movimentacao_financeira(compra_id, tipo);
CREATE INDEX idx_mov_fin_servico_tipo
    ON movimentacao_financeira(servico_realizado_id, tipo);
```

Colunas adicionadas à tabela histórica permanecem nullable para compatibilidade, mas todo lançamento novo deve preencher `usuario_id`, `caixa_id` e `chave_origem`.

## 7. API — lançamentos financeiros

### GET `/api/v1/financeiro/lancamentos`

ADMIN.

Filtros: `dataInicio`, `dataFim`, `tipo`, `categoria`, `formaPagamento`, `origem`, `page`, `size`.

```json
{
  "content": [{
    "id": 501,
    "tipo": "ENTRADA",
    "categoria": "VENDA_PRODUTO",
    "descricao": "Venda #91",
    "valor": 364.80,
    "dataMovimentacao": "2026-09-26",
    "formaPagamento": "PIX",
    "origem": {"tipo": "VENDA", "id": 91},
    "caixaId": 18,
    "estornoDeId": null
  }],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Erros: 400 filtros inválidos; 401; 403.

### GET `/api/v1/financeiro/lancamentos/{id}`

ADMIN.

```json
{
  "id": 501,
  "tipo": "ENTRADA",
  "categoria": "VENDA_PRODUTO",
  "descricao": "Venda #91",
  "valor": 364.80,
  "formaPagamento": "PIX",
  "dataMovimentacao": "2026-09-26",
  "usuarioId": 5,
  "caixaId": 18,
  "origem": {"tipo": "VENDA", "id": 91},
  "estornoDeId": null,
  "motivoEstorno": null,
  "observacoes": null
}
```

404 inexistente.

### POST `/api/v1/financeiro/lancamentos`

ADMIN. Header `Idempotency-Key` UUID obrigatório.

```json
{
  "tipo": "SAIDA",
  "categoria": "DESPESA_OPERACIONAL",
  "descricao": "Material de limpeza",
  "valor": 89.90,
  "formaPagamento": "DINHEIRO",
  "observacoes": "Higienização da loja"
}
```

201:

```json
{
  "id": 520,
  "tipo": "SAIDA",
  "categoria": "DESPESA_OPERACIONAL",
  "valor": 89.90,
  "formaPagamento": "DINHEIRO",
  "dataMovimentacao": "2026-09-26",
  "caixaId": 18
}
```

Replay: 200 + `Idempotent-Replayed: true`.

400 chave/campo/categoria; 403; 409 sem caixa.

### POST `/api/v1/financeiro/lancamentos/{id}/estornar`

ADMIN.

```json
{"motivo":"Despesa lançada em duplicidade"}
```

200:

```json
{
  "id": 521,
  "tipo": "ENTRADA",
  "categoria": "DESPESA_OPERACIONAL",
  "valor": 89.90,
  "estornoDeId": 520,
  "motivoEstorno": "Despesa lançada em duplicidade"
}
```

400 motivo; 404; 409 automático/já estornado/sem caixa.

## 8. API — contas a pagar

### GET `/api/v1/financeiro/contas-a-pagar`

ADMIN. Filtros `status`, `situacao=TODAS|VENCIDA|VENCE_HOJE|A_VENCER`, datas, page/size.

```json
{
  "content": [{
    "id": 40,
    "compraId": 12,
    "descricao": "Compra #12",
    "valorOriginal": 1250.00,
    "saldo": 1250.00,
    "dataVencimento": "2026-10-10",
    "status": "ABERTA",
    "vencida": false
  }]
}
```

### GET `/api/v1/financeiro/contas-a-pagar/{id}`

ADMIN. 404 se inexistente.

### POST `/api/v1/financeiro/contas-a-pagar/{id}/pagar`

ADMIN.

```json
{"formaPagamento":"PIX"}
```

200:

```json
{
  "id": 40,
  "status": "PAGA",
  "valorOriginal": 1250.00,
  "saldo": 0.00,
  "pagaEm": "2026-09-26T13:40:00",
  "formaPagamento": "PIX",
  "lancamentoId": 530
}
```

400 forma; 404; 409 já paga/sem caixa.

## 9. API — caixa

### POST `/api/v1/caixas/abrir`

ADMIN, VENDAS, SERVICO.

```json
{"fundoAbertura":200.00}
```

201:

```json
{
  "id":18,
  "status":"ABERTO",
  "abertoEm":"2026-09-26T08:02:14",
  "fundoAbertura":200.00
}
```

409 se já aberto.

### GET `/api/v1/caixas/atual`

Retorna o caixa aberto do usuário. 404 se não houver.

### POST `/api/v1/caixas/atual/suprimentos`

```json
{"valor":100.00,"motivo":"Reforço de troco"}
```

201.

### POST `/api/v1/caixas/atual/sangrias`

```json
{"valor":300.00,"motivo":"Retirada preventiva de numerário"}
```

201; 409 se exceder dinheiro esperado.

### GET `/api/v1/caixas/atual/previa-fechamento`

```json
{
  "caixaId":18,
  "esperado":{
    "DINHEIRO":780.00,
    "CARTAO_CREDITO":1200.00,
    "CARTAO_DEBITO":640.00,
    "PIX":980.00,
    "TRANSFERENCIA":0.00
  },
  "totalEsperado":3600.00
}
```

### POST `/api/v1/caixas/atual/fechar`

```json
{
  "contado":{
    "DINHEIRO":775.00,
    "CARTAO_CREDITO":1200.00,
    "CARTAO_DEBITO":640.00,
    "PIX":980.00,
    "TRANSFERENCIA":0.00
  },
  "motivoDiferenca":"Diferença de R$ 5,00 na contagem física"
}
```

200:

```json
{
  "id":18,
  "status":"FECHADO",
  "diferencaTotal":-5.00
}
```

400 valores/motivo; 409 já fechado.

### GET `/api/v1/caixas`

ADMIN. Filtros período, usuário e status.

### GET `/api/v1/caixas/{id}`

ADMIN qualquer caixa; VENDAS/SERVICO somente o próprio.

## 10. API — integrações existentes

### Venda

`POST /api/v1/vendas` mantém contrato da etapa 05, mas exige caixa aberto.

Resposta acrescenta:

```json
{
  "id":91,
  "status":"CONCLUIDA",
  "caixaId":18,
  "lancamentoFinanceiroId":501,
  "valorTotal":364.80
}
```

Sem caixa: 409. Replay: 200 sem nova baixa/lancamento.

### Cancelamento

`POST /api/v1/vendas/{id}/cancelar`: além do estorno de estoque, exige caixa ADMIN aberto e cria estorno financeiro.

### Compra

`POST /api/v1/compras/{id}/receber`:

```json
{
  "dataEntrega":"2026-09-26",
  "dataVencimento":"2026-10-26"
}
```

Resposta inclui conta:

```json
{
  "id":12,
  "status":"RECEBIDO",
  "contaPagar":{
    "id":40,
    "status":"ABERTA",
    "dataVencimento":"2026-10-26",
    "valorOriginal":1250.00
  }
}
```

### Serviço

Na rota já existente de conclusão:

```json
{
  "formaPagamento":"PIX",
  "vacina":null
}
```

Sem forma: 400. Sem caixa: 409. Qualquer falha desfaz conclusão e receita.

## 11. API — fluxo e resumo

### GET `/api/v1/financeiro/fluxo?dataInicio=2026-09-01&dataFim=2026-09-30`

ADMIN.

```json
{
  "dataInicio":"2026-09-01",
  "dataFim":"2026-09-30",
  "entradas":45280.40,
  "saidas":18920.10,
  "saldo":26360.30,
  "porCategoria":[
    {"categoria":"VENDA_PRODUTO","entradas":30100.00,"saidas":420.00,"saldo":29680.00}
  ],
  "porFormaPagamento":[
    {"formaPagamento":"PIX","entradas":17000.00,"saidas":6200.00,"saldo":10800.00}
  ]
}
```

### GET `/api/v1/financeiro/resumo-dia?data=2026-09-26`

ADMIN.

```json
{
  "data":"2026-09-26",
  "entradas":3580.00,
  "saidas":980.00,
  "saldo":2600.00,
  "contasVencidas":2,
  "valorContasVencidas":1430.00,
  "contasAVencer7Dias":4,
  "valorAVencer7Dias":2870.00,
  "caixasAbertos":2
}
```

### GET `/api/v1/caixas/resumo-atual`

ADMIN, VENDAS, SERVICO.

```json
{
  "aberto":true,
  "caixaId":18,
  "abertoEm":"2026-09-26T08:02:14",
  "saldoEsperadoDinheiro":780.00
}
```

Agregações são SQL, não somas de listas em memória.

## 12. Exemplo de cálculo

Caixa abre com R$ 200,00.

- venda em dinheiro: +500;
- serviço em dinheiro: +180;
- suprimento: +100;
- sangria: -200;
- despesa manual em dinheiro: -50.

Esperado:

`200 + 500 + 180 + 100 - 200 - 50 = 730`.

Contado: R$ 725,00.

Diferença:

`725 - 730 = -5`.

Persistir esperado 730, contado 725 e diferença -5.

## 13. Autorização

| Operação | ADMIN | VENDAS | SERVICO |
|---|---:|---:|---:|
| Financeiro geral | ✅ | ❌ | ❌ |
| Fluxo/resumo financeiro | ✅ | ❌ | ❌ |
| Lançamento manual | ✅ | ❌ | ❌ |
| Estorno manual | ✅ | ❌ | ❌ |
| Contas a pagar | ✅ | ❌ | ❌ |
| Pagar conta | ✅ | ❌ | ❌ |
| Abrir próprio caixa | ✅ | ✅ | ✅ |
| Operar próprio caixa | ✅ | ✅ | ✅ |
| Fechar próprio caixa | ✅ | ✅ | ✅ |
| Listar todos os caixas | ✅ | ❌ | ❌ |
| Consultar caixa de outro usuário | ✅ | ❌ | ❌ |
| Registrar venda | ✅ | ✅ | ❌ |
| Cancelar venda | ✅ | ❌ | ❌ |

Configurar URL e `@PreAuthorize`. O 403 deve ocorrer antes da validação de corpo quando a URL for restrita.

## 14. Auditoria

Eventos:

- `LANCAMENTO_CRIADO`
- `LANCAMENTO_ESTORNADO`
- `CONTA_PAGAR_CRIADA`
- `CONTA_PAGA`
- `CAIXA_ABERTO`
- `CAIXA_SANGRIA`
- `CAIXA_SUPRIMENTO`
- `CAIXA_FECHADO`

`CAIXA_FECHADO` inclui esperado, contado e diferença. Não auditar JWT, tokens ou dados bancários. Replay não audita novamente.

## 15. Frontend por rota

### `/financeiro`
ADMIN. Cards de entradas, saídas, saldo, vencidas e próximos 7 dias; filtros; tabela md+ e cards mobile.

### `/financeiro/lancamentos/novo`
ADMIN. Tipo, categoria manual, descrição, valor, forma e observações. UUID preservado em retry.

### `/financeiro/lancamentos/[id]`
Detalhe e, apenas para manual não estornado, ação de estorno com motivo/confirmação.

### `/financeiro/contas-a-pagar`
Filtros e estados `Vencida`, `Vence hoje`, `A vencer`, `Paga`, sempre com texto.

### `/financeiro/contas-a-pagar/[id]`
Detalhe e pagamento integral com confirmação.

### `/caixa`
Sem caixa: abertura. Com caixa: fundo, esperado, sangria, suprimento e fechamento.

### `/caixa/[id]`
Detalhe histórico. VENDAS/SERVICO somente próprio.

### `/caixa/[id]/comprovante`
Route group `(impressao)`, sem AppShell. Exibir operador, abertura, fechamento, esperado/contado/diferença, suprimentos e sangrias. Documento não fiscal.

### `/pdv`
Consultar caixa atual. Sem caixa, bloquear finalização e mostrar `Abra o caixa para realizar vendas.`. Em 409 por caixa fechado em outra aba, preservar carrinho.

### conclusão do agendamento
Adicionar forma de pagamento. Sem caixa, bloquear e oferecer acesso a `/caixa`.

## 16. Dashboard

ADMIN:
- Saldo de hoje;
- Entradas hoje;
- Saídas hoje;
- Contas vencidas;
- A vencer em 7 dias;
- Caixas abertos.

VENDAS/SERVICO:
- `Meu caixa`;
- aberto desde;
- dinheiro esperado;
- ou `Caixa fechado` com CTA.

Não chamar endpoint sem permissão.

## 17. Erros/UI

- 400: campos/regras sintáticas;
- 401: não autenticado;
- 403: sem permissão;
- 404: recurso inexistente;
- 409: conflito de domínio.

Mensagens:
- `Não há caixa aberto para o operador atual. Abra o caixa antes de continuar.`
- `Já existe um caixa aberto para o usuário atual.`
- `Este caixa já está fechado.`
- `Esta conta já foi paga.`
- `Este lançamento já foi estornado.`
- `A sangria não pode exceder o saldo esperado em dinheiro.`

Pending desabilita botões. Confirmar pagamento, estorno, sangria e fechamento.

## 18. Regras numeradas

1. Somente `FinanceiroService` persiste lançamentos.
2. Lançamento é imutável.
3. Lançamento nunca é hard-delete.
4. Correção ocorre por estorno.
5. Venda concluída gera uma entrada.
6. Replay não duplica.
7. Venda exige caixa.
8. Venda, estoque e financeiro são atômicos.
9. Cancelamento gera saída vinculada.
10. Cancelamento exige caixa ADMIN.
11. Caixa fechado não é reaberto.
12. Serviço concluído gera entrada.
13. Serviço exige forma e caixa.
14. Compra recebida cria conta.
15. Recebimento não gera saída.
16. Pagamento é integral.
17. Pagamento gera saída.
18. Conta paga não é paga novamente.
19. Uma compra possui no máximo uma conta.
20. Origem automática é única.
21. Manual exige Idempotency-Key.
22. Endpoint manual não aceita categoria automática.
23. Só ADMIN cria/estorna manual.
24. Um usuário possui no máximo um caixa aberto.
25. Usuário opera o próprio caixa.
26. Sangria não excede dinheiro esperado.
27. Sangria/suprimento exigem motivo.
28. Fechamento usa lock.
29. Diferença = contado - esperado.
30. Diferença não zero exige motivo.
31. Fundo/sangria/suprimento afetam dinheiro físico.
32. Cartão/PIX/transferência são apurados separadamente.
33. Valores são BigDecimal 2 casas.
34. Frontend trabalha em centavos.
35. Datas automáticas vêm do servidor.
36. Backfill é idempotente.
37. Backfill não inventa forma de serviço.
38. Origem automática nunca vem do frontend.
39. Autorização é garantida no backend.
40. Financeiro não altera regra de estoque.
41. SERVICO/VENDAS não acessam financeiro geral.
42. Relatórios avançados ficam para etapa 07.

## 19. Testes unitários

`FinanceiroServiceTest`:
- `deveRegistrarEntradaDeVenda`
- `deveImpedirDuplicidadeDaMesmaVenda`
- `deveRegistrarEstornoDeVenda`
- `deveVincularEstornoAoOriginal`
- `deveRegistrarReceitaDeServico`
- `deveCriarContaPagarDaCompra`
- `deveImpedirDuasContasDaMesmaCompra`
- `devePagarContaIntegralmente`
- `deveRejeitarContaJaPaga`
- `deveCriarLancamentoManual`
- `deveRejeitarCategoriaAutomaticaManual`
- `deveSerIdempotenteNoLancamentoManual`
- `deveEstornarLancamentoManual`
- `deveRejeitarSegundoEstorno`
- `deveUsarBigDecimalDuasCasas`

`CaixaServiceTest`:
- `deveAbrirCaixa`
- `deveRejeitarSegundoCaixaAberto`
- `deveRegistrarSuprimento`
- `deveRegistrarSangria`
- `deveRejeitarSangriaExcedente`
- `deveCalcularEsperadoDinheiro`
- `deveCalcularEsperadoPorForma`
- `deveCalcularDiferenca`
- `deveExigirMotivoParaDiferenca`
- `deveFecharCaixa`
- `deveRejeitarSegundoFechamento`

Ajustar `VendaServiceTest`, `CompraServiceTest` e `AgendamentoServiceTest` para as novas invariantes, incluindo rollback integral.

## 20. Testes de integração

`FinanceiroVendaIT`:
- `vendaConcluidaDeveGerarExatamenteUmLancamento`
- `replayNaoDeveDuplicarLancamento`
- `vendaSemCaixaDeveRetornar409SemBaixarEstoque`
- `cancelamentoDeveGerarEstornoVinculado`
- `cancelamentoSemCaixaDeveFazerRollback`

`ContaPagarControllerIT`:
- `compraRecebidaDeveCriarConta`
- `recebimentoSemVencimentoDeveRetornar400`
- `adminDevePagarConta`
- `pagamentoDeveGerarSaida`
- `segundoPagamentoDeveRetornar409`

`CaixaControllerIT`:
- `adminDeveAbrirCaixa`
- `vendasDeveAbrirProprioCaixa`
- `servicoDeveAbrirProprioCaixa`
- `segundoCaixaDeveRetornar409`
- `deveRegistrarSangria`
- `deveRegistrarSuprimento`
- `sangriaExcedenteDeveRetornar409`
- `deveFecharComDiferencaZero`
- `deveExigirMotivoComDiferenca`
- `caixaFechadoNaoPodeFecharNovamente`

`FinanceiroControllerIT`:
- `adminDeveListarLancamentos`
- `vendasDeveReceber403`
- `servicoDeveReceber403`
- `adminDeveCriarManual`
- `mesmaChaveNaoDeveDuplicarManual`
- `adminDeveEstornarManual`
- `fluxoDeveSomarEntradasSaidasSaldo`

`FinanceiroServicoIT`:
- `servicoConcluidoDeveGerarReceita`
- `servicoSemCaixaDeveRetornar409`
- `servicoSemFormaDeveRetornar400`
- `falhaFinanceiraDeveFazerRollback`

`FinanceiroBackfillIT`:
- `deveCriarHistorico`
- `segundaExecucaoNaoDeveDuplicar`
- `vendaCanceladaDeveGerarOriginalEEstorno`
- `compraRecebidaDeveGerarConta`

`FinanceiroConcorrenciaIT`:
- `doisFechamentosSimultaneosSoUmPassa`
- `doisPagamentosSimultaneosSoUmPassa`
- `vendasSimultaneasNaoPerdemLancamentos`
- `duasAberturasSimultaneasGeramUmCaixa`

## 21. Testes frontend por arquivo

- `financeiro-schema.test.ts`
- `lancamento-manual.test.tsx`
- `lancamento-detalhes.test.tsx`
- `contas-a-pagar.test.tsx`
- `conta-pagar-detalhes.test.tsx`
- `caixa-abertura.test.tsx`
- `caixa-operacao.test.tsx`
- `caixa-fechamento.test.tsx`
- `pdv-caixa.test.tsx`
- `agendamento-conclusao-financeiro.test.tsx`
- `financeiro-dashboard.test.tsx`
- `comprovante-caixa.test.tsx`

Cobrir validação, centavos, loading, 400/403/404/409, confirmação, idempotência, diferenças, permissões e responsividade.

## 22. Segurança e integridade

- serviço financeiro central;
- locks pessimistas em pagamento/fechamento/estorno;
- UNIQUE de origem;
- UNIQUE de conta por compra;
- UNIQUE funcional de caixa aberto;
- transações envolvendo módulos de origem;
- nenhuma edição/exclusão financeira;
- dados automáticos não vêm do browser;
- SecurityConfig + `@PreAuthorize`;
- auditoria sem segredo;
- backfill não público;
- JWT nunca persistido no caixa/sessionStorage;
- frontend nunca é autoridade para total financeiro.

## 23. Critérios de aceite

- [ ] V8 aplica sobre V7.
- [ ] V1–V7 permanecem intactas.
- [ ] Um usuário não abre dois caixas.
- [ ] PDV sem caixa retorna 409 sem efeitos.
- [ ] Venda gera exatamente uma entrada.
- [ ] Replay não duplica.
- [ ] Cancelamento gera estorno financeiro e de estoque atomicamente.
- [ ] Serviço exige forma/caixa e gera receita.
- [ ] Compra recebida cria conta sem saída.
- [ ] Pagamento gera exatamente uma saída.
- [ ] Pagamento concorrente só permite um vencedor.
- [ ] Fechamento concorrente só permite um vencedor.
- [ ] Sangria excedente é recusada.
- [ ] Fechamento calcula esperado/contado/diferença.
- [ ] Diferença exige motivo.
- [ ] Manual é idempotente.
- [ ] Correção manual só por estorno.
- [ ] VENDAS/SERVICO recebem 403 no financeiro geral.
- [ ] Operadores acessam somente próprio caixa.
- [ ] Backfill repetido não duplica.
- [ ] `/financeiro` e `/caixa` funcionam em 375 px sem overflow horizontal.
- [ ] Comprovante imprime sem AppShell.
- [ ] Testes anteriores permanecem verdes após ajustes justificados.
- [ ] backend build PASS.
- [ ] frontend build PASS.
- [ ] lint PASS.
- [ ] typecheck PASS.

## 24. Riscos

- duplicidade: UNIQUE + serviço central;
- PDV existente: agora exige caixa;
- serviço existente: agora exige forma/caixa;
- histórico sem caixa: backfill controlado;
- serviço histórico sem forma: pendência explícita;
- datas/fuso: Clock SP;
- arredondamento: BigDecimal;
- corrida de pagamento/fechamento: lock;
- cancelamento após fechamento: estorno no caixa atual do ADMIN;
- cartão real possui liquidação futura, mas esta etapa reconhece na operação;
- conta sem parcelamento: decisão intencional desta etapa.

## 25. Próximas etapas

**07 — Relatórios gerenciais:** vendas por período, ticket médio, produtos mais vendidos, curva ABC, giro de estoque, receitas por serviço, despesas por categoria, margem estimada, desempenho por operador e relatórios de caixa.

**08 — Migração do legado:** importar e reconciliar dados antigos.

Futuro: conciliação bancária, taxas de cartão, recebíveis e emissão fiscal.

## 26. Ordem de implementação

1. Baseline e inspeção V1–V7.
2. V8.
3. Domínio/repositórios.
4. `FinanceiroService`.
5. `CaixaService`.
6. APIs de caixa.
7. Venda + financeiro.
8. Cancelamento + estorno.
9. Compra + conta.
10. Pagamento.
11. Serviço + receita.
12. Manual + estorno/idempotência.
13. Fluxo/resumo.
14. Backfill.
15. Segurança.
16. Auditoria.
17. Unitários.
18. Integração/concorrência.
19. Frontend financeiro.
20. Contas a pagar.
21. Caixa/comprovante.
22. PDV/agendamento.
23. Dashboard/AppShell.
24. Testes frontend.
25. Backend verify.
26. Lint/typecheck/test/build frontend.
27. Smoke test.
28. Documentação.
29. Relatório final.
30. Revisar `git diff`; não commit/push.

## 27. Restrições

- NÃO editar V1–V7.
- NÃO alterar `legacy/`.
- NÃO hard-delete.
- NÃO editar/apagar lançamento.
- NÃO corrigir lançamento sem estorno.
- NÃO aceitar valor/tipo/categoria/origem automática do frontend.
- NÃO usar double/float.
- NÃO duplicar origem.
- NÃO gravar lançamento fora do `FinanceiroService`.
- NÃO alterar regras de estoque.
- NÃO vender sem caixa após V8.
- NÃO concluir serviço sem forma/caixa.
- NÃO pagar parcialmente.
- NÃO reabrir caixa.
- NÃO reescrever fechamento histórico.
- NÃO expor backfill publicamente.
- NÃO inventar forma de serviço histórico.
- NÃO implementar conciliação/taxas/fiscal.
- NÃO desabilitar testes/lint.
- NÃO usar `any`.
- NÃO commit/push automático.

## 28. Relatório final obrigatório

Criar `06-financeiro-relatorio.md`:

```text
# 06 — Financeiro — Relatório de implementação

## Banco
- V8 aplicada:
- tabelas/colunas/constraints:
- divergências:

## Integrações
- vendas:
- compras:
- serviços:
- caixa:
- backfill:

## Segurança
- SecurityConfig:
- @PreAuthorize:

## Auditoria
- eventos:

## Frontend
- rotas/componentes:
- dashboard:
- impressão:

## Testes
- unitários: X/Y
- integração: X/Y
- frontend: X/Y

## Validações
- backend build: PASS/FAIL
- frontend build: PASS/FAIL
- lint: PASS/FAIL
- typecheck: PASS/FAIL

## Divergências
- somente divergências reais

## Pendências
- somente pendências reais

## Handoff da etapa 07
- migrations aplicadas
- schema financeiro final
- endpoints
- regras de caixa
- categorias
- formas
- agregações existentes
- componentes reutilizáveis
- helpers
- bases de teste
- limitações conhecidas
```

Nunca declarar PASS sem executar o comando correspondente.

## 29. Definição de pronto

A etapa está pronta quando:

```text
ABRIR CAIXA
→ cria uma sessão única ABERTA do usuário

VENDA
→ exige caixa
→ venda + estoque + ENTRADA/VENDA_PRODUTO
→ mesma transação
→ replay sem duplicidade

CANCELAMENTO
→ ADMIN + caixa
→ estoque + SAIDA vinculada
→ mesma transação

SERVIÇO
→ forma + caixa
→ servico_realizado + ENTRADA/SERVICO
→ mesma transação

COMPRA
→ recebimento + conta ABERTA
→ sem saída imediata

PAGAMENTO
→ lock
→ SAIDA/COMPRA_MERCADORIA
→ conta PAGA

MANUAL
→ ADMIN + caixa + UUID
→ imutável
→ correção por estorno

FECHAMENTO
→ lock
→ esperado/contado/diferença por forma
→ FECHADO definitivamente

BACKFILL
→ explícito
→ repetível
→ idempotente
→ sem inventar dados ausentes

ETAPA 07
→ consome fatos financeiros consolidados
→ não reconstrói regras transacionais
```

Só considerar concluída quando integridade, concorrência, idempotência, autorização e backfill estiverem cobertos por testes e backend/frontend estiverem com testes, lint, typecheck e builds aprovados.
