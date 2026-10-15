# Relatório de homologação — etapa 08

Ambiente: Windows 11 + Docker Desktop (WSL2, 7,9 GB), imagens construídas do código da `main` (`ghcr.io/aleksgustavo/petconnect-web-{api,web}:local`), duas instalações pelo `scripts/cliente.sh`, execução do `scripts/homologacao.sh homolog-a homolog-b`. **Não foi servidor remoto**: TLS é o certificado interno do Caddy, DNS é `*.localhost`, e a cópia off-site não foi exercitada.

| Instalação | Domínio | Portas | Módulos | Volumes |
|---|---|---|---|---|
| `homolog-a` | `a.localhost` | 18801/18441 | `produtos,estoque,caixa,pdv` (depois `+clientes`) | `petconnect-homolog-a_{mysql-data,mongo-data,uploads,caddy-*}` |
| `homolog-b` | `b.localhost` | 18802/18442 | `todos` | `petconnect-homolog-b_{mysql-data,mongo-data,uploads,caddy-*}` |

## Resultado (todos PASS)

1. **Isolamento** (`cliente.sh isolamento`): validação das duas; volumes e redes próprios sem nenhum em comum; pastas de backup distintas; a API de A **não alcança** MySQL nem Mongo de B (e vice-versa, teste TCP entre containers).
2. **Módulos diferentes respeitados:** `/api/v1/clientes` → **403 `MODULO_INDISPONIVEL` em A** e **200 em B**; `/produtos` → 200 em A.
3. **Segredo por instalação:** token de A em B → 401, e de B em A → 401.
4. **Modificar A não afeta B:** produto criado em A não aparece em B e vice-versa.
5. **Backup/restore:** backup de A (manifesto com `instalacao=homolog-a`); `restore-test` (MySQL descartável, contagens e versão do Flyway); **restore do backup de A em B recusado**; produto criado depois do backup some após o **restore real de A**, o anterior permanece; B intocado; smoke de A e B depois.
6. **Upgrade/downgrade de módulos:** A passou a `…,clientes` (backup → validação → reinício → smoke) e `/clientes` virou 200; lista inválida (`pdv` sem `caixa`) recusada **antes** de reiniciar; downgrade removendo `caixa`/`pdv` com **caixa aberto** recusado pela API no boot ("não é possível remover 'caixa': caixa aberto com movimento por fechar (1). Nenhum dado foi alterado.") e o script **voltou sozinho** à lista anterior; dados preservados; histórico gravado em `modulo_instalacao_historico`.

Também: `cliente.sh novo` recusa id inválido, domínio repetido, módulo desconhecido e dependência faltando, sem criar nada.

## Defeitos encontrados e corrigidos nesta etapa

| Achado | Correção |
|---|---|
| `compose.prod.yaml` **inválido** desde a etapa 07 (`ex.: ` dentro de valor sem aspas quebrava o YAML) | texto da mensagem reescrito; o compose agora carrega |
| `smoke.sh` ignorava `HTTPS_PORT`/`HTTP_PORT` (só funcionava em 80/443) | monta a URL com a porta quando não é a padrão |

## NÃO EXECUTADO

- Servidor remoto real, DNS, Let's Encrypt e imagens do GHCR (acesso fora do escopo desta etapa).
- Cópia off-site (`BACKUP_REMOTO`/rclone) e o ensaio de desastre em VM temporária.
- Deploy automático por cliente via GitHub Actions (não criado).
- Fotos de pets entre instalações: o isolamento do volume `uploads` foi verificado, mas nenhum upload foi feito.
- Revisão jurídica de LGPD (controlador/operador): registrada como decisão pendente no [checklist](08_CHECKLIST_SEGURANCA_PRIVACIDADE.md).

## Como repetir

```bash
scripts/cliente.sh novo homolog-a --dominio a.localhost --modulos produtos,estoque,caixa,pdv --tls internal --tag local --http-port 18801 --https-port 18441
scripts/cliente.sh novo homolog-b --dominio b.localhost --modulos todos --tls internal --tag local --http-port 18802 --https-port 18442
scripts/cliente.sh subir homolog-a && scripts/cliente.sh subir homolog-b
scripts/cliente.sh smoke-usuario homolog-a && scripts/cliente.sh smoke-usuario homolog-b
scripts/homologacao.sh homolog-a homolog-b
```

Só em homologação: o script cria produtos de teste, abre um caixa e restaura o backup de A.
