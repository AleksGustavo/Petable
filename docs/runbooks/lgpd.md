# LGPD — operação

## Inventário (onde há dado pessoal)

| Onde | Dados | Base legal |
|---|---|---|
| `cliente` + `endereco` | nome, CPF, nascimento, telefone, e-mail, endereço, observações | execução de contrato |
| `pet`, `carteira_vacina`, `historico_consulta` | dados do animal; nome do veterinário | execução de contrato |
| `usuario`, `funcionario` | username, e-mail, nome, CPF, nascimento, sexo, telefone | contrato de trabalho / obrigação legal |
| `fornecedor` | contato, e-mail e telefone comerciais | contrato |
| Auditoria (Mongo) | username e ids (sem conteúdo pessoal) | legítimo interesse (segurança) / obrigação legal |
| Backups | tudo acima, cifrado | mesmas bases |
| Logs | mascarados (CPF, e-mail, telefone, tokens); log do Caddy com IP truncado e sem busca | legítimo interesse (segurança) |

## Pedido do titular (cliente)

Na página do cliente, o **ADMIN** tem o cartão "Privacidade (LGPD)":
- **Exportar dados (JSON):** cliente, endereço, pets com vacinas, consultas e agendamentos, e vendas. Atende acesso e portabilidade. Fica auditado como `CLIENTE_DADOS_EXPORTADOS`.
- **Anonimizar:** atende a eliminação. É irreversível, pede motivo e a confirmação `ANONIMIZAR`.
  - O que muda: nome → `Cliente anonimizado #id`; CPF, e-mail, nascimento e observações removidos; telefone → marcador `0…`; endereço desvinculado e limpo; observações dos pets removidas; cliente inativo.
  - O que se mantém: vendas e atendimentos, sem identificar a pessoa, porque são registro fiscal/contábil.
  - Fica auditado como `CLIENTE_ANONIMIZADO` (só o motivo).
- Os **backups** antigos ainda contêm os dados até expirarem pela retenção (no máximo 6 meses). Informe isso ao titular.

Prazo de resposta ao titular: até 15 dias (art. 19, II).

## Retenção

- **Auditoria:** sem TTL. Guardada pelo prazo fiscal mínimo de 5 anos, com expurgo manual anual do que tiver mais de 5 anos, com aprovação do dono:
  ```bash
  docker compose -f compose.prod.yaml --env-file .env.prod exec mongo mongosh -u petconnect -p --authenticationDatabase admin petconnect_web \
    --eval 'db.auditoria.deleteMany({ ocorridoEm: { $lt: new Date(Date.now() - 5*365*24*3600*1000) } })'
  ```
  Confira antes o nome da coleção (`db.getCollectionNames()`) e faça backup.
- **Clientes inativos** há mais de 5 anos, sem obrigação de guarda: avaliar a anonimização anualmente.

## Script legado com dados reais

Scripts de inserção do sistema PHP antigo, com **dados pessoais declarados como reais** (issue #2), estão no histórico do git. Situação, atualizada em 2026-10-19:

- O arquivo foi **removido da árvore atual**; dumps, backups, chaves e relatórios de scanner entraram no `.gitignore`; o workflow `Segredos` (gitleaks + `scripts/verificar-dados-sensiveis.sh`) bloqueia novas ocorrências em PRs.
- O **histórico** continua com os dados até a reescrita. O inventário, o ensaio feito, as refs afetadas e o procedimento estão em [`docs/seguranca/RELATORIO_HISTORICO_DADOS_PESSOAIS.md`](../seguranca/RELATORIO_HISTORICO_DADOS_PESSOAIS.md). Publicar a reescrita (force-push) exige **autorização específica do dono** e aviso aos colaboradores.
- Reescrever não remove `refs/pull/*`, caches nem clones: isso exige o suporte do GitHub.
- Tornar o repositório privado é contenção, não limpeza, e não revoga cópias já feitas.
- Rotacionar credenciais só se for confirmado o uso real (o relatório lista o que foi encontrado).
- A comunicação a titulares e à ANPD é decisão do responsável jurídico, não da automação.
