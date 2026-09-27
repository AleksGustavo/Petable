# 02 — Agendamentos, Serviços e Execução de Serviços

> **Arquivo:** `02-agendamentos.md`  
> **Projeto:** PetConnect Web  
> **Stack:** Spring Boot 4.1 + Java 21 + Next.js 16 + TypeScript + MySQL 8.4 + MongoDB 7  
> **Arquitetura:** monólito modular, MVC no backend/web e BFF no frontend  
> **Execução:** esta especificação será executada pelo Claude Code diretamente no monorepo.  
> **Regra principal:** antes de alterar qualquer arquivo, inspecione a implementação atual e preserve os padrões consolidados. Não reescreva módulos funcionais, não edite migrations aplicadas e não duplique regras já implementadas no módulo de pets.

---

# 1. Objetivo

Implementar o módulo **Agendamentos** do PetConnect Web, incluindo catálogo administrável de serviços, agenda operacional, cálculo e congelamento do preço estimado, controle de conflitos, máquina de estados, conclusão de serviços e integração da conclusão de vacinação com a carteira do pet.

A implementação deve permitir:

- consultar e administrar serviços;
- administrar preços por porte;
- criar agendamentos;
- listar agenda por dia e semana;
- buscar e filtrar agendamentos;
- editar agendamentos enquanto permitido;
- cancelar;
- confirmar;
- iniciar atendimento;
- concluir atendimento;
- marcar não comparecimento;
- detectar sobreposição de horários;
- calcular preço antes da confirmação;
- congelar `total_estimado` no agendamento;
- registrar `servico_realizado` na conclusão;
- lançar vacina na carteira quando a conclusão representar vacinação;
- exibir próximos agendamentos na ficha do pet;
- liberar o item **Agendamentos** no `AppShell`;
- auditar escritas e transições no MongoDB;
- cobrir backend e frontend com testes.

---

# 2. Arquitetura MVC

O projeto deve continuar como **monólito modular**, utilizando MVC de forma compatível com Spring Boot e com a arquitetura já consolidada.

No backend:

```text
Model
├── domain
│   ├── Agendamento
│   ├── Servico
│   ├── PrecoServicoPorte
│   └── ServicoRealizado
└── infrastructure
    └── repositories JPA

Controller
└── web
    ├── AgendamentoController
    └── ServicoController

Camada de aplicação
└── application
    ├── AgendamentoService
    ├── ServicoService
    └── regras/orquestração transacional
```

No contexto deste projeto, a camada `application` atua entre Controller e Model, evitando regra de negócio no Controller.

O frontend Next.js continua sendo a camada de apresentação/BFF:

```text
View / Presentation
└── frontend/
    ├── Server Components
    ├── Client Components estritamente quando necessários
    ├── Server Actions
    └── Zod
```

Não criar controllers Spring que renderizem HTML. O Spring expõe API REST; o Next.js é responsável pela interface.

---

# 3. Análise obrigatória antes da implementação

Antes de modificar código:

1. Inspecionar `backend/`, `frontend/`, V1–V3, módulo de pets, carteira de vacinação, histórico de consultas, segurança, tratamento de erros, `Clock`, auditoria e testes.
2. Localizar os nomes reais das classes/serviços responsáveis por registrar vacina na carteira.
3. Reutilizar essa regra na conclusão de vacinação. Não copiar a validação para o módulo de agendamentos.
4. Inspecionar `AppShell`, `apiFetch`, Server Actions, `Campo`, `Aviso`, `BotaoConfirmar`, `TutorPicker` e padrões de `?aba=`.
5. Inspecionar os arquivos PHP de agendamento apenas como referência funcional.
6. Executar baseline de testes backend/frontend antes das alterações.
7. Não editar V1, V2 ou V3.
8. Não modificar `legacy/php/`.
9. Ao final executar testes, lint, typecheck e builds disponíveis.

---

# 4. Decisões arquiteturais desta etapa

## 4.1. Um serviço por agendamento

**Decisão:** manter **um único `servico_id` por agendamento** nesta etapa.

Não criar `agendamento_servico` na V4.

Justificativas:

- o schema atual já modela explicitamente um serviço por agendamento;
- simplifica duração, conflito de agenda, preço e máquina de estados;
- evita alterar prematuramente um modelo já aplicado;
- combinações comerciais como “Banho e Tosa” já podem existir como serviços próprios;
- múltiplos serviços por atendimento podem ser introduzidos futuramente com migração própria e regra explícita de duração/preço.

## 4.2. Funcionário opcional

**Decisão:** `funcionario_id` permanece opcional/nulo nesta etapa.

Não implementar CRUD de funcionário.

Justificativas:

- não existe CRUD de usuários/funcionários;
- há apenas ADMIN provisionado por variável de ambiente;
- criar funcionário mínimo agora exigiria resolver criação/vínculo de usuário, credenciais, papéis e ciclo de vida de conta;
- agendamentos sem profissional definido são operacionalmente válidos para esta fase.

A API aceita `funcionarioId: null`.

Se no futuro houver funcionários, a regra de conflito já deve suportar `funcionario_id` quando presente.

## 4.3. Financeiro

**Decisão:** movimentação financeira fica **fora desta etapa**.

`servico_realizado.valor` registra o valor operacional final do serviço, mas não cria:

- venda;
- pagamento;
- contas a receber;
- caixa;
- movimentação financeira.

Esses registros pertencem à futura integração com PDV/financeiro.

---

# 5. Escopo

Entram:

