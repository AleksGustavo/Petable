# Go-live por cliente: checklist assinável e plano de canário (etapa 09)

Um por cliente. Cada linha tem **responsável, data e evidência**; só assina quem executou. Itens `[auto]` têm comando; `[manual]` dependem de pessoa. **Nada aqui autoriza deploy, DNS, ligar flag de lotes em produção ou migração destrutiva sem a autorização explícita do dono.**

Cliente: ____________  Instalação (`INSTALACAO_ID`): ____________  Pacote/módulos: ____________  Tag: ____________

## A. Qualidade

| # | Item | Como | Resp. | Data | Evidência / assinatura |
|---|---|---|---|---|---|
| A1 | CI verde na tag da release (backend, frontend, e2e, segurança, CodeQL) | `gh run list` / PR | | | |
| A2 | Homologação do pacote contratado passou (`Pacote*IT`) | [relatório de go-live](../implementacao/09_RELATORIO_GO_LIVE.md) | | | |
| A3 | Testes negativos (módulo, papel, token, isolamento) passaram | idem, seção 3 | | | |
| A4 | Reconciliação do estoque `consistente: true` após a carga inicial | `GET /api/v1/estoque/reconciliacao` (ADMIN) | | | |

## B. Banco e migrations

| # | Item | Como | Resp. | Data | Evidência |
|---|---|---|---|---|---|
| B1 | Backup antes de subir a versão | `scripts/cliente.sh backup <id>` | | | |
| B2 | Migrations aplicadas até a versão esperada (V13 ou superior) e sem falha | log da API / `flyway_schema_history` | | | |
| B3 | Migração do legado, se houver, executada com dry-run analisado | [migracao-legado.md](../migracao-legado.md) | | | |

## C. Operação

| # | Item | Como | Resp. | Data | Evidência |
|---|---|---|---|---|---|
| C1 | `cliente.sh validar` e `isolamento` PASS | scripts | | | |
| C2 | Smoke test PASS | `scripts/cliente.sh smoke <id>` | | | |
| C3 | Backup diário agendado + `restore-test` PASS | cron + script | | | |
| C4 | Cópia off-site configurada **por cliente** e testada | `BACKUP_REMOTO` | | | |
| C5 | Monitoração: alerta de API/Web/backup/disco/5xx chegando ao responsável | `cliente.sh monitorar <id>` + parar o web de propósito | | | |
| C6 | Log de auditoria registrando (`MODULOS_ALTERADOS`, vendas, ajustes) | consulta de auditoria | | | |

## D. Segurança e privacidade

| # | Item | Como | Resp. | Data | Evidência |
|---|---|---|---|---|---|
| D1 | [Checklist de segurança e privacidade](08_CHECKLIST_SEGURANCA_PRIVACIDADE.md) completo | documento | | | |
| D2 | Contrato com controlador/operador, retenção e encarregado **revisado juridicamente** | jurídico do cliente/fornecedor | | | |
| D3 | ADMIN trocou a senha inicial; `ADMIN_PASSWORD` removido do `.env.prod` | manual | | | |

## E. Suporte

| # | Item | Como | Resp. | Data | Evidência |
|---|---|---|---|---|---|
| E1 | Usuários do cliente treinados ([guia](09_GUIA_SUPORTE_E_TREINAMENTO.md)) | sessão registrada | | | |
| E2 | Canal e horário de suporte combinados; contato de incidente definido | contrato | | | |
| E3 | Cliente recebeu a [oferta](09_OFERTA_COMERCIAL.md) com o que **não** está contratado | e-mail/ata | | | |

Assinaturas: Operador ________  Dono do produto ________  Cliente ________  Data ________

---

## Plano de canário (primeiro cliente piloto)

**Escolha do piloto:** um cliente de baixo risco, pacote **PDV** ou **Pet Shop** (os "prontos"), com disposição para reportar problemas e com acesso rápido ao suporte. Não use Clínica (parcial) nem Agropecuária (não pronta) como piloto.

**Janela:** 2 semanas. Dias 1–3 com acompanhamento diário; depois, a cada 2 dias.

| O que acompanhar | Como medir | Limite para **parar** e voltar |
|---|---|---|
| Divergência de estoque | `GET /api/v1/estoque/reconciliacao` diário (e após cada compra/inventário) | qualquer `consistente: false` sem explicação |
| Falha de transação | respostas 5xx e 409 inesperados nos logs da API (`X-Request-Id`) | 5xx em venda/cancelamento/recebimento > 0 em um dia |
| Lentidão do FEFO | tempo de resposta da venda (percentil alto nos logs do Caddy/API); referência de homologação em `LoteDesempenhoIT` | venda perceptivelmente lenta para o caixa, repetidamente |
| Alertas de lote/validade | `/estoque/alertas` coerente com o físico | lote vencido vendido (deve ser impossível) |
| Rotas negadas | 403 `MODULO_INDISPONIVEL` nos logs de quem usa a tela | usuário legítimo barrado em recurso contratado |
| Backup/restore | `restore-test` semanal PASS | falha de backup ou restore |
| Satisfação | relato do dono/caixa | bloqueio operacional do dia a dia |

**Rollback operacional (sem apagar dado do cliente):**
1. Problema de versão: [rollback.md](../runbooks/rollback.md) (volta a `TAG`; banco só por restore, com backup feito antes).
2. Problema de módulo: `scripts/cliente.sh modulos <id> <lista anterior>` (o script já reverte sozinho se a API recusar).
3. Problema de dado: `CONFIRMAR=RESTAURAR scripts/cliente.sh restore <id> <backup>` (substitui só a instalação do cliente; recusa backup de outro cliente).
4. Fallback de negócio: o cliente mantém o processo anterior (papel/planilha/sistema antigo, **não desligado** durante o canário) até o aceite final.

**Aceite do canário:** 2 semanas sem parada pelos limites acima, reconciliação consistente em todos os dias medidos, restore-test PASS, e assinatura do cliente. Só então o pacote entra no catálogo comercial. **Release/tag e deploy exigem autorização explícita do dono.**
