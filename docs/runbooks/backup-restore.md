# Backup e restore

## Como funciona

- `scripts/backup.sh` (cron diário às 02:30) gera em `backups/diario/<data>/`:
  - `mysql.sql.gz.enc`, `mongo.archive.gz.enc` e `fotos.tar.gz.enc`, cifrados com AES-256 (`BACKUP_PASSPHRASE`);
  - `manifesto.txt`, com contagens das tabelas, versão do Flyway e sha256 dos arquivos. **Não tem dados pessoais.**
- **Retenção:** 7 diários, 4 semanais (domingo) e 6 mensais (dia 1).
- **Off-site:** `rclone copy` para `BACKUP_REMOTO`, se configurado.
- Uma falha no meio remove a pasta parcial e sai com erro. O `monitorar.sh` alerta se não houver backup em 26 h.

## Teste de restore (automático, semanal)

```bash
./scripts/restore-test.sh                                  # último diário
./scripts/restore-test.sh backups/mensal/20260901-023000  # um específico
```

O teste:
1. confere os sha256;
2. valida os arquivos do Mongo e das fotos;
3. restaura o MySQL num container **descartável**;
4. compara as contagens com o manifesto.

Não toca na produção. Qualquer `FAIL` → investigue no mesmo dia.

## Restore real

Use só em incidente, desastre ou rollback com migration.

1. Escolha o backup e confira: `./scripts/restore-test.sh <pasta>`.
2. Avise os usuários. O sistema fica fora durante o restore.
3. Rode:
   ```bash
   ./scripts/restore.sh backups/diario/<data>
   ```
   Digite `RESTAURAR`. O script:
   1. para web e api;
   2. recria o banco MySQL;
   3. faz `mongorestore --drop`;
   4. repõe as fotos;
   5. sobe tudo.
4. Rode `./scripts/smoke.sh` e confira clientes, vendas do dia e caixa.

**Sem a `BACKUP_PASSPHRASE` não há restore.** Ela fica no cofre de senhas do dono, junto com o `.env.prod`.