- leitura do catálogo de serviços;
- CRUD de serviços para ADMIN;
- CRUD/substituição dos preços por porte para ADMIN;
- criação de agendamento;
- agenda diária;
- agenda semanal via intervalo máximo de 7 dias;
- filtros por status, pet e tutor;
- edição de agendamento;
- cancelamento;
- confirmação;
- início do atendimento;
- conclusão;
- não comparecimento;
- conflito por pet;
- conflito por funcionário quando `funcionario_id` estiver preenchido;
- preço estimado;
- preço final na conclusão;
- geração de `servico_realizado`;
- integração com carteira quando aplicável;
- próximos agendamentos do pet;
- auditoria;
- testes.

---

# 6. Fora do escopo

Não implementar:

- múltiplos serviços no mesmo agendamento;
- recorrência;
- encaixe automático;
- fila de espera;
- notificações;
- WhatsApp/e-mail;
- calendário externo;
- CRUD de usuários;
- CRUD de funcionários;
- comissão;
- escala de funcionários;
- sala/baia/recurso físico;
- PDV;
- estoque;
- pagamento;
- caixa;
- contas a receber;
- financeiro;
- emissão fiscal;
- migração do legado.

---

# 7. Banco — V4

Criar:

```text
backend/src/main/resources/db/migration/V4__indices_agendamentos.sql
```

Não criar tabela de itens.

Antes de adicionar índices, verificar os já existentes em V1–V3. Criar somente os ausentes.

Índices desejados:

```sql
CREATE INDEX idx_agendamento_pet_data_status
    ON agendamento (pet_id, data_hora, status);

CREATE INDEX idx_agendamento_funcionario_data_status
    ON agendamento (funcionario_id, data_hora, status);

CREATE INDEX idx_agendamento_status_data
    ON agendamento (status, data_hora);

CREATE INDEX idx_agendamento_servico_data
    ON agendamento (servico_id, data_hora);

CREATE INDEX idx_servico_ativo_nome
    ON servico (ativo, nome);

CREATE INDEX idx_servico_realizado_pet_data
    ON servico_realizado (pet_id, data_servico);
```

O índice simples `agendamento(data_hora)` já existe em V1 e não deve ser recriado.

---

# 8. Catálogo de serviços

## 8.1. Leitura

```http
GET /api/v1/servicos
```

Qualquer autenticado.

Query opcional:

```text
?ativo=true
```

Para selects de agendamento, usar somente ativos.

Resposta:

```json
[
  {
    "id": 1,
    "nome": "Banho",
    "descricao": "Banho completo",
    "preco": 55.00,
    "duracaoMinutos": 60,
    "ativo": true,
    "precosPorPorte": [
      {"porte": "PEQUENO", "preco": 45.00},
      {"porte": "MEDIO", "preco": 55.00},
      {"porte": "GRANDE", "preco": 70.00}
    ]
  }
]
```

## 8.2. Detalhar

```http
GET /api/v1/servicos/{id}
```

Qualquer autenticado. Inexistente → 404.

## 8.3. Criar serviço

```http
POST /api/v1/servicos
```

Somente ADMIN.

```json
{
  "nome": "Tosa Higiênica",
  "descricao": "Tosa de áreas específicas",
  "preco": 50.00,
  "duracaoMinutos": 45,
  "precosPorPorte": [
    {"porte": "PEQUENO", "preco": 45.00},
    {"porte": "MEDIO", "preco": 55.00},
    {"porte": "GRANDE", "preco": 65.00}
  ]
}
```

Validar:

- nome obrigatório, trim, 2–100;
- nome único;
- descrição opcional, máximo 1000;
- preço obrigatório, `>= 0`;
- duração obrigatória, entre 5 e 1440 minutos;
- no máximo um preço por porte;
- preço por porte `>= 0`.

Nome duplicado → 409.

Auditar `SERVICO_CRIADO`.

## 8.4. Atualizar serviço

```http
PUT /api/v1/servicos/{id}
```

Somente ADMIN.

O request representa o estado completo editável do serviço, inclusive `precosPorPorte`. Sincronizar preços por porte: atualizar existentes, inserir novos e remover registros omitidos.

Não alterar agendamentos antigos já criados.

Auditar `SERVICO_ATUALIZADO`.

## 8.5. Desativar serviço

```http
DELETE /api/v1/servicos/{id}
```

Somente ADMIN.

Executar `ativo=false`. Não apagar fisicamente.

Agendamentos existentes permanecem válidos.

Auditar `SERVICO_DESATIVADO`.

---

# 9. Cálculo de preço

O preço é calculado no backend.

Ordem:

1. se o pet possui `porte` e existe `preco_servico_porte` para serviço + porte, usar esse preço;
2. caso contrário, usar `servico.preco`;
3. se `servico.preco` estiver ausente/nulo no schema real, impedir agendamento com 409 indicando serviço sem preço configurado.

**Não assumir PEQUENO para pet sem porte.** Isso melhora o legado e evita cobrança silenciosamente incorreta.

Ao criar ou alterar serviço/pet/data que afete o cálculo, o backend recalcula `total_estimado`.

`total_estimado` é um **snapshot congelado** no agendamento. Mudanças posteriores no catálogo não alteram agendamentos existentes.

Na conclusão, `valorFinal`:

- se omitido → usar `total_estimado`;
- se informado → deve ser `>= 0` e será gravado em `servico_realizado.valor`;
- alterar o valor final não altera `total_estimado`.

ADMIN, VENDAS e SERVICO podem informar `valorFinal` somente durante a conclusão, pois todos os três podem executar a transição de conclusão definida nesta especificação.

---

# 10. Duração e intervalo ocupado

Cada agendamento ocupa:

```text
[data_hora, data_hora + servico.duracao_minutos)
```

Usar intervalo semiaberto: um agendamento que termina às 10:00 não conflita com outro que começa às 10:00.

Duração é obtida do serviço atual no momento da criação/edição.

Para preservar coerência histórica sem adicionar coluna nova, a duração usada para detectar conflitos de um agendamento existente deve ser a duração do serviço associado. Alterações administrativas de duração podem afetar verificações futuras; este risco deve ser documentado e fica como candidato a snapshot de duração em etapa posterior.

