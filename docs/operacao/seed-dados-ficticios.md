# Seed de dados fictícios (desenvolvimento e testes)

`scripts/seed-dados-ficticios.py` popula um ambiente **de desenvolvimento/teste** com dados fictícios em várias situações, para você trabalhar e testar sem cadastrar tudo à mão.

> **Nunca rode em produção nem em homologação com dados de cliente.** O script recusa hosts que não sejam locais (`localhost`, `127.0.0.1`, `*.test`). Ele **não** é uma migration: não entra no Flyway e não roda sozinho.

## Como funciona

- Usa a **API** (não SQL direto): estoque, lotes, caixa, financeiro, contas a pagar e auditoria ficam consistentes, e o script não depende da versão do schema.
- Dados 100% fictícios: nomes com o sufixo `(Seed)`, e-mails `@seed.petable.test`, CPF e CNPJ gerados por algoritmo (válidos nos dígitos verificadores, sem pertencer a ninguém de propósito), telefones e CEPs de exemplo.
- Determinístico: as escolhas aleatórias usam uma semente fixa. Roda **uma vez por banco**; se o usuário `seed.admin2` já existir, o script para.
- Cada falha de regra de negócio é registrada e o script continua. No fim ele mostra o resumo e quantas falhas houve.

## Como rodar

1. Suba o ambiente de desenvolvimento e anote a porta da API e a senha do ADMIN do `.env`.
2. Rode (a senha vem do ambiente, nunca da linha de comando):

```bash
ADMIN_PASSWORD='<senha-do-admin-do-seu-.env>' python scripts/seed-dados-ficticios.py --api http://127.0.0.1:8080 --admin admin
```

3. As senhas dos usuários de teste ficam em `.seed-credenciais.txt` (ignorado pelo git). O script **não imprime** senhas.

Requisitos: Python 3.9+ (só biblioteca padrão). Para recomeçar: apague o banco (`docker compose down -v` do **seu** ambiente de desenvolvimento) e suba de novo.

## O que é criado (e em que situação)

| Tabela | Mínimo | Situações cobertas |
|---|---|---|
| `usuario` | 7 (6 + o ADMIN do ambiente) | ADMIN, 2 VENDAS, 2 SERVICO, 1 inativo |
| `funcionario` | 6 | um por usuário, sexos e idades variados |
| `cliente` / `endereco` | 10 | com e sem CPF, e-mail, endereço, nascimento; 2 inativos |
| `pet` | 12 | cão, gato, ave, roedor, réptil, peixe; portes e idades diferentes; castrado ou não |
| `carteira_vacina` | 7 | vacinas em dia, a vencer e vencidas |
| `historico_consulta` | 6 | motivos e diagnósticos diversos |
| `fornecedor` | 6 | ativos e 1 inativo |
| `servico` e `preco_servico_porte` | 5 novos + os da referência | preço por porte |
| `produto` | 12 | com e sem controle de lote, estoque mínimo, 1 descontinuado (inativo) |
| `compra` / `item_compra` / `item_compra_lote` | 7 / 18 / 18 | 5 recebidas (vencimentos diferentes), 1 pedido em aberto, 1 cancelada |
| `lote_estoque` | vários | validade próxima e distante, perda, lote bloqueado (se a flag de lotes estiver ligada) |
| `movimentacao_estoque` | muitas | entrada de compra, ajustes positivos e negativos, venda, estorno, perda |
| `conta_pagar` | 5 | 2 pagas e 3 em aberto (uma já vencida) |
| `caixa` / `caixa_movimento` | 7 / 6 | 6 fechados (sem diferença, com falta e com sobra) e 1 aberto de propósito; suprimentos e sangrias |
| `venda` / `item_venda` / `item_venda_lote` | 10 ou mais | todas as formas de pagamento, com e sem cliente, desconto, troco, 2 canceladas (estoque retorna ou é descartado) |
| `movimentacao_financeira` | muitas | entradas de venda e serviço, saídas manuais, 1 estorno |
| `agendamento` / `servico_realizado` | 12 / 5 | agendado, confirmado, em andamento, concluído (com pagamento), cancelado, não compareceu |
| `token_redefinicao_senha` | 5 | um por usuário |

Fora do seed, de propósito: `migracao_legado_execucao` e `migracao_legado_mapa` (pertencem à migração offline do banco legado), `modulo_instalacao_historico` (o sistema preenche ao iniciar), `papel`, `especie`, `raca`, `categoria_produto` e `vacina` (dados de referência que já vêm das migrations) e a auditoria no MongoDB (gerada pela própria API).

## Limitações conhecidas

- A API só aceita **data futura** ao criar um agendamento. Por isso os "concluídos" e o "em andamento" são criados para algumas horas à frente e avançam de status; só o "não compareceu" espera o horário passar (o script aguarda cerca de 2 minutos).
- `papel` fica com 3 linhas (ADMIN, VENDAS, SERVICO são fixos). As tabelas `migracao_legado_*` e `modulo_instalacao_historico` ficam fora do seed, como descrito acima.
- Validado em 2026-10-26 em banco limpo da build da etapa 11 (schema V14), com 0 falhas e todas as tabelas de negócio com 5 ou mais linhas. Na `main` atual o campo `nomeCompleto` do usuário é enviado a mais e deve ser ignorado pela API, mas **não foi testado nessa build**.
- Não é idempotente: repetir exige recriar o banco.

## Estado em que o ambiente fica

- O caixa de `seed.vendas2` fica **aberto** com vendas, para testar o PDV e o fechamento. O do ADMIN fica fechado, para você testar a abertura.
- Há produtos com estoque baixo, zerado e com lote perto do vencimento, para testar alertas.
