# Rollback

## Aplicação (sem migration nova)

O `deploy.sh` já faz isso sozinho quando o smoke falha. Para fazer à mão:

```bash
cat .ultimo-deploy                 # última versão que passou no smoke
./scripts/deploy.sh <tag-anterior>
```

## Aplicação + banco (a versão nova aplicou migration)

A versão antiga pode não funcionar com o schema novo, porque o Hibernate valida o schema. Neste caso:
1. Avise os usuários (sistema indisponível).
2. Restaure o backup feito pelo deploy, que é o mais recente em `backups/diario/`, seguindo [backup-restore.md](backup-restore.md#restore-real).
3. Faça o deploy da tag anterior: `./scripts/deploy.sh <tag-anterior>`.
4. Rode `./scripts/smoke.sh`.

Registre no incidente o que foi perdido: as operações feitas entre o deploy e o restore precisam ser relançadas.

## Critério

Rollback **sempre** que:
- o smoke falhar;
- houver erro 5xx recorrente;
- ou algum fluxo crítico quebrar (login, PDV, caixa, agendamento).

Não tente corrigir em produção.