---

# 11. Conflito de agenda

Status que **ocupam agenda**:

- `AGENDADO`
- `CONFIRMADO`
- `EM_ANDAMENTO`

Status que não bloqueiam novo horário:

- `CONCLUIDO`
- `CANCELADO`
- `NAO_COMPARECEU`

Verificar sobreposição:

```text
inicioExistente < fimNovo
AND
fimExistente > inicioNovo
```

Aplicar por:

1. mesmo `pet_id`, sempre;
2. mesmo `funcionario_id`, somente quando o novo agendamento possuir funcionário.

Na edição, ignorar o próprio ID.

Conflito → `409 Conflict`.

Mensagem por pet:

```text
O pet já possui um agendamento que se sobrepõe a este horário.
```

Mensagem por funcionário:

```text
O funcionário já possui um agendamento que se sobrepõe a este horário.
```

Não resolver automaticamente nem mover horário.

---

# 12. Máquina de estados

Estados:

```text
AGENDADO
CONFIRMADO
EM_ANDAMENTO
CONCLUIDO
CANCELADO
NAO_COMPARECEU
```

Transições permitidas:

```text
AGENDADO -> CONFIRMADO
AGENDADO -> CANCELADO
AGENDADO -> NAO_COMPARECEU

CONFIRMADO -> EM_ANDAMENTO
CONFIRMADO -> CANCELADO
CONFIRMADO -> NAO_COMPARECEU

EM_ANDAMENTO -> CONCLUIDO
EM_ANDAMENTO -> CANCELADO
```

Estados terminais:

```text
CONCLUIDO
CANCELADO
NAO_COMPARECEU
```

Não permitir retorno de estado terminal.

Não permitir:

```text
AGENDADO -> CONCLUIDO
CONFIRMADO -> CONCLUIDO
CANCELADO -> AGENDADO
CONCLUIDO -> qualquer outro
NAO_COMPARECEU -> qualquer outro
```

Transição inválida → 409:

```text
A transição de status AGENDADO para CONCLUIDO não é permitida.
```

---

# 13. Autorização

| Operação | ADMIN | VENDAS | SERVICO |
|---|---:|---:|---:|
| Ver catálogo | Sim | Sim | Sim |
| Criar/editar/desativar serviço | Sim | Não | Não |
| Ver agenda | Sim | Sim | Sim |
| Criar agendamento | Sim | Sim | Não |
| Editar dados do agendamento | Sim | Sim | Não |
| Confirmar | Sim | Sim | Não |
| Iniciar atendimento | Sim | Sim | Sim |
| Concluir atendimento | Sim | Sim | Sim |
| Cancelar | Sim | Sim | Não |
| Marcar não comparecimento | Sim | Sim | Sim |
| Ver próximos agendamentos do pet | Sim | Sim | Sim |

Aplicar `@PreAuthorize` no backend. O frontend apenas reflete permissões; não é barreira de segurança.

---

# 14. API — Agendamentos

Base:

```text
/api/v1/agendamentos
```

## 14.1. Listar por período

```http
GET /api/v1/agendamentos?inicio=2026-09-25&fim=2026-09-25&page=0&size=50
```

Qualquer autenticado.

Filtros opcionais:

```text
status=CONFIRMADO
petId=42
clienteId=10
q=mel
```

`q` busca case-insensitive por nome do pet ou tutor.

Regras:

- `inicio` obrigatório;
- `fim` obrigatório;
- `fim >= inicio`;
- intervalo máximo: 7 dias corridos;
- `page >= 0`;
- `1 <= size <= 100`.

Ordenar `data_hora ASC`.

Resposta `PageResponse`:

```json
{
  "content": [
    {
      "id": 100,
      "dataHora": "2026-09-25T09:00:00",
      "fimPrevisto": "2026-09-25T10:00:00",
      "status": "CONFIRMADO",
      "pet": {"id": 42, "nome": "Mel"},
      "tutor": {"id": 10, "nome": "Maria Silva"},
      "servico": {"id": 1, "nome": "Banho", "duracaoMinutos": 60},
      "funcionario": null,
      "totalEstimado": 55.00,
      "observacoes": null
    }
  ],
  "page": 0,
  "size": 50,
  "totalElements": 1,
  "totalPages": 1
}
```

## 14.2. Detalhar

```http
GET /api/v1/agendamentos/{id}
```

Qualquer autenticado. Inexistente → 404.

## 14.3. Prévia de preço

```http
GET /api/v1/agendamentos/preco?petId=42&servicoId=1
```

Qualquer autenticado.

Resposta:

```json
{
  "petId": 42,
  "servicoId": 1,
  "porte": "MEDIO",
  "origemPreco": "PORTE",
  "totalEstimado": 55.00
}
```

`origemPreco`:

```text
PORTE
PADRAO
```

Esse endpoint é informativo. O POST recalcula o preço; nunca confiar no valor enviado pelo frontend.

## 14.4. Criar

```http
POST /api/v1/agendamentos
```

ADMIN/VENDAS.

```json
{
  "petId": 42,
  "servicoId": 1,
  "funcionarioId": null,
  "dataHora": "2026-09-28T09:00:00",
  "observacoes": "Tutor pediu shampoo hipoalergênico."
}
```

Não aceitar `status` nem `totalEstimado` no request.

Novo agendamento inicia em:

```text
AGENDADO
```

Validações:

- pet ativo;
- serviço ativo;
- funcionário, se informado, deve existir;
- `dataHora` obrigatória e estritamente futura em relação ao `Clock` configurado;
- observações máximo 2000;
- calcular preço no backend;
- verificar conflito.

Sucesso: 201.

Auditar `AGENDAMENTO_CRIADO`.

