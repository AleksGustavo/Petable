# Política de segurança

## Como relatar uma vulnerabilidade

**Não abra uma issue pública** para falhas de segurança nem para vazamento de dados pessoais.

Use o relato privado do GitHub: aba **Security → Report a vulnerability** deste repositório
(Security Advisories). Descreva:

- o que foi encontrado e onde (rota, tela, arquivo);
- como reproduzir, de preferência com passos curtos;
- o impacto que você imagina (que dados ou ações ficam expostos).

**Não inclua dados pessoais reais** no relato: use dados fictícios.

## O que esperar

| Etapa | Prazo-alvo |
|---|---|
| Confirmação do recebimento | 3 dias úteis |
| Avaliação e classificação | 7 dias |
| Correção de falha crítica ou alta | 14 dias |
| Correção das demais | próxima versão |

## Versões que recebem correção

Apenas a versão mais recente da `main`. O projeto ainda não tem versões estáveis publicadas
(veja o [CHANGELOG](CHANGELOG.md)).

## Controles já em vigor

- JWT com revogação imediata (`token_version`), BCrypt custo 12 e política de senha.
- Limite de tentativas de login por conta e por IP; recuperação de senha com token de uso único.
- CSP com nonce, HSTS, cookie `httpOnly`/`Secure` e API não exposta diretamente à internet em produção.
- CodeQL, Trivy (CVE crítica corrigível quebra o CI), SBOM e Dependabot.
- Logs em JSON com CPF, e-mail, telefone e tokens mascarados.

Detalhes operacionais: [docs/producao.md](docs/producao.md) e [docs/runbooks/incidente.md](docs/runbooks/incidente.md).
