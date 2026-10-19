# Relatório sanitizado — dados pessoais e segredos no Git (issue #2)

Data: **2026-10-19**. Nenhum valor, linha ou dado pessoal aparece aqui: só caminhos, contagens e identificadores de commit.
Evidências com material sensível (relatórios brutos do scanner) ficam **fora do Git**, na máquina do responsável pela execução; este documento não aponta para elas por link.

## 1. Inventário

Escopo: todas as refs do repositório (30 branches, 1 tag, 39 refs de PR) e a árvore atual. Ferramentas: inspeção de objetos Git, `gitleaks` 8.30.1 com `--redact`, contagem de padrões (CPF, e-mail, telefone).

| ID | Categoria | Caminho (histórico) | Na árvore atual? | Perfil (contagens por arquivo) | Observação |
|---|---|---|---|---|---|
| A-01 | dado pessoal (declarado real na issue #2) | `Documentação/Banco de Dados/Scripts do Banco/pet_e_pet_DB - Script de Inserção de Dados.sql` e o mesmo arquivo movido para `docs/legacy/documentacao/Banco de Dados/Scripts do Banco/` (blob `f89f3a98`) | **SIM (em `main`) — removido nesta PR** | ~80 padrões de CPF, ~45 e-mails, ~89 telefones, 3 hashes | 2 commits tocam os 2 caminhos |
| A-02 | dado pessoal (provável) | `Documentação/Banco de Dados/Scripts do Banco/petshop_db - Script de inserção de dados.sql` (blob `f56c6027`) | não | ~75 CPF, ~40 e-mails, ~84 telefones | versão anterior do legado |
| A-03 | dado pessoal (provável) | `petshop_db.sql` (raiz) (blob `2d9bfe1c`) | não | ~77 CPF, ~40 e-mails, ~86 telefones | dump do sistema PHP |
| A-04 | dado pessoal (provável) | `Projeto/127_0_0_1.sql` (blob `29e362ee`) | não | idem A-03 | exportação do phpMyAdmin |
| A-05 | fixture sintética | `backend/src/test/resources/legacy/02-fixture-legado.sql` | sim | 6 CPF, 4 e-mails | criada pelo projeto na etapa 08 (commit `57dce0f`); permanece, na allowlist da barreira |
| A-06 | revisão humana | `Projeto/Screenshots/` (87 imagens), `Projeto/uploads/fotos_pets/` (1) | não | — | podem exibir dados reais; **só o dono sabe**; o scanner não vê imagens |
| A-07 | segredo potencial, descartado | `Projeto/conexao.php` e variantes (`cliente/`, `funcionario/`, `pet/`, `produtos/`) | não | senha do MySQL local do XAMPP vazia | credencial de desenvolvimento local, sem valor |
| A-08 | senha de exemplo do legado | `docs/migracao-legado.md`, `docs/banco-de-dados.md`, `docs/next-stage/08-prompt-chatgpt.md`, `MigracaoLegadoIT.java` | sim | menção à senha `admin123` do usuário `admin` do legado | não é credencial de instalação: `AdminBootstrap` recusa senha fraca (PoliticaSenha); **confirmar que nenhum ambiente usou** |
| A-09 | falso positivo (gitleaks) | `IntegrationTest.java` (senha de ADMIN só de teste), `MascaramentoLogsTest.java` (JWT sintético), `docs/producao.md` (`localhost:<porta>`) | sim | 3 achados | conferidos; allowlist por caminho em `.gitleaks.toml` |

Resultado do `gitleaks` no histórico completo: **3 achados, todos falsos positivos (A-09); nenhum segredo confirmado**.
Limite: o gitleaks não identifica dado pessoal (nomes, CPF, endereço). Para isso há a barreira `scripts/verificar-dados-sensiveis.sh`, que só reconhece **CPF formatado** (`000.000.000-00`); CPF sem pontuação, nomes e endereços exigem revisão humana.

Colaboradores nos commits: `Aleksander Gustavo`, `marcos22-s`, `Wanderson` e `dependabot[bot]`. A issue cita "Everton, Marquinhos e Wanderson": **não há branch `Everton` no remoto**; confirme a lista real com o dono antes de coordenar.

Visibilidade em 2026-10-19 antes da contenção: **PÚBLICA** (tornada **PRIVADA** no mesmo dia, com autorização do dono; ver seção 9). Tornar privado não revoga cópias já feitas e não é limpeza de histórico. Forks: 0.

## 2. Árvore atual (nesta PR)

- A-01 removido da árvore (`git rm`). Os outros SQL do legado em `docs/legacy/` não têm padrão de dado pessoal (procedures/DDL) e ficam.
- `.gitignore` bloqueia dumps (`*.dump`, `*.bak`, `*.sql.*`, `petshop_db.sql`, `Projeto/*.sql`), chaves e relatórios de scanner; fixtures de `src/test/resources` continuam permitidas.
- Workflow `Segredos` (todo PR e push na `main`): gitleaks com checksum fixado + barreira de CPF.
- **Não resolve o histórico**: o arquivo continua alcançável pelos commits antigos até a reescrita da seção 5.

## 3. Credenciais

| Item | Serviço / consumidor | Situação |
|---|---|---|
| `admin123` (A-08) | usuário `admin` do sistema legado PHP | sistema legado desativado? **Confirmar com o dono.** Se algum ambiente do Petable usou essa senha, rotacionar; senha fraca já é recusada pelo código |
| senha MySQL local vazia (A-07) | XAMPP local de desenvolvimento | sem rotação necessária |
| hashes de senha nos dumps (A-01..A-04) | contas do sistema legado | tratar junto com A-08: se alguma pessoa reutiliza essa senha em outro serviço, o risco é dela; avisar quando houver o comunicado |

Sequência se for confirmado algum uso real: nova credencial com privilégio mínimo → atualizar consumidores → testar → revogar a antiga → confirmar com o provedor. Nenhum valor deve ser colado em chat, issue ou PR.

## 4. Ensaio da limpeza (sem push, 2026-10-19)

Em clone espelho descartável do remoto, fora do repositório, com `git filter-repo` (versão `a40bce548d2c`):

```bash
git clone --mirror https://github.com/AleksGustavo/Petable.git espelho.git
git clone --mirror espelho.git limpo.git && cd limpo.git
printf '%s\n' \
  'Documentação/Banco de Dados/Scripts do Banco/pet_e_pet_DB - Script de Inserção de Dados.sql' \
  'docs/legacy/documentacao/Banco de Dados/Scripts do Banco/pet_e_pet_DB - Script de Inserção de Dados.sql' \
  'Documentação/Banco de Dados/Scripts do Banco/petshop_db - Script de inserção de dados.sql' \
  'petshop_db.sql' 'Projeto/127_0_0_1.sql' > ../caminhos-a-remover.txt
git filter-repo --invert-paths --paths-from-file ../caminhos-a-remover.txt --force
```

Resultados:

| Verificação | Resultado |
|---|---|
| Blobs A-01..A-04 | não existem mais (`git cat-file -e` falha) |
| Árvore da `main` antes × depois | difere **só** pelo arquivo de A-01 (349 linhas removidas) |
| `git fsck` | sem erros |
| `gitleaks` no repositório reescrito | 0 achados |
| Padrão de CPF em `.sql/.dump/.csv` do histórico reescrito | só a fixture A-05 |
| Refs alteradas | 29 branches + a tag `v0.1.0`; `Marquinhos` não muda (não continha os arquivos) |

Não foi feita a limpeza de A-06 (imagens), porque depende de o dono dizer se mostram dados reais; se mostrarem, acrescente `Projeto/Screenshots/` e `Projeto/uploads/` à lista e repita o ensaio.

Os hashes das refs mudam por completo (a reescrita atinge os ancestrais): `main` `12bbf0d26e` → `b1641ff0c8` **neste ensaio**; o valor final será recalculado na janela, a partir do remoto naquele momento.

### Refs a atualizar (ensaio de 2026-10-19)

```
Aleksander                               29c2974b5f -> 73a1150521
Wanderson                                71e18c53b8 -> 190ff8aa1e
docs/protecao-main                       498d86e230 -> acc1db1a33
feat/ci-compose-e2e                      999465d029 -> 5b34600ed6
feat/go-live-etapa-09                    b1d39ff7e1 -> 8bd25a5251
feat/lotes-01-diagnostico                0ad1b09814 -> 76bb73cc61
feat/lotes-etapa-02                      267470cf92 -> e6070ac0b7
feat/lotes-etapa-03                      2c9f1ff417 -> eac9411fe9
feat/lotes-etapa-04                      9f8ee6b8e4 -> e6c573df1a
feat/lotes-etapa-04b-clinica             83f5fadd40 -> 427c14bc86
feat/lotes-etapa-05                      3103c7a0a6 -> f02df1ad2d
feat/modulos-etapa-06                    15920aeb76 -> 3d0680577b
feat/modulos-etapa-07                    978a8a5687 -> 672bf14954
feat/producao-operacao                   62bec58d1d -> 22dba1712e
feat/provisionamento-etapa-08            d1f06c440b -> 6006d9950a
main                                     12bbf0d26e -> b1641ff0c8
tag v0.1.0                               88826ebc5e -> 01cc9a302c
13 branches dependabot/* (PRs #15 a #27) — o Dependabot as recria/rebaseia
```

## 5. Procedimento de publicação (após autorização)

**Pré-condições (todas):** autorização específica do dono para reescrever e publicar; janela de congelamento (ninguém faz push); backup restrito feito; comunicado enviado **pelo dono** (texto na seção 7).

1. **Congelar:** avisar quem tem acesso de escrita; conferir `git ls-remote` e comparar os SHAs com a tabela. Se alguém enviou commit novo, **refazer o ensaio**; não sobrescrever.
2. **Backup de recuperação:** o espelho `espelho.git` (contém os dados removidos) fica **cifrado, fora do repositório e de pastas compartilhadas**, com um responsável e prazo de retenção definidos pelo dono; apague quando o prazo vencer. Nunca vira artifact de CI.
3. **Reescrever** a partir de um espelho novo do remoto (comandos da seção 4).
4. **Publicar só as refs revisadas**, com proteção contra atualização concorrente, uma a uma (sem `--mirror`, sem `--force` genérico):
   ```bash
   git push --force-with-lease=refs/heads/<ref>:<sha-antigo> origin <novo-sha>:refs/heads/<ref>
   ```
   Se existir proteção na `main`, abrir exceção **só** para esta operação, registrar e restaurar logo depois.
5. **Validar em clone novo** independente: `git clone`, `gitleaks git --redact`, ausência dos caminhos, `fsck`, build e testes pertinentes.
6. **Tratar as sobras:**
   - `refs/pull/*` (39) e `refs/github-services/*` **não são reescrevíveis por push** e continuam expondo o conteúdo antigo. Pedir ao GitHub Support a remoção de objetos em cache e das refs de PR (texto na seção 6).
   - Os 13 PRs do Dependabot: após o push das branches, o Dependabot rebaseia; se algum ficar divergente, `@dependabot recreate`.
   - Clones antigos, forks (0 hoje) e o `.git` de quem clonou continuam com os dados.
   - Worktrees/branches **locais não publicadas** do dono (`feat/pdv-entrada-etapa-10`, `feat/petable-etapa-11`) precisam ser rebaseadas sobre a nova `main`: `git rebase --onto <nova-main> <main-antiga> <branch>`.
7. **Registrar** quando e por quem, quais credenciais foram trocadas, e a retenção do backup.

## 6. Texto para o GitHub Support (rascunho, não enviado)

> Repository `AleksGustavo/Petable` (public). We removed files containing personal data from history using git filter-repo and force-pushed the rewritten branches/tags. Please run garbage collection on the repository network, remove the cached views of the old commits and delete the read-only `refs/pull/*` refs that still point to commits containing the removed blobs (list available on request, SHAs only). Removed paths and old commit SHAs are in the attached sanitized list.

## 7. Texto de coordenação (rascunho, não enviado)

> Assunto: Petable — reescrita do histórico do Git em [data/hora]
> Vamos remover do histórico um arquivo com dados pessoais do sistema antigo. Isso muda os commits de todas as branches. Pare de enviar commits até avisarmos. Depois: (1) **não** faça merge/pull da branch antiga; (2) apague seu clone e clone de novo; (3) se você tinha trabalho não enviado, salve os arquivos fora do clone e reaplique-os numa branch nova criada a partir da nova `main` (cherry-pick ou cópia). Não reenvie o histórico antigo.

## 8. Critério de aceite da issue #2 — situação

| Critério | Estado |
|---|---|
| Achados inventariados | **feito** (seção 1); A-06 depende do dono |
| Árvore atual tratada | **feito nesta PR** (A-01 removido; prevenção ativa) |
| Segredos confirmados rotacionados | nenhum confirmado; A-08 depende de confirmação do dono |
| Histórico publicado validado em clone novo | **feito em 2026-10-19** (seção 9) |
| Cópias residuais tratadas | **PENDENTE**: `refs/pull/*` e caches (GitHub Support, pedido do dono) e clones locais |

**A issue #2 permanece aberta.** Avaliação de comunicação a titulares ou à ANPD é do responsável competente; este documento não faz essa conclusão.

## 9. Execução (2026-10-19)

Autorizações do dono, dadas em chat no mesmo dia: (1) tornar o repositório privado; (2) publicar a reescrita, com colaboradores avisados e congelamento por conta do dono; (3) A-06 (imagens) **fora** da limpeza: o dono declarou que são telas com dados fictícios.

| Passo | Resultado |
|---|---|
| Visibilidade | PRIVADA (`gh repo edit --visibility private`) |
| Remoto antes de publicar | idêntico ao ensaio, exceto a branch `chore/seguranca-ci-homologacao`, criada por esta execução |
| Refazer a reescrita em espelho novo | 31 refs alteradas, `Marquinhos` inalterada; a árvore de **todas** as refs difere do original só pelos 5 caminhos aprovados |
| Backup de recuperação | espelho original cifrado (AES-256, PBKDF2) em `C:\Users\Aleksander\petable-backup-historico\`; senha em `C:\Users\Aleksander\.petable-backup-senha.txt`, **a mover para um gerenciador de senhas e a apagar**; decifragem testada (chave errada falha). Contém os dados removidos: retenção e acesso são do dono |
| Publicação | 31 de 31 refs com `--force-with-lease` e SHA antigo esperado, sem `--mirror`; 0 falhas |
| Clone novo independente | `main` = `b1641ff0c8` (igual ao ensaio); 0 refs divergentes; `git fsck` ok; gitleaks 0 achados; nenhum dos 5 caminhos alcançável; único padrão de CPF restante: fixture A-05 |
| Branches locais não publicadas | `feat/pdv-entrada-etapa-10` (3 commits) e `feat/petable-etapa-11` (6 commits) rebaseadas sobre a nova `main` sem conflito; a PR #48 e o checkout `wt-seg` foram alinhados |

### Efeitos colaterais conhecidos

- O push da `main` e da tag `v0.1.0` **disparou os workflows de push**. O de **Entrega** republicou no GHCR `petconnect-web-api` e `petconnect-web-web` com as tags `main`, `0.1.0` e uma `sha-…` nova, construídas dos mesmos diretórios `backend/` e `frontend/` (que a reescrita não altera). Não há deploy automático nem environment, então nada foi implantado.
- Os commits de **todas as branches e da tag mudaram de hash**; assinaturas e referências por hash antigas deixam de valer.
- A PR #15 e as outras 12 do Dependabot continuam abertas apontando para as branches reescritas (a #15 passou de `e95e2ef` para `fc44bd2`).

### O que não foi possível ou ainda depende de terceiros

- **41 `refs/pull/*`** (e `refs/github-services/*`) continuam no remoto apontando para os commits antigos: só o GitHub Support remove. Texto pronto na seção 6; o pedido deve partir do dono.
- **Clones e checkouts locais antigos** continuam com os dados: o do dono em `...\PetConnect-Web` (branch `feat/producao-operacao`, com alterações não commitadas, que **não foram tocadas**) e quaisquer clones de colaboradores. Para limpar um clone local depois de alinhá-lo: `git fetch --prune origin`, atualizar/recriar as branches e então `git reflog expire --expire=now --all && git gc --prune=now` (apaga também a cópia local de recuperação: só depois de confirmar o backup cifrado).
- **Colaboradores:** o aviso e a recriação de clones/branches deles é do dono. Quem tinha trabalho não enviado deve reaplicá-lo (texto na seção 7).
- **Credenciais:** nada rotacionado, pois nenhum segredo real foi confirmado (seção 3).