## 14.5. Editar

```http
PUT /api/v1/agendamentos/{id}
```

ADMIN/VENDAS.

Permitido somente quando status for:

```text
AGENDADO
CONFIRMADO
```

Request:

```json
{
  "petId": 42,
  "servicoId": 3,
  "funcionarioId": null,
  "dataHora": "2026-09-28T10:00:00",
  "observacoes": "Atualizado a pedido do tutor."
}
```

Recalcular `total_estimado` e conflitos.

Não alterar status neste endpoint.

Status não editável → 409.

Auditar `AGENDAMENTO_ATUALIZADO`.

---

# 15. Endpoint de transição de status

Usar endpoint explícito:

```http
POST /api/v1/agendamentos/{id}/status
```

Request básico:

```json
{
  "status": "CONFIRMADO"
}
```

Para `CONCLUIDO`, usar payload adicional conforme seção de conclusão.

O serviço de aplicação deve:

1. carregar agendamento;
2. verificar papel;
3. validar transição;
4. executar efeitos colaterais;
5. persistir;
6. auditar transição.

Não permitir update direto do campo `status` por endpoint genérico.

---

# 16. Confirmar

```json
{"status":"CONFIRMADO"}
```

ADMIN/VENDAS.

Somente `AGENDADO -> CONFIRMADO`.

Auditar:

```text
AGENDAMENTO_STATUS_ALTERADO
```

Detalhes devem conter `statusAnterior` e `statusNovo`.

---

# 17. Iniciar atendimento

```json
{"status":"EM_ANDAMENTO"}
```

ADMIN/VENDAS/SERVICO.

Somente `CONFIRMADO -> EM_ANDAMENTO`.

Auditar transição.

---

# 18. Cancelar

```json
{
  "status": "CANCELADO",
  "motivo": "Tutor solicitou cancelamento."
}
```

ADMIN/VENDAS.

Permitido a partir de `AGENDADO`, `CONFIRMADO` ou `EM_ANDAMENTO`.

`motivo` obrigatório, trim, 3–500 caracteres.

Como V1 não possui coluna específica de motivo, registrar o motivo na auditoria e acrescentar às observações do agendamento de forma identificável, preservando observação anterior:

```text
[CANCELAMENTO] Tutor solicitou cancelamento.
```

Não criar coluna na V4 apenas para esse campo.

Auditar `AGENDAMENTO_CANCELADO` além do registro genérico de transição, ou usar um único evento específico com anterior/novo/motivo. Preferir **um único evento específico** para evitar duplicidade:

```text
AGENDAMENTO_CANCELADO
```

---

# 19. Não comparecimento

```json
{
  "status": "NAO_COMPARECEU",
  "motivo": "Tutor não compareceu e não respondeu ao contato."
}
```

ADMIN/VENDAS/SERVICO.

Permitido de `AGENDADO` ou `CONFIRMADO`.

Só permitir quando `dataHora <= agora`.

Antes do horário → 409:

```text
O agendamento ainda não atingiu o horário previsto e não pode ser marcado como não comparecimento.
```

Motivo opcional, máximo 500. Se informado, registrar em observações com marcador `[NÃO COMPARECIMENTO]` e na auditoria.

Auditar `AGENDAMENTO_NAO_COMPARECEU`.

---

# 20. Conclusão

Somente:

```text
EM_ANDAMENTO -> CONCLUIDO
```

ADMIN/VENDAS/SERVICO.

Request normal:

```json
{
  "status": "CONCLUIDO",
  "valorFinal": 55.00,
  "observacoesConclusao": "Serviço realizado sem intercorrências."
}
```

Criar exatamente um `servico_realizado` ligado ao agendamento.

Campos:

- `agendamento_id` = ID;
- `pet_id` = pet;
- `servico_id` = serviço;
- `funcionario_id` = funcionário do agendamento ou null;
- `data_servico` = instante atual do `Clock`;
- `valor` = `valorFinal` ou `total_estimado`;
- `observacoes` = observações de conclusão.

A constraint UNIQUE de `agendamento_id` garante idempotência no banco; o serviço também deve rejeitar segunda conclusão com 409.

Auditar `AGENDAMENTO_CONCLUIDO` com valor final.

---

# 21. Conclusão de vacinação

A V2 informada não lista um serviço “Vacinação”. Portanto, **não identificar vacinação pelo nome do serviço** e não criar regra frágil baseada em texto.

Nesta etapa, a integração de vacinação será ativada quando existir no catálogo um serviço ativo cujo nome normalizado seja exatamente `Vacinação`, criado por ADMIN através do CRUD de serviços. Até existir esse serviço, não há fluxo de agendamento de vacinação disponível na UI.

Ao selecionar o serviço `Vacinação`, o frontend deve exibir campos adicionais de conclusão. O backend deve validar o nome exato do serviço para determinar que a conclusão requer lançamento na carteira. Esta é uma compatibilidade transitória; uma futura migration poderá introduzir um tipo/categoria de serviço explícito.

Request de conclusão de vacinação:

```json
{
  "status": "CONCLUIDO",
  "valorFinal": 80.00,
  "observacoesConclusao": "Aplicação sem intercorrências.",
  "vacina": {
    "vacinaId": 4,
    "dataAplicacao": "2026-09-25",
    "dataProxima": "2027-09-25",
    "veterinario": "Dra. Ana Souza",
    "lote": "RAI-2026-001",
    "observacoes": "Dose aplicada durante atendimento agendado."
  }
}
```

Para serviço `Vacinação`, objeto `vacina` é obrigatório.

Para outros serviços, `vacina` deve ser ausente; se enviado, retornar 400 no campo `vacina`.

A conclusão deve chamar a **mesma application service/regra pública já usada pelo módulo de pets para registrar a carteira**, incluindo:

