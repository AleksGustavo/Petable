# Runbooks — PetConnect Web

Procedimentos de operação em produção. Todos partem de `/opt/petconnect` no servidor. Visão geral: [../producao.md](../producao.md).

| Situação | Runbook |
|---|---|
| Rotina diária/semanal | [operacao-diaria.md](operacao-diaria.md) |
| Publicar uma versão | [deploy.md](deploy.md) |
| Versão nova com problema | [rollback.md](rollback.md) |
| Backup, teste de restore, restore real | [backup-restore.md](backup-restore.md) |
| Suspeita de incidente de segurança/vazamento | [incidente.md](incidente.md) |
| Trocar segredos | [rotacao-segredos.md](rotacao-segredos.md) |
| Servidor perdido | [recuperacao-desastre.md](recuperacao-desastre.md) |
| Primeira entrada em produção | [go-live.md](go-live.md) |
| Pedidos do titular (LGPD) e retenção | [lgpd.md](lgpd.md) |
| Ligar o controle de lote e validade (e reverter com segurança) | [lotes-liberacao.md](lotes-liberacao.md) |
| Instalar/operar um novo cliente (uma instalação por cliente) | [../operacao/08_GUIA_NOVO_CLIENTE.md](../operacao/08_GUIA_NOVO_CLIENTE.md) |
| Módulos contratados por cliente | [modulos-por-cliente.md](modulos-por-cliente.md) |
| Go-live por cliente: checklist, canário e relatório de homologação | [../operacao/09_CHECKLIST_GO_LIVE_E_CANARIO.md](../operacao/09_CHECKLIST_GO_LIVE_E_CANARIO.md) |
| Suporte e treinamento de usuários | [../operacao/09_GUIA_SUPORTE_E_TREINAMENTO.md](../operacao/09_GUIA_SUPORTE_E_TREINAMENTO.md) |
| Roteiro de VPS de homologação (GHCR, DNS, TLS, off-site, desastre) | [../operacao/09_ROTEIRO_VPS_HOMOLOGACAO.md](../operacao/09_ROTEIRO_VPS_HOMOLOGACAO.md) |
