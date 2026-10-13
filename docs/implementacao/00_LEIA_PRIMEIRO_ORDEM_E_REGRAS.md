# PetConnect ERP — Plano mestre de execução (Claude)

**Objetivo:** entregar estoque com rastreabilidade robusta por lote/validade e, somente depois, comercializar o ERP completo por módulos habilitados individualmente por instalação/cliente. Destinos: pet shops, agropecuárias e clínicas veterinárias.

**Contexto conhecido, a validar no código:** ERP web Spring/Next, repositório `AleksGustavo/PetConnect-Web`, branch de trabalho anteriormente citada `feat/producao-operacao`; lote de vacina já existe em outro contexto, mas lote de estoque ainda não. NÃO presumir que branch, classes, migrations ou infraestrutura ainda estejam iguais. Confira antes de editar.

## Ordem obrigatória

| Ordem | Arquivo | Resultado | Dependência |
|---|---|---|---|
| 01 | `01_DIAGNOSTICO_E_CONTRATOS.md` | mapa real do código, riscos, regras acordadas | nenhuma |
| 02 | `02_DOMINIO_E_BANCO_LOTES.md` | modelo e migrations seguras | 01 |
| 03 | `03_MOVIMENTACOES_FEFO_E_CONCORRENCIA.md` | entradas, baixas, devoluções, correções atômicas | 02 |
| 04 | `04_TELAS_ALERTAS_E_OPERACAO.md` | interface e alertas operacionais | 03 |
| 05 | `05_TESTES_LOTES_HOMOLOGACAO.md` | testes e liberação controlada de lotes | 04 |
| 06 | `06_CATALOGO_DE_MODULOS_E_DEPENDENCIAS.md` | catálogo comercial/técnico de módulos | 05 |
| 07 | `07_AUTORIZACAO_E_ISOLAMENTO_MODULAR.md` | API, telas e permissões de módulo | 06 |
| 08 | `08_PROVISIONAMENTO_E_OPERACAO_CLIENTES.md` | configuração, isolamento, backups, implantação | 07 |
| 09 | `09_TESTES_MODULARES_E_GO_LIVE.md` | matriz de cenários e liberação comercial | 08 |

**Regra de execução:** enviar **somente um arquivo numerado por vez** ao Claude, junto do 00 na primeira rodada. Só avançar após critérios de aceite, testes e evidências da etapa. Não delegar dez etapas juntas. Claude deve escrever código; este material especifica, não declara nada implementado.

## Instruções fixas ao Claude (economia de tokens)

1. Examine apenas os diretórios/classes afetados e histórico das migrations; reutilize padrões existentes. Não reestruture o monorepo sem necessidade.
2. Antes de codificar, responda em no máximo **12 linhas**: arquivos previstos, decisões necessárias e risco de regressão. Quando não houver bloqueador, implemente diretamente.
3. **Não invente nomes de arquivos, entidades, endpoints, stack, permissões ou comandos:** descubra no repositório e documente equivalências.
4. Diff mínimo; preserve regras de PDV, caixa, compras, estoque, financeiro, auditoria, agenda e clínica. Evite dependências novas se não indispensáveis.
5. Migration sempre aditiva, reversão/mitigação descrita, **sem apagar histórico ou dados**. Toda alteração de saldo deve ser transacional, auditável e idempotente quando aplicável.
6. Não substituir autorização de backend por menu oculto, nem considerar HTTP 404 como proteção suficiente. Sessões e tokens jamais podem ampliar módulos contratados.
7. Nunca fazer merge na `main`, deploy em produção, alteração de DNS, exclusão de dados ou migração destrutiva sem autorização explícita.
8. Ao concluir, responder em no máximo **25 linhas** com: resumo, arquivos, migrations, testes executados e resultados, pendências/riscos e próximo arquivo. Não alegar teste que não rodou.
9. Caso faltarem decisões comerciais, usar feature flag segura (desligada por padrão), listar a escolha e parar apenas no ponto bloqueado.
10. Criar ou atualizar dentro do repositório `docs/implementacao/STATUS_LOTES_MODULOS.md` com checklist e evidências, para retomada em outra sessão gastando poucos tokens.

## Princípios de produto

- **Produto único:** código e banco estruturado para todos os módulos; cada instalação só expõe funcionalidades contratadas.
- **Isolamento inicial recomendado:** uma instalação lógica por cliente, credenciais e banco de dados isolados; sem multi-tenancy por linhas neste ciclo.
- **Dependências técnicas não equivalem a direito de uso:** PDV pode depender do núcleo de estoque, mas isso não concede acesso a telas administrativas de estoque não contratadas.
- **Controle de lote opt-in por produto** e com validade por lote; suportar produtos sem lote e produtos com lote sem vencimento **somente se política explícita permitir**.
- **Controle econômico:** manter o estado completo habilitável depois, sem migrations por ativação de módulo; migrations gerais ainda são necessárias quando o software evolui.

## Entrega da etapa

**Modelo de retorno obrigatório**: `Etapa N | concluída/parcial/bloqueada | resumo | evidências (comandos e resultado) | riscos | arquivos alterados | próximo passo`. Se houver decisão bloqueadora, formular no máximo 3 perguntas curtas.