- vacina existente/ativa;
- mesma espécie do pet;
- datas válidas;
- status calculado posteriormente na leitura.

Não duplicar essas validações.

A conclusão e o lançamento da carteira devem ocorrer na mesma transação MySQL. Se o lançamento da vacina falhar, não criar `servico_realizado` nem marcar o agendamento como concluído.

A auditoria deve conter os eventos já previstos pelo módulo de carteira e `AGENDAMENTO_CONCLUIDO`, sem duplicar manualmente o evento da carteira se o serviço reutilizado já o registra.

---

# 22. Próximos agendamentos do pet

```http
GET /api/v1/pets/{petId}/agendamentos/proximos?limit=5
```

Qualquer autenticado.

Validar `1 <= limit <= 20`.

Considerar somente:

- `AGENDADO`
- `CONFIRMADO`
- `EM_ANDAMENTO`

com `data_hora >= agora`, exceto `EM_ANDAMENTO`, que deve aparecer mesmo se o horário inicial já passou.

Ordenar por data crescente.

---

# 23. Auditoria

Registrar no Mongo:

- `SERVICO_CRIADO`
- `SERVICO_ATUALIZADO`
- `SERVICO_DESATIVADO`
- `AGENDAMENTO_CRIADO`
- `AGENDAMENTO_ATUALIZADO`
- `AGENDAMENTO_STATUS_ALTERADO` para confirmação/início;
- `AGENDAMENTO_CANCELADO`
- `AGENDAMENTO_NAO_COMPARECEU`
- `AGENDAMENTO_CONCLUIDO`

Cada transição deve registrar:

```json
{
  "statusAnterior": "CONFIRMADO",
  "statusNovo": "EM_ANDAMENTO"
}
```

Quando relevante incluir petId, servicoId, dataHora, motivo e valorFinal.

Não registrar JWT, credenciais ou dados binários.

---

# 24. Regras de negócio numeradas

1. Todo agendamento pertence a um pet ativo.
2. Todo agendamento possui exatamente um serviço ativo na criação.
3. Funcionário é opcional nesta etapa.
4. Data/hora de novo agendamento deve estar no futuro.
5. Novo agendamento inicia em `AGENDADO`.
6. Status só muda pelas transições definidas.
7. Estados terminais não podem ser reabertos.
8. Agendamentos em estados ativos não podem se sobrepor para o mesmo pet.
9. Se houver funcionário, estados ativos não podem se sobrepor para o mesmo funcionário.
10. Intervalos são semiabertos `[início,fim)`.
11. Preço é calculado no backend.
12. Preço por porte tem precedência sobre preço padrão.
13. Pet sem porte usa preço padrão; nunca assumir porte Pequeno.
14. `total_estimado` é congelado no agendamento.
15. Alterações futuras no catálogo não recalculam agendamentos existentes automaticamente.
16. Edição em `AGENDADO`/`CONFIRMADO` recalcula preço e conflito.
17. Agendamento em andamento não pode ter pet/serviço/data editados.
18. Conclusão exige estado `EM_ANDAMENTO`.
19. Conclusão cria exatamente um `servico_realizado`.
20. `valorFinal` omitido usa `total_estimado`.
21. Valor final informado deve ser não negativo.
22. Não comparecimento só pode ser marcado após atingir o horário previsto.
23. Cancelamento exige motivo.
24. Serviço desativado não pode ser usado em novo agendamento.
25. Desativar serviço não invalida agendamentos históricos/existentes.
26. Serviço de vacinação exige payload de vacina na conclusão.
27. Conclusão de vacinação reutiliza a regra do módulo de carteira.
28. Falha no lançamento da vacina desfaz a conclusão inteira.
29. Não existe movimentação financeira nesta etapa.
30. Autorização deve ser aplicada no backend.

---

# 25. Frontend — rotas

Criar:

```text
/agendamentos
/agendamentos/novo
/agendamentos/[id]
/servicos
/servicos/novo
/servicos/[id]
```

`/servicos*` deve ser acessível para leitura por autenticados, mas controles administrativos somente para ADMIN. Se o projeto possuir mecanismo server-side de papel, esconder ações sem remover a proteção backend.

---

# 26. Agenda do dia

`/agendamentos` deve abrir por padrão no dia atual do fuso configurado.

Query:

```text
/agendamentos?data=2026-09-25
```

Cabeçalho:

```text
< Dia anterior | 25/09/2026 | Próximo dia >
[Hoje]
```

Adicionar alternância:

```text
Dia | Semana
```

A visão semanal consulta no máximo 7 dias e agrupa por dia; não implementar grade complexa de calendário nesta etapa.

Desktop `md+`: tabela/lista estruturada por horário.

Mobile: cartões em ordem cronológica.

Card:

```text
09:00–10:00
Mel • Maria Silva
Banho
R$ 55,00
[Confirmado]
```

Filtros:

- status;
- pet/tutor por busca;
- serviço.

---

# 27. Novo agendamento

Acessos:

```text
/agendamentos/novo
/agendamentos/novo?petId=42
```

Também adicionar CTA na ficha `/pets/[id]`:

```text
Agendar serviço
```

Quando não houver `petId`, fluxo:

```text
Tutor -> Pet -> Serviço -> Data/hora -> Preço -> Observações -> Confirmar
```

Reutilizar `TutorPicker`.

Ao selecionar tutor, carregar somente pets ativos daquele tutor.

Ao selecionar pet + serviço, chamar prévia de preço e mostrar:

```text
Valor estimado: R$ 55,00
Duração prevista: 60 minutos
```

O valor exibido é informativo; POST recalcula.

Funcionário não deve ser exibido enquanto não houver registros disponíveis. O frontend envia `funcionarioId: null`. Não mostrar select vazio sem utilidade.

---

# 28. Detalhes do agendamento

