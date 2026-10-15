# Oferta comercial — módulos, pacotes e limites (etapa 09)

Base: [catálogo validado](../implementacao/06_CATALOGO_MODULOS.md), enforcement testado na [etapa 07](../implementacao/07_AUTORIZACAO_MODULAR.md) e homologação por pacote no [relatório de go-live](../implementacao/09_RELATORIO_GO_LIVE.md). **Só vende-se o que está marcado "pronto".** Contrato, preço e faturamento ficam fora do sistema.

## Pacotes

| Pacote | Situação | Módulos | Entrega |
|---|---|---|---|
| **PDV** | pronto | produtos, estoque, caixa, pdv, relatórios | balcão e caixa, baixa de estoque (com validade/FEFO quando o produto controla lote), estorno de venda, relatórios de vendas, estoque e caixas |
| **Pet Shop** | pronto | clientes, produtos, estoque, lotes, caixa, pdv, compras, financeiro, pets, agenda, relatórios | tudo do PDV + cadastro de clientes e pets, compras e entrada de mercadoria, contas a pagar e fluxo, agenda de banho/tosa/serviços, controle de lote e validade (opcional por produto) |
| **Clínica** | **parcial** | clientes, pets, clinica, agenda, caixa, financeiro, relatórios (+ produtos, estoque, lotes se a vacina baixar estoque) | agenda, atendimento de vacinação, carteira de vacinação e histórico de consultas. **Vender somente como "agenda e carteira de vacinação"** |
| **Agropecuária** | **não pronta** | clientes, produtos, estoque, lotes, caixa, pdv, compras, financeiro, relatórios | a parte técnica de lote/validade/FEFO/compras funciona, mas **falta venda a granel e quantidade decimal**; não vender como pronto |
| **Completo** | pronto | os 12 módulos | todos os módulos existentes (a parte clínica continua parcial) |

## Dependências entre módulos

`estoque` exige `produtos` · `lotes` exige `estoque` · `pdv` exige `produtos` e `caixa` · `compras` exige `produtos` · `financeiro` exige `caixa` · `pets` exige `clientes` · `clinica` exige `pets` · `agenda` exige `pets` e `caixa`. Lista inválida **impede a instalação de subir** (não existe "meio contratado").

## Incluir e retirar módulos

- **Incluir:** o suporte altera a lista de módulos, o sistema valida dependências, reinicia e roda o smoke test; menus e APIs liberam em seguida, **sem migração de dados**. Fica registrado em histórico e auditoria.
- **Retirar:** **nunca apaga dado**. É recusado se houver pendência (caixa aberto, venda recente cancelável, conta a pagar aberta, pedido de compra, agendamento ativo, produto com controle de lote, vacinação ativa). Resolvida a pendência, retira-se; recolocar o módulo traz tudo de volta.
- Independentemente dos módulos, **o núcleo continua**: venda baixa o estoque pelo FEFO, lança a receita e registra auditoria mesmo sem as telas gerenciais correspondentes.

## Isolamento e dados

Cada cliente tem **a própria instalação**: banco, arquivos (fotos), backups, logs e segredos separados; nada é compartilhado entre clientes. Backups cifrados, com teste de restauração. Regras de privacidade (LGPD): papéis de controlador/operador e retenção **dependem de contrato revisado juridicamente** (não definido pelo sistema).

## O que NÃO está contratado nem existe

Venda a granel / quantidade decimal, venda a prazo (fiado) e contas a receber, nota fiscal (NF-e/NFC-e), prontuário completo / prescrição / exames / internação, transferência entre locais e inventário formal, multiempresa dentro de uma instalação, licença assinada e cobrança automática, notificações ao tutor, modo offline, pacote de insumos agrícolas.
