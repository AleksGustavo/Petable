# Roteiro — VPS de homologação (evidência para as issues #38, #39 e #40)

Objetivo: provar **num servidor de verdade** o que só foi provado no Docker local: imagens do GHCR, DNS, TLS público, e-mail, backup/restore e cópia off-site. Use uma VPS **descartável** com um subdomínio de teste (`homolog.seudominio.com.br`) e **dados fictícios**. Este roteiro **não foi executado** (nenhum servidor autorizado até 2026-10-19; ver [pré-requisitos](09_HOMOLOGACAO_PRE_REQUISITOS.md)) (precisa de conta no provedor e de DNS); cada passo traz o resultado esperado e o que registrar. Nenhum passo envolve produção nem dados de cliente. **Homologação com dados fictícios não encerra a issue #37** (canário com o primeiro cliente real, que tem aceite e evidências próprios).

Quem faz: o dono (contas, DNS, segredos) ou o operador com acesso delegado. Tempo estimado: 2 a 3 horas.

## 0. Antes de começar

- [ ] VPS Linux (Ubuntu 24.04 LTS ou Debian 12), **2 vCPU, 4 GB RAM, 40 GB SSD**, IPv4 público. (Custo típico: alguns reais por dia; destrua no fim.)
- [ ] Um subdomínio de teste e acesso ao DNS dele.
- [ ] Chave SSH sua cadastrada na VPS; login por senha desabilitado.
- [ ] Um e-mail de teste e um provedor SMTP (ex.: conta gratuita de transacional) — ou aceite testar o e-mail só pelo Mailpit.
- [ ] Token do GitHub só de **leitura de pacotes** (`read:packages`), se as imagens do GHCR forem privadas.
- [ ] Um destino para a cópia off-site (bucket/Drive/SFTP) com credencial **restrita a uma pasta**.

Registre em uma nota: provedor, IP, subdomínio, data, quem executou.

## 1. Servidor

```bash
# como root, uma vez
adduser --disabled-password --gecos "" petconnect && usermod -aG sudo petconnect
rsync --archive --chown=petconnect:petconnect ~/.ssh /home/petconnect/
# antes do firewall: abra OUTRA sessão e confirme que `ssh petconnect@IP` entra; só então feche a de root
# firewall: só SSH, HTTP e HTTPS (MySQL, Mongo, API interna e métricas NÃO são publicados)
ufw allow OpenSSH && ufw allow 80/tcp && ufw allow 443/tcp && ufw allow 443/udp && ufw --force enable
apt-get update && apt-get -y install ca-certificates curl git openssl cron
```