`/agendamentos/[id]` deve mostrar:

- status;
- data/hora;
- fim previsto;
- pet;
- tutor;
- serviço;
- duração;
- funcionário, quando existir;
- valor estimado;
- observações;
- ações permitidas conforme status/papel.

Links:

```text
pet -> /pets/{id}
tutor -> /clientes/{id}
```

---

# 29. Ações de status no frontend

Exibir somente ações válidas para estado/papel.

Confirmações obrigatórias para:

- cancelar;
- não compareceu;
- concluir.

Usar `BotaoConfirmar` onde compatível.

Cancelar deve solicitar motivo.

Conclusão deve abrir formulário com:

- valor final pré-preenchido com estimado;
- observações da conclusão;
- campos de vacina somente quando serviço for `Vacinação`.

Erros 409 devem aparecer em `Aviso` com mensagem da API.

---

# 30. Ficha do pet

Em `/pets/[id]`, adicionar seção de **Próximos agendamentos**, sem remover Dados/Vacinas/Consultas.

Mostrar até 5.

Estado vazio:

```text
Nenhum agendamento futuro para este pet.
```

CTA autorizado:

```text
Agendar serviço
```

---

# 31. Menu

No `AppShell`, remover `em breve` de **Agendamentos** e apontar para:

```text
/agendamentos
```

Não adicionar item Serviços ao menu principal se isso poluir a navegação existente. Disponibilizar acesso administrativo ao catálogo a partir da página de Agendamentos, por botão/link `Gerenciar serviços` visível para ADMIN.

---

# 32. Estados de UI

Implementar:

- loading/skeleton;
- agenda vazia;
- busca sem resultados;
- erro de rede;
- 403;
- 404;
- 409 de conflito/transição.

Mensagens:

```text
Nenhum agendamento para esta data.
```

```text
Nenhum agendamento encontrado com os filtros selecionados.
```

```text
Não foi possível carregar os agendamentos. Tente novamente.
```

Não depender apenas de cor nos badges de status.

---

# 33. Testes unitários backend

## AgendamentoServiceTest

```text
deveCriarAgendamentoComPrecoPorPorte
deveUsarPrecoPadraoQuandoPetNaoTemPorte
deveUsarPrecoPadraoQuandoNaoExistePrecoParaOPorte
deveCongelarTotalEstimadoNaCriacao
deveRecalcularPrecoAoEditarAgendamento
deveFalharComPetInativo
deveFalharComServicoInativo
deveFalharComDataHoraNoPassado
deveDetectarConflitoDoMesmoPet
devePermitirAgendamentoExatamenteAposFimDoAnterior
deveDetectarConflitoDoMesmoFuncionario
deveIgnorarConflitoDoProprioAgendamentoNaEdicao
devePermitirEditarAgendado
devePermitirEditarConfirmado
deveFalharAoEditarEmAndamento
deveConfirmarAgendamento
deveIniciarAgendamentoConfirmado
deveFalharTransicaoAgendadoParaConcluido
deveCancelarAgendamentoComMotivo
deveMarcarNaoComparecimentoAposHorario
deveFalharNaoComparecimentoAntesDoHorario
deveConcluirECriarServicoRealizado
deveUsarTotalEstimadoQuandoValorFinalNaoInformado
deveUsarValorFinalInformadoNaConclusao
deveFalharAoConcluirDuasVezes
```

## AgendamentoVacinaServiceTest

```text
deveConcluirVacinacaoELancarCarteira
deveFalharVacinacaoSemPayloadDeVacina
deveFalharQuandoVacinaNaoPertenceAEspecie
deveDesfazerConclusaoQuandoLancamentoDaVacinaFalhar
deveRejeitarPayloadVacinaParaServicoNaoVacinacao
```

## ServicoServiceTest

```text
deveCriarServico
deveFalharComNomeDuplicado
deveAtualizarServicoEPrecosPorPorte
deveRemoverPrecoPorPorteOmitidoNaAtualizacao
deveDesativarServico
```

## TransicaoAgendamentoTest

Cobrir cada transição permitida e pelo menos todas as transições proibidas a partir dos estados terminais.

---

# 34. Testes de integração

Usar `IntegrationTest`/`PetIntegrationTest` e helpers existentes. Criar helpers adicionais apenas se reduzirem duplicação.

## AgendamentoControllerIT

```text
deveCriarAgendamentoComoVendas
servicoNaoDeveCriarAgendamento
deveListarAgendaDoDia
deveListarIntervaloSemanal
deveFiltrarPorStatus
deveFiltrarPorPet
deveFiltrarPorTutor
deveBuscarPorNomeDoPet
deveBuscarPorNomeDoTutor
deveRetornarPrecoPrevisto
deveRetornar409ParaConflitoDoPet
devePermitirHorarioNoFimExatoDoAnterior
deveEditarAgendamento
deveRetornar409AoEditarStatusNaoPermitido
```

## AgendamentoStatusControllerIT

```text
vendasDeveConfirmar
servicoNaoDeveConfirmar
servicoDeveIniciarConfirmado
servicoDeveConcluirEmAndamento
vendasDeveCancelar
servicoNaoDeveCancelar
servicoDeveMarcarNaoComparecimento
deveRetornar409ParaTransicaoInvalida
deveCriarServicoRealizadoAoConcluir
deveImpedirSegundaConclusao
```

## AgendamentoVacinaIT

```text
deveConcluirAgendamentoDeVacinacaoELancarDoseNaCarteira
deveRetornar409ParaVacinaDeOutraEspecie
deveManterAgendamentoEmAndamentoQuandoVacinaFalhar
deveNaoCriarServicoRealizadoQuandoVacinaFalhar
```

## ServicoControllerIT

