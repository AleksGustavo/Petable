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
