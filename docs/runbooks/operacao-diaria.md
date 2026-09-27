# Operação diária

**Todo dia (5 min)**
1. Leia os alertas do webhook. Sem alerta é o esperado: o `monitorar.sh` só avisa em problema.
2. Confira o backup da noite: `tail -n 3 /var/log/petconnect-backup.log`. A última linha deve ser `Backup concluído`.

**Toda semana**
1. Confira o teste de restore de segunda-feira: `grep -E "PASS|FAIL|sucesso" /var/log/petconnect-restore-test.log | tail`.
2. Veja o espaço: `df -h /` e `docker system df`. Limpe imagens antigas com `docker image prune -f`; ele mantém as em uso.
3. Leia os PRs do Dependabot e os alertas do workflow **Segurança** (Trivy/CodeQL) no GitHub.

**Todo mês**
1. Atualize o sistema operacional (`apt update && apt upgrade`) numa janela de baixo movimento e rode `./scripts/smoke.sh`.
2. Confirme que a cópia off-site (`BACKUP_REMOTO`) está recebendo arquivos.

**Comandos úteis**
```bash
docker compose -f compose.prod.yaml --env-file .env.prod ps
docker compose -f compose.prod.yaml --env-file .env.prod logs -f --tail 200 api        # JSON; filtre por "requestId"
docker compose -f compose.prod.yaml --env-file .env.prod exec api curl -s localhost:9090/actuator/health
./scripts/monitorar.sh; echo $?
```