```text
autenticadoDeveListarServicos
adminDeveCriarServico
vendasNaoDeveCriarServico
adminDeveAtualizarPrecosPorPorte
adminDeveDesativarServico
servicoDesativadoNaoDeveAparecerNoSelectAtivo
```

Verificar auditoria Mongo nas operações principais.

---

# 35. Testes frontend

## agendamento-schema.test.ts

```text
aceitaAgendamentoValido
rejeitaDataHoraInvalida
rejeitaPetAusente
rejeitaServicoAusente
```

## agenda-dia.test.tsx

```text
renderizaEstadoVazio
renderizaAgendamentosEmOrdemCronologica
renderizaCardsNoModeloDeDadosEsperado
renderizaStatusComTexto
```

## novo-agendamento.test.tsx

```text
carregaPetsAposSelecionarTutor
preSelecionaPetRecebidoNaUrl
exibePrecoAposSelecionarPetEServico
exibeDuracaoPrevista
exibeErroDeConflito
```

## acoes-status.test.tsx

```text
exibeConfirmarParaAgendadoQuandoVendas
naoExibeConfirmarParaServico
exibeIniciarParaServicoQuandoConfirmado
solicitaConfirmacaoAoCancelar
solicitaConfirmacaoAoConcluir
```

## conclusao-vacina.test.tsx

```text
exibeCamposDeVacinaParaServicoVacinacao
naoExibeCamposDeVacinaParaOutroServico
exibeErroDeVacinaDaApi
```

## pet-proximos-agendamentos.test.tsx

```text
renderizaProximosAgendamentos
renderizaEstadoVazio
renderizaLinkParaNovoAgendamentoComPet
```

## servico-form.test.tsx

```text
validaNomeObrigatorio
validaDuracao
editaPrecosPorPorte
```

---

# 36. Critérios de aceite

## Banco

- [ ] V1–V3 permanecem intactas.
- [ ] V4 contém somente índices ausentes necessários.
- [ ] Nenhuma tabela de itens de agendamento foi criada.
- [ ] Flyway funciona em banco limpo e upgrade V3→V4.

## Serviços

- [ ] Autenticados listam serviços.
- [ ] ADMIN cria/edita/desativa serviço.
- [ ] VENDAS/SERVICO não alteram catálogo.
- [ ] Preços por porte podem ser administrados.
- [ ] Serviço desativado não entra em novo agendamento.

## Preço

- [ ] Preço por porte tem precedência.
- [ ] Pet sem porte usa preço padrão.
- [ ] Preço é calculado no backend.
- [ ] `total_estimado` fica congelado.
- [ ] Conclusão aceita valor final diferente sem alterar estimado.

## Agenda

- [ ] Novo agendamento inicia AGENDADO.
- [ ] Agenda diária funciona.
- [ ] Visão semanal cobre no máximo 7 dias.
- [ ] Filtros por status/pet/tutor funcionam.
- [ ] Busca por pet/tutor funciona.
- [ ] Edição funciona apenas nos estados permitidos.
- [ ] Conflito do pet retorna 409.
- [ ] Conflito do funcionário está implementado mesmo que não haja funcionários cadastrados atualmente.
- [ ] Horários adjacentes sem sobreposição são permitidos.

## Status

- [ ] Máquina de estados está centralizada.
- [ ] Transições inválidas retornam 409.
- [ ] Permissões por transição são respeitadas.
- [ ] Cancelamento exige motivo.
- [ ] Não comparecimento só após o horário.
- [ ] Estados terminais não reabrem.

## Conclusão

- [ ] Concluir cria exatamente um `servico_realizado`.
- [ ] Segunda conclusão é rejeitada.
- [ ] Valor final é gravado corretamente.
- [ ] Nenhuma venda/pagamento/caixa é criado.

## Vacinação

- [ ] Fluxo só aparece quando existe serviço `Vacinação` ativo.
- [ ] Conclusão exige dados da vacina.
- [ ] Regra da carteira é reutilizada, não duplicada.
- [ ] Vacina de espécie incompatível falha.
- [ ] Falha na vacina desfaz toda a conclusão.
- [ ] Dose aparece posteriormente na carteira do pet.

## Frontend

- [ ] `/agendamentos` funciona em mobile e desktop.
- [ ] Navegação dia anterior/próximo/hoje funciona.
- [ ] Visão semanal funciona.
- [ ] `/agendamentos/novo` funciona.
- [ ] Pet pode iniciar agendamento pela ficha.
- [ ] Preço é exibido antes de confirmar.
- [ ] Ações de status respeitam papel/estado.
- [ ] Próximos agendamentos aparecem na ficha do pet.
- [ ] Item Agendamentos está liberado no menu.
- [ ] Estados vazio/loading/erro existem.

## Auditoria

- [ ] CRUD administrativo de serviço é auditado.
- [ ] Criação/edição de agendamento é auditada.
- [ ] Cada transição relevante é auditada uma vez.
- [ ] Status anterior/novo constam nos detalhes.

## Qualidade

- [ ] Testes unitários passam.
- [ ] Testes IT passam.
- [ ] Testes frontend passam.
- [ ] Testes anteriores continuam passando.
- [ ] Build backend passa.
- [ ] Build frontend passa.
- [ ] Lint passa.
- [ ] Typecheck passa.

---

# 37. Riscos

## R01 — Duração não congelada

A V1 não possui snapshot de duração no agendamento. Alterar `servico.duracao_minutos` pode modificar a interpretação de conflitos de agendamentos antigos ainda ativos.

Mitigação nesta etapa: documentar o comportamento e evitar alterações administrativas de duração de serviços com agenda futura sem revisão operacional. Snapshot de duração pode entrar em migration futura.

## R02 — Identificação transitória de vacinação

O schema não possui tipo/categoria de serviço.

Mitigação: reconhecer somente nome normalizado exatamente `Vacinação`; nunca usar `contains("vacina")`. Registrar como dívida técnica para futura coluna/tipo explícito.

