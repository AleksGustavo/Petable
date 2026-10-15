# Checklist de segurança e privacidade por instalação (etapa 08)

Marque por cliente antes de liberar. `[auto]` = verificado por `cliente.sh validar|isolamento`; `[manual]` = confira você.

## Isolamento

- [ ] `[auto]` Domínio e `BIND_IP:porta` próprios; nenhum repetido em outro cliente.
- [ ] `[auto]` `JWT_SECRET`, senhas de MySQL/Mongo, frase de backup, senha do ADMIN e do smoke **diferentes** de todos os outros clientes.
- [ ] `[auto]` Volumes e redes só do projeto `petconnect-<id>`; a API de um cliente não alcança o MySQL/Mongo de outro (`cliente.sh isolamento`).
- [ ] `[auto]` Pasta de backups própria; backup de outra instalação é recusado pelo restore.
- [ ] `[manual]` `.env.prod` com `chmod 600` (o `validar` confere em Linux), fora do git, e cópia **no cofre do cliente**, separada da dos demais.
- [ ] `[manual]` Cópia off-site com credencial restrita à pasta deste cliente.
- [ ] `[manual]` Banco, API e Mongo **sem porta publicada** (`docker compose ... ps` mostra só o Caddy com portas).
- [ ] `[manual]` Jobs de cron citam apenas o `<id>` deste cliente.

## Acesso e módulos

- [ ] `PETCONNECT_MODULOS` conforme o contrato; `GET /api/v1/modulos` confere; uma rota de módulo **não** contratado responde 403.
- [ ] Menu e telas coerentes com os módulos (sem item de módulo desligado).
- [ ] ADMIN inicial trocou a senha; `ADMIN_PASSWORD` removido do `.env.prod`.
- [ ] Usuários com o papel mínimo (VENDAS/SERVICO quando bastar); funcionários desligados desativados.
- [ ] Usuário técnico de smoke com papel VENDAS apenas.

## Privacidade (LGPD)

> **Decisão jurídica pendente (não é do ERP):** papéis de **controlador** e **operador**, base legal por finalidade, encarregado (DPO), prazos de retenção e cláusulas de operador. O padrão técnico abaixo assume o petshop como controlador e o provedor do PetConnect como operador, **mas isso deve ser confirmado em contrato revisado por jurídico**.

- [ ] Contrato com o cliente define controlador/operador, finalidade, retenção e o que acontece no fim do contrato.
- [ ] Aviso de privacidade do petshop disponível a seus clientes, com contato do encarregado.
- [ ] Pedidos do titular: exportar e anonimizar pela página do cliente (ADMIN) — [lgpd.md](../runbooks/lgpd.md); prazo de resposta de 15 dias.
- [ ] Backups guardados cifrados, por no máximo a retenção (6 meses mensais) e sem acesso do operador de **outro** cliente.
- [ ] Logs mascarados (CPF, e-mail, telefone, tokens) e log de acesso com IP truncado: configurados no perfil `prod`; não desligar.
- [ ] Mínimo privilégio da equipe do operador: quem acessa o servidor/cofre de um cliente não precisa dos demais.
- [ ] Incidente com dado pessoal: [incidente.md](../runbooks/incidente.md) e comunicação ao controlador (cliente) no prazo contratado.

## Transporte e borda

- [ ] TLS válido (Let's Encrypt, `CADDY_TLS` com e-mail real), HTTP→HTTPS (308), HSTS, CSP com nonce (`cliente.sh smoke` confere).
- [ ] Firewall do host: só 22 (restrito), 80 e 443 (ou o `BIND_IP` do cliente).

## Operação contínua

- [ ] `restore-test` semanal passando; alerta de backup atrasado funcionando (webhook do cliente).
- [ ] Rotação anual de segredos ([rotacao-segredos.md](../runbooks/rotacao-segredos.md)); `rotacionar-jwt` se houver suspeita.
- [ ] Revisão semestral: módulos contratados × ativos, usuários, capacidade do host.
