# Guia operacional — suporte e treinamento (etapa 09)

Para quem atende o cliente (suporte) e para quem treina os usuários do petshop. Procedimentos de infraestrutura estão nos [runbooks](../runbooks/README.md) e no [guia de novo cliente](08_GUIA_NOVO_CLIENTE.md). **Só descreve o que existe e foi testado**; o que não existe está em "Não faz parte".

## 1. Quem faz o quê (papéis)

| Papel | Vê e faz | Não faz |
|---|---|---|
| **ADMIN** | tudo dos módulos contratados: usuários, ajuste de estoque, cancelar venda, compras, financeiro, relatórios, privacidade (LGPD) | — |
| **VENDAS** | PDV, caixa próprio, cadastros (clientes, pets, produtos), agenda, relatórios de vendas | cancelar venda, ajustar estoque, receber compra, financeiro gerencial, usuários |
| **SERVICO** | agenda e atendimentos (concluir serviço), carteira de vacinação | vendas, compras, financeiro, relatórios |

Contratar um módulo **não dá** permissão: o usuário precisa do papel certo **e** o módulo precisa estar contratado.

## 2. Mensagens que o usuário vê e o que significam

| Mensagem | Causa | O que fazer |
|---|---|---|
| "Este recurso não está disponível nesta instalação." | módulo não contratado | confirmar o contrato; se for o caso, [incluir o módulo](08_GUIA_NOVO_CLIENTE.md#módulos-contratados) |
| "Você não possui permissão para realizar esta ação." | papel insuficiente | pedir a um ADMIN (ou ajustar o papel em Usuários) |
| "Abra o caixa" / caixa fechado | venda ou conclusão de serviço exigem caixa aberto do próprio usuário | abrir o caixa (Caixa → Abrir) |
| Saldo/estoque insuficiente (409) ao vender | falta saldo vendável (lote vencido ou bloqueado não conta) | conferir lotes e validade; receber a compra; ajustar com motivo |
| Item de menu ausente | módulo desligado **ou** papel sem acesso | ver as duas causas acima |

## 3. Treinamento por perfil (roteiro de 30 min cada)

**Caixa / balcão (VENDAS):** abrir o caixa → vender (busca por nome/código, desconto, forma de pagamento, troco) → comprovante → consultar vendas de hoje → fechar o caixa. Exercício: vender, errar, pedir ao ADMIN para cancelar com motivo e ver o saldo voltar.

**Gerente (ADMIN):** receber compra (conferir quantidade e custo; com lote, informar código e validade) → ajuste de estoque com motivo → contas a pagar → relatórios → usuários e papéis → privacidade do cliente (exportar/anonimizar).

**Banho/tosa e vacinação (SERVICO/VENDAS):** agendar → confirmar → iniciar → concluir (forma de pagamento; na vacinação, escolher a vacina e a dose é baixada do lote que vence primeiro) → carteira do pet.

**Regras de ouro para o usuário:** nunca vender produto vencido (o sistema bloqueia); informar sempre o motivo em ajuste e cancelamento; cada pessoa usa o **próprio** usuário (o caixa é pessoal).

## 4. Triagem de chamado (suporte)

1. **Qual cliente (instalação)?** Tudo a seguir é por instalação: `scripts/cliente.sh status <id>`, `logs <id> api`.
2. **Sistema fora do ar?** `scripts/cliente.sh smoke <id>` e o alerta do `monitorar`. Procedimento: [incidente](../runbooks/incidente.md), [rollback](../runbooks/rollback.md).
3. **"Não acho a tela/botão":** pergunte o usuário e o papel; confira `GET /api/v1/modulos` (módulos) antes de suspeitar de defeito.
4. **Divergência de estoque:** rodar a reconciliação como ADMIN (`GET /api/v1/estoque/reconciliacao`, só leitura). `consistente: true` descarta defeito de saldo; com divergência, **não corrigir à mão**: abrir incidente com o JSON da resposta e o horário.
5. **Venda duplicada?** A API é idempotente por `Idempotency-Key`: repetir a mesma chave devolve a mesma venda. Duas vendas com chaves diferentes são duas vendas reais.
6. **Dado de titular (LGPD):** [lgpd.md](../runbooks/lgpd.md); prazo de 15 dias.
7. Registre o chamado com: cliente, versão (`TAG`), módulos, horário, `X-Request-Id` da resposta (liga o log do Next ao da API).

## 5. Não faz parte (diga ao cliente sem rodeios)

Venda a granel e quantidade decimal, venda a prazo/fiado e contas a receber, nota fiscal (NF-e/NFC-e), prontuário completo, prescrição, exames e internação, transferência entre locais, multiempresa dentro de uma instalação, licença online e cobrança automática, notificações automáticas ao tutor, modo offline. Lista oficial: seção de ausências do [catálogo](../implementacao/06_CATALOGO_MODULOS.md).