## R03 — Concorrência na criação

Duas requisições simultâneas podem passar pela verificação de conflito antes do commit.

Mitigação: executar verificação e persistência em transação; manter testes. Lock pessimista/estratégia avançada pode ser adicionada se carga real justificar. Não criar constraint impossível de representar para intervalos no MySQL.

## R04 — Funcionários ausentes

A agenda não poderá atribuir profissional enquanto não existir cadastro de usuários/funcionários.

Mitigação: `funcionario_id` nulo e arquitetura preparada para validação/conflito futuro.

## R05 — Financeiro desacoplado

`servico_realizado.valor` não representa recebimento.

Mitigação: UI deve chamar de **valor do serviço**, nunca “pago/recebido”. PDV/financeiro fará a liquidação futuramente.

---

# 38. Próximas etapas

Ficam explicitamente para especificações posteriores:

- PDV/vendas;
- pagamento e caixa;
- financeiro;
- estoque e baixa de produtos usados;
- cadastro de usuários/funcionários;
- escalas;
- comissão;
- múltiplos serviços por agendamento;
- snapshot de duração;
- tipo/categoria formal de serviço;
- notificações;
- migração de dados do legado.

---

# 39. Ordem obrigatória de implementação

1. Inspecionar arquitetura e baseline.
2. Inspecionar V1–V3.
3. Localizar API pública/application service da carteira de vacinação.
4. Criar V4 somente com índices ausentes.
5. Implementar/ajustar domínio de serviços.
6. Implementar CRUD administrativo de serviços/preços.
7. Implementar cálculo de preço.
8. Implementar domínio de agendamento e máquina de estados.
9. Implementar detecção de conflito.
10. Implementar criação/listagem/detalhe/edição.
11. Implementar transições.
12. Implementar `servico_realizado` na conclusão.
13. Integrar conclusão de vacinação reutilizando módulo de pets.
14. Implementar próximos agendamentos do pet.
15. Implementar auditoria.
16. Implementar testes unitários.
17. Implementar ITs.
18. Implementar `/agendamentos`.
19. Implementar `/agendamentos/novo`.
20. Implementar `/agendamentos/[id]`.
21. Implementar telas administrativas de serviços.
22. Integrar CTA/próximos agendamentos em `/pets/[id]`.
23. Liberar menu Agendamentos.
24. Implementar testes frontend.
25. Executar lint/typecheck.
26. Executar todos os testes.
27. Executar builds.
28. Revisar `git diff` e confirmar que V1–V3/legacy não foram alterados.

---

# 40. Restrições para o agente

- NÃO editar V1, V2 ou V3.
- NÃO modificar `legacy/php/`.
- NÃO criar `agendamento_servico` nesta etapa.
- NÃO implementar múltiplos serviços por agendamento.
- NÃO criar CRUD improvisado de funcionário.
- NÃO assumir porte Pequeno para pet sem porte.
- NÃO aceitar preço calculado pelo frontend como fonte de verdade.
- NÃO recalcular automaticamente agendamentos antigos após mudança de preço.
- NÃO permitir alteração direta de status em PUT genérico.
- NÃO duplicar regras de vacinação.
- NÃO identificar vacinação por substring.
- NÃO criar venda, pagamento ou movimentação financeira.
- NÃO apagar agendamentos fisicamente.
- NÃO remover/desabilitar testes para obter build verde.
- NÃO usar `any` para contornar TypeScript.
- NÃO expor JWT.
- NÃO fazer commit/push automaticamente.
- NÃO declarar concluído sem testes/validações correspondentes.

---

# 41. Relatório final obrigatório

Ao concluir, apresentar:

```text
## Implementação concluída

### Banco
- migration V4 criada
- índices efetivamente adicionados

### Arquitetura MVC
- Models/domínio implementados
- Controllers implementados
- serviços de aplicação implementados
- Views/rotas Next.js implementadas

### Backend
- arquivos criados/alterados
- endpoints implementados
- máquina de estados
- conflito de agenda
- cálculo de preço

### Integrações
- servico_realizado
- carteira de vacinação
- ficha do pet

### Frontend
- rotas
- componentes
- agenda diária/semanal
- ações de status

### Segurança
- @PreAuthorize por operação/transição

### Auditoria
- eventos implementados

### Testes
- unitários: X aprovados / Y falharam
- integração: X aprovados / Y falharam
- frontend: X aprovados / Y falharam

### Validações finais
- backend build: PASS/FAIL
- frontend build: PASS/FAIL
- lint: PASS/FAIL
- typecheck: PASS/FAIL

### Pendências
- somente pendências reais encontradas durante a implementação
```

---

# 42. Definição de pronto

O fluxo abaixo deve funcionar de ponta a ponta:

```text
Login
  ↓
Agendamentos
  ↓
Agenda do dia/semana
  ↓
Novo agendamento
  ↓
Tutor
  ↓
Pet
  ↓
Serviço
  ↓
Preço + duração
  ↓
Data/hora
  ↓
Validação de conflito
  ↓
AGENDADO
  ↓
CONFIRMADO
  ↓
EM_ANDAMENTO
  ↓
CONCLUIDO
  ↓
servico_realizado
```

Para vacinação:

```text
EM_ANDAMENTO
  ↓
Concluir serviço Vacinação
  ↓
Validar vacina pela regra do módulo Pets
  ↓
Registrar carteira
  ↓
Criar servico_realizado
  ↓
CONCLUIDO
```

E pela ficha do pet:

```text
Pet
  ├── Agendar serviço
  └── Próximos agendamentos
```

A etapa somente está pronta quando preço, conflitos, máquina de estados, permissões, auditoria, conclusão, integração com carteira, frontend responsivo e testes estiverem funcionando conforme esta especificação.
