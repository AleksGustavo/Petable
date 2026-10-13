# Etapa 01 — Diagnóstico técnico e contratos do domínio

## Objetivo
Mapear a implementação existente antes de alterar estoque, vendas, compras e configuração de acesso. **Não criar funcionalidades nesta etapa.**

## Trabalho do Claude
1. Confirmar repositório, branch atual, diretórios e frameworks reais; identificar migrations, banco, testes e CI.
2. Traçar fluxo **compra → recebimento → saldo de estoque → venda PDV → cancelamento/devolução → financeiro/caixa**, incluindo transações, endpoints, DTOs, interfaces e auditoria.
3. Localizar cadastro de produto, variações/unidade, estoques por local (se houver), saldos, movimentos, reservas, ajustes, perdas, transferências, preços/custos e campo de lote em carteira de vacina; verificar possibilidade de reconciliação.
4. Verificar se existe multiempresa, usuário vinculado a organização, feature flags, RBAC, permissões de acesso e deploy da etapa 09.
5. Levantar integrações: leitor de código de barras, importação de XML/NF-e, offline/sincronização, lotes já conhecidos de produtos existentes e serviços/medicamentos.
6. Propor modelo de lote sem duplicar ledger de estoque; definir saldo por produto + local + lote (ou justificar outra chave).
7. Especificar contrato de migração para **estoque legado sem lote**: não inventar lote/validade; manter quantitativos e exigir regularização quando habilitar controle no produto com saldo existente.
8. Escrever a matriz de regras abaixo, indicando decisão adotada, justificativa e impacto.

## Regras para decidir e registrar
- SKU controlado por lote: quantidade comercializada deve ser alocada a um lote identificável; rastreio e histórico permanentes.
- Validade: armazenar data (sem horário), fuso apenas em relatórios; política de vencimento configurável; **bloqueio padrão de vencidos** no PDV.
- Alocação: **FEFO** (First Expire, First Out); lotes sem validade ao final, caso permitidos; empate por entrada mais antiga e ID estável.
- Quantidade: usar decimal de precisão compatível com unidades existentes, nunca float; normalização de unidade documentada.
- Cancelamento ou devolução: reverter ao **lote original**; não recompor automaticamente saldo vendável de produtos danificados/vencidos.
- Estoque negativo proibido para item controlado, exceto política explícita aprovada.
- Controle por produto ativado só após tratamento de saldo legado; nunca alterar classificação silenciosamente.
- Preparação para setores: vacinas, medicamentos, rações e alimentos; não confundir lote clínico da aplicação com lote físico movimentado.

## Entregáveis
- `docs/implementacao/01_MAPA_ATUAL.md` com caminhos reais das classes e migrations.
- `docs/implementacao/01_DECISOES_LOTES.md` com modelo de dados, regras, riscos e pontos abertos.
- Checklist de testes existentes e comandos reais para executá-los.
- Nenhuma alteração no comportamento de produção.

## Aceite
- Fluxos e pontos transacionais identificados.
- Solução de migração de saldos legados, FEFO, vencimento, concorrência e cancelamentos documentada.
- Revisão indica o que já existe e o que precisa ser criado; sem afirmações sem evidência.

**Ao concluir:** relatório conciso; aguardar autorização para etapa 02.
