# Runbook — módulos contratados por instalação

Referência: [etapa 07](../implementacao/07_AUTORIZACAO_MODULAR.md) e [catálogo](../implementacao/06_CATALOGO_MODULOS.md).

## Definir ou alterar os módulos

1. Faça **backup** (ver `backup-restore.md`).
2. Escolha a lista (`todos` ou ids do catálogo; pacotes prontos na seção 5 do catálogo). Exemplo PDV: `produtos,estoque,caixa,pdv`.
3. Defina `PETCONNECT_MODULOS` no `.env` da instalação (em produção é obrigatória).
4. Reinicie a API. Não há hot reload.
5. Confira: a API sobe (lista inválida **recusa o boot** com a causa), `GET /api/v1/modulos` reflete a lista e a auditoria `MODULOS_ALTERADOS` foi registrada.

## Remover um módulo (downgrade)

Nunca apaga dado. A API recusa a partida se houver pendência (ex.: caixa aberto, lote com saldo, conta a pagar aberta, pedido de compra em aberto, serviço de vacinação ativo sem clínica) e diz qual. Resolva a pendência na operação e reinicie. Para voltar, reponha o módulo e reinicie: os dados continuam lá.

## Sintomas

| Sintoma | Causa provável |
|---|---|
| Boot recusado "Configuração de módulos recusada" | Lista inválida ou remoção com pendência; a mensagem diz o que fazer |
| 403 "Este recurso não está disponível nesta instalação." | Módulo não contratado (esperado); confira `GET /api/v1/modulos` |
| 403 "sem permissão" | Papel do usuário, não o módulo |
| Menu sem um item | Módulo desligado **ou** papel sem acesso |