Docker Engine pelo **repositório apt oficial** (não use `curl ... | sh` como root; documentação: https://docs.docker.com/engine/install/ubuntu/, conferida em 2026-10-19):

```bash
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
tee /etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
apt-get update && apt-get -y install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
usermod -aG docker petconnect    # quem entra no grupo docker tem poder de root na máquina: só o operador
mkdir -p /home/petconnect/logs && chown petconnect: /home/petconnect/logs
```

(Debian: troque `ubuntu` por `debian` nos dois endereços.)

Resultado esperado: `ssh petconnect@IP` entra; `docker compose version` responde (v2); `ufw status` mostra só 22, 80 e 443.

## 2. DNS

Crie o registro `A` (e `AAAA` se tiver) de `homolog.seudominio.com.br` apontando para o IP. Aguarde propagar: `dig +short homolog.seudominio.com.br` devolve o IP.

## 3. Código e imagens

```bash
sudo mkdir -p /opt/petconnect && sudo chown petconnect /opt/petconnect
git clone https://github.com/AleksGustavo/Petable.git /opt/petconnect   # nome antigo PetConnect-Web redireciona, mas use o novo
cd /opt/petconnect && git checkout <tag-ou-commit-da-release>   # anote qual
read -rsp "Token read:packages: " GHCR_TOKEN; echo        # não fica no histórico do shell
printf %s "$GHCR_TOKEN" | docker login ghcr.io -u <usuario-github> --password-stdin; unset GHCR_TOKEN
# Use uma tag imutável já publicada (sha-xxxxxxx ou vX.Y.Z); NÃO crie release/tag só para este teste.
docker pull ghcr.io/aleksgustavo/petconnect-web-api:<TAG> && docker pull ghcr.io/aleksgustavo/petconnect-web-web:<TAG>
docker inspect --format '{{index .RepoDigests 0}}' ghcr.io/aleksgustavo/petconnect-web-api:<TAG>   # registre o digest
```

**Esse é o primeiro teste real do GHCR (#38).** Os pacotes continuam com o nome `petconnect-web-api`/`petconnect-web-web`: renomear o repositório não renomeia imagens (o `delivery.yml` usa `petconnect-web-*` sob o owner). Se o `pull` falhar: confira se as imagens existem para essa tag (`delivery.yml` publica por tag `vX.Y.Z` ou `sha-…`), a visibilidade do pacote e o escopo do token. **Se ainda não há tag publicada, pare aqui**: a release exige autorização do dono.

## 4. Instalação de teste pelo `cliente.sh`

```bash
cd /opt/petconnect
scripts/cliente.sh novo homolog --dominio homolog.seudominio.com.br --modulos clientes,produtos,estoque,lotes,caixa,pdv,compras,financeiro,pets,agenda,relatorios \
    --tls seu-email@seudominio.com.br --tag <TAG> --admin-email seu-email@seudominio.com.br --smtp-host <smtp>
$EDITOR clientes/homolog/.env.prod      # SMTP_USERNAME / SMTP_PASSWORD, se for testar e-mail real
scripts/cliente.sh validar homolog
scripts/cliente.sh subir homolog
scripts/cliente.sh smoke-usuario homolog
scripts/cliente.sh smoke homolog
```

Resultado esperado: `subir` termina com todos os containers `Healthy`; o Caddy emite o certificado **Let's Encrypt de verdade** (acesse `https://homolog.seudominio.com.br/login` num navegador, sem aviso de certificado); `smoke` termina com `Smoke test OK` (login 200, CSP com nonce, HSTS, X-Request-Id, HTTP→HTTPS 308, readiness, login do usuário de smoke).

Se o certificado não sair: DNS ainda não propagou, portas 80/443 bloqueadas (provedor ou `ufw`), ou limite do Let's Encrypt. Use `scripts/cliente.sh logs homolog caddy`.

## 5. Verificações de produto (15 min)

1. Entre como ADMIN (usuário e senha do `.env.prod`), **troque a senha** e remova `ADMIN_PASSWORD` do arquivo.
2. **"Esqueci minha senha"** com um e-mail real: o e-mail chega? (testa o SMTP; sem SMTP real, veja o Mailpit só no ambiente local.)
3. Crie um cliente, um pet **com foto** (testa o volume de uploads), um produto com lote, faça uma venda e cancele.
4. Abra o menu com um usuário VENDAS e um SERVICO: só aparece o que o papel e o pacote permitem.
5. `GET /api/v1/estoque/reconciliacao` (ADMIN) → `consistente: true`.

## 6. Backup, restore e cópia off-site

```bash
scripts/cliente.sh backup homolog
scripts/cliente.sh restore-test homolog        # esperado: PASS em todas as tabelas e na versão do Flyway
# off-site: configure BACKUP_REMOTO no .env.prod (rclone), depois
scripts/cliente.sh backup homolog              # esperado: "Cópia off-site ok"
```

Cron (como `petconnect`, `crontab -e`):

```cron
30 2 * * *  /opt/petconnect/scripts/cliente.sh backup homolog       >> /home/petconnect/logs/petconnect-homolog-backup.log 2>&1
0 4 * * 1   /opt/petconnect/scripts/cliente.sh restore-test homolog >> /home/petconnect/logs/petconnect-homolog-restore.log 2>&1
*/5 * * * * /opt/petconnect/scripts/cliente.sh monitorar homolog    >> /home/petconnect/logs/petconnect-homolog-monitor.log 2>&1
```

Monitoração: defina `ALERTA_WEBHOOK_URL` e **pare o web de propósito** (`scripts/cliente.sh parar homolog`; depois `subir`) para ver o alerta chegar.

## 7. Ensaio de desastre (#40)

Com o relógio ligado, restaure **em paralelo**: numa **segunda VPS** descartável (ou num projeto Compose novo e identificado, ex.: `homolog-restore`) e siga o [plano de recuperação](08_PLANO_RECUPERACAO.md) a partir do backup **off-site**, sem tocar nos volumes da origem. Apagar volumes (`down -v`) ou a VPS só com o alvo exato anotado e a aprovação do dono. Confira também que um backup de **outra** instalação é recusado e que arquivo adulterado/chave errada falham. Anote o tempo total até o `smoke` passar (meta RTO 4 h) e o que se perdeu desde o último backup (meta RPO 24 h).

## 8. Encerramento e registro

- [ ] Preencha o quadro abaixo e cole nas issues #38 e #40 (e #39 se o deploy por cliente foi testado). **Não** use para a #37 (canário real) nem para #10, #41 e #46.
- [ ] **Destrua a VPS** e remova o registro DNS de teste; revogue o token do GHCR e a credencial off-site usados.
- [ ] Corrija o que o ensaio revelou (script, documentação, compose) **antes** do cliente piloto.

| Verificação | Resultado (PASS/FAIL) | Observação / tempo |
|---|---|---|
| `docker pull` das imagens do GHCR | | |
| Certificado Let's Encrypt emitido | | |
| `cliente.sh smoke` | | |
| E-mail de recuperação chegou (SMTP real) | | |
| Upload e leitura de foto | | |
| `restore-test` | | |
| Cópia off-site | | |
| Alerta do `monitorar` chegou | | |
| Ensaio de desastre (tempo / perda) | | |
