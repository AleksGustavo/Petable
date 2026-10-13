# Deploy

A versão é identificada pela **tag da imagem** no GHCR:
- git tag `v1.2.3` → imagem `1.2.3` (publicada pelo workflow **Entrega**);
- ou `sha-abc1234`.

## Pelo GitHub (recomendado)

1. Crie a tag no GitHub (`git tag v1.2.3 && git push origin v1.2.3`) e espere o workflow **Entrega** publicar as imagens.
2. Em **Actions → Deploy (produção) → Run workflow**, informe `1.2.3`.
3. Um aprovador do environment `producao` aprova.
4. O job conecta por SSH e roda `scripts/deploy.sh 1.2.3`:
   1. **backup** (sempre);
   2. troca `TAG` no `.env.prod`;
   3. `pull`;
   4. `up -d --wait`;
   5. **smoke test**.
5. Se o smoke falhar, o script **volta sozinho para a tag anterior** e o job fica vermelho. Siga [rollback.md](rollback.md).

## Manual (no servidor)

```bash
cd /opt/petconnect && ./scripts/deploy.sh 1.2.3
```

## Quando o deploy muda infraestrutura

Se a versão alterou `compose.prod.yaml`, `deploy/Caddyfile` ou `scripts/`, atualize os arquivos antes do deploy:

```bash
git fetch --tags && git checkout v1.2.3
```

Mudanças nesses arquivos aparecem nas notas da release.

## Migrations

- O Flyway roda na subida da API. O backup antes do deploy é o plano de volta do **banco**.
- Migration destrutiva (DROP/RENAME de coluna com dados) só com plano escrito no PR:
  - deploy em duas fases (expandir → migrar dados → contrair);
  - janela de manutenção combinada.
