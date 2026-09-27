# Go-live (checklist)

## Infraestrutura

- [ ] VPS com Docker e Compose; firewall liberando só 22 (restrito), 80 e 443.
- [ ] DNS de `<DOMINIO>` apontando para `<IP_DO_SERVIDOR>`.
- [ ] `/opt/petconnect` com o repositório na tag da release.
- [ ] `.env.prod` completo, com chmod 600 e **cópia no cofre**, junto com a `BACKUP_PASSPHRASE`.
- [ ] `CADDY_TLS` com e-mail real; `TAG` da release.
- [ ] `docker compose -f compose.prod.yaml --env-file .env.prod up -d --wait` sobe tudo e o certificado HTTPS foi emitido.
- [ ] Pacotes do GHCR acessíveis (`docker login ghcr.io` se forem privados).

## Operação

- [ ] Cron de `backup.sh`, `restore-test.sh` e `monitorar.sh` instalado.
- [ ] Primeiro backup manual feito **e** `restore-test.sh` PASS.
- [ ] `BACKUP_REMOTO` configurado e com a primeira cópia off-site feita.
- [ ] `ALERTA_WEBHOOK_URL` configurado. Teste: pare o web e confira se o alerta chega.
- [ ] Usuário de smoke criado; `./scripts/smoke.sh` PASS.

## Aplicação

- [ ] ADMIN inicial trocou a senha no primeiro acesso; `ADMIN_PASSWORD` removido do `.env.prod` depois disso.
- [ ] SMTP real testado ("Esqueci minha senha" chega).
- [ ] Dados do sistema antigo migrados, se for o caso ([../migracao-legado.md](../migracao-legado.md)):
  - dry-run analisado;
  - backup antes;
  - execução real CONCLUIDA;
  - usuários avisados de que entram pelo "Esqueci minha senha".
- [ ] `PETCONNECT_MIGRACAO_LEGADO=false` (padrão do compose.prod).

## GitHub (configuração do dono)

- [ ] Environment `producao` com **Required reviewers** e os secrets `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY` e `DEPLOY_KNOWN_HOSTS`.
- [ ] Proteção da `main`. Sugestão via `gh`, rodada pelo dono:
  ```bash
  gh api -X PUT repos/AleksGustavo/PetConnect-Web/branches/main/protection \
    -F required_pull_request_reviews.required_approving_review_count=1 \
    -F enforce_admins=true -F allow_force_pushes=false -F allow_deletions=false \
    -F 'required_status_checks[strict]=true' \
    -f 'required_status_checks[contexts][]=Build + testes (unit + integração)' \
    -f 'required_status_checks[contexts][]=Lint + tipos + testes + build' \
    -f 'required_status_checks[contexts][]=Playwright (Chromium)' \
    -F restrictions=
  ```

## LGPD

- [ ] Aviso de privacidade disponível aos clientes (balcão/site), com contato do encarregado.
- [ ] Decisão sobre o script legado com dados reais no histórico do git ([lgpd.md](lgpd.md)).
