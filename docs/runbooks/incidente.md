# Incidente de segurança

Exemplos: acesso indevido, vazamento de dados, servidor comprometido, credencial exposta.

## 1. Conter (primeira hora)

- Credencial exposta → [rotacao-segredos.md](rotacao-segredos.md) imediatamente. Trocar o `JWT_SECRET` derruba todas as sessões.
- Conta suspeita → desative em **Usuários**. As sessões dela caem na hora.
- Servidor comprometido → tire do ar (`docker compose ... stop caddy`), preserve os logs, não apague nada.

## 2. Avaliar

Levante:
- quais dados;
- de quantos titulares;
- desde quando;
- como aconteceu.

Fontes:
- logs da API (JSON, por `requestId`);
- auditoria (`GET /api/v1/auditoria`, ADMIN);
- log de acesso do Caddy.

## 3. Registrar

Abra um registro com:
- linha do tempo;
- dados afetados;
- medidas tomadas;
- responsáveis.

Guarde por 5 anos.

## 4. Comunicar (LGPD, art. 48)

Havendo **risco ou dano relevante** aos titulares, comunique à ANPD e aos titulares **em até 3 dias úteis** do conhecimento. A comunicação diz:
- a natureza dos dados;
- os titulares envolvidos;
- as medidas técnicas;
- os riscos;
- as medidas para mitigar.

## 5. Corrigir e aprender

Aplique a correção, faça deploy e registre a lição. Se for o caso, adicione um teste ou alerta que teria pegado o problema.
