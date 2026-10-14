# Matriz de acesso: endpoint → módulo → papel (versionada)

> **Gerada pelo teste `MatrizAcessoIT`**; não edite à mão. Lê as rotas reais da API, o catálogo de módulos (`modulos/catalogo.json`), a lista de regras de URL (`RegrasDeAcesso`) e o `@PreAuthorize` de cada método. Para atualizar depois de uma mudança intencional: `MATRIZ_ATUALIZAR=true ./mvnw verify -Dit.test=MatrizAcessoIT` e revise o diff no PR.

**Como ler.** O acesso exige as DUAS camadas, nesta ordem: (1) o **módulo** está contratado na instalação (`PETCONNECT_MODULOS`), senão `403` com `codigo = MODULO_INDISPONIVEL` para qualquer papel; (2) o **papel** do usuário (coluna *Papéis*: regra de URL e `@PreAuthorize` juntas). `núcleo` está sempre ativo. `qualquer papel` = qualquer usuário logado. Relatórios e exportações dependem também do módulo de origem do tipo (`relatorios (+pdv)`). Sem token a resposta é `401` e nada é revelado.

Total: **136 rotas**.

| Módulo | Método | Rota | Papéis |
|---|---|---|---|
| nucleo | GET | `/api/v1/auditoria` | ADMIN |
| nucleo | POST | `/api/v1/auth/esqueci-senha` | pública |
| nucleo | POST | `/api/v1/auth/login` | pública |
| nucleo | GET | `/api/v1/auth/me` | qualquer papel |
| nucleo | POST | `/api/v1/auth/redefinir-senha` | pública |
| nucleo | GET | `/api/v1/estoque/configuracao` | qualquer papel |
| nucleo | GET | `/api/v1/funcionarios` | qualquer papel |
| nucleo | POST | `/api/v1/funcionarios` | ADMIN |
| nucleo | GET | `/api/v1/funcionarios/{id}` | ADMIN |
| nucleo | PUT | `/api/v1/funcionarios/{id}` | ADMIN |
| nucleo | GET | `/api/v1/me` | qualquer papel |
| nucleo | PUT | `/api/v1/me` | qualquer papel |
| nucleo | PUT | `/api/v1/me/funcionario` | qualquer papel |
| nucleo | POST | `/api/v1/me/trocar-senha` | qualquer papel |
| nucleo | GET | `/api/v1/modulos` | qualquer papel |
| nucleo | GET | `/api/v1/usuarios` | ADMIN |
| nucleo | POST | `/api/v1/usuarios` | ADMIN |
| nucleo | GET | `/api/v1/usuarios/{id}` | ADMIN |
| nucleo | PUT | `/api/v1/usuarios/{id}` | ADMIN |
| nucleo | PATCH | `/api/v1/usuarios/{id}/ativar` | ADMIN |
| nucleo | PATCH | `/api/v1/usuarios/{id}/desativar` | ADMIN |
| nucleo | PATCH | `/api/v1/usuarios/{id}/papel` | ADMIN |
| clientes | GET | `/api/v1/clientes` | qualquer papel |
| clientes | POST | `/api/v1/clientes` | ADMIN, VENDAS |
| clientes | DELETE | `/api/v1/clientes/{id}` | ADMIN, VENDAS |
| clientes | GET | `/api/v1/clientes/{id}` | qualquer papel |
| clientes | PUT | `/api/v1/clientes/{id}` | ADMIN, VENDAS |
| clientes | POST | `/api/v1/clientes/{id}/anonimizar` | ADMIN |
| clientes | GET | `/api/v1/clientes/{id}/dados-pessoais` | ADMIN |
| produtos | GET | `/api/v1/categorias-produto` | qualquer papel |
| produtos | GET | `/api/v1/produtos` | qualquer papel |
| produtos | POST | `/api/v1/produtos` | ADMIN, VENDAS |
| produtos | GET | `/api/v1/produtos/busca-rapida` | qualquer papel |
| produtos | GET | `/api/v1/produtos/{id}` | qualquer papel |
| produtos | PUT | `/api/v1/produtos/{id}` | ADMIN, VENDAS |
| produtos | PATCH | `/api/v1/produtos/{id}/ativar` | ADMIN |
| produtos | PATCH | `/api/v1/produtos/{id}/desativar` | ADMIN, VENDAS |
| estoque | GET | `/api/v1/estoque/reconciliacao` | ADMIN |
| estoque | GET | `/api/v1/produtos/resumo-estoque` | qualquer papel |
| estoque | POST | `/api/v1/produtos/{id}/ajustes-estoque` | ADMIN |
| estoque | GET | `/api/v1/produtos/{id}/movimentacoes` | qualquer papel |
| lotes | GET | `/api/v1/estoque/alertas` | ADMIN, VENDAS |
| lotes | GET | `/api/v1/lotes` | ADMIN, VENDAS |
| lotes | GET | `/api/v1/lotes/{id}/rastreio` | ADMIN, VENDAS |
| lotes | POST | `/api/v1/produtos/{id}/controle-lote` | ADMIN |
| lotes | POST | `/api/v1/produtos/{id}/controle-lote/desativar` | ADMIN |
| lotes | GET | `/api/v1/produtos/{id}/lotes` | ADMIN, VENDAS |
| lotes | POST | `/api/v1/produtos/{id}/lotes/{loteId}/bloquear` | ADMIN |
| lotes | POST | `/api/v1/produtos/{id}/lotes/{loteId}/desbloquear` | ADMIN |
| lotes | POST | `/api/v1/produtos/{id}/lotes/{loteId}/perdas` | ADMIN |
| lotes | POST | `/api/v1/produtos/{id}/lotes/{loteId}/vencimentos` | ADMIN |
| caixa | GET | `/api/v1/caixas` | ADMIN |
| caixa | POST | `/api/v1/caixas/abrir` | qualquer papel |
| caixa | GET | `/api/v1/caixas/atual` | qualquer papel |
| caixa | POST | `/api/v1/caixas/atual/fechar` | qualquer papel |
| caixa | GET | `/api/v1/caixas/atual/previa-fechamento` | qualquer papel |
| caixa | POST | `/api/v1/caixas/atual/sangrias` | qualquer papel |
| caixa | POST | `/api/v1/caixas/atual/suprimentos` | qualquer papel |
| caixa | GET | `/api/v1/caixas/resumo-atual` | qualquer papel |
| caixa | GET | `/api/v1/caixas/{id}` | qualquer papel |
| pdv | GET | `/api/v1/vendas` | ADMIN, VENDAS |
| pdv | POST | `/api/v1/vendas` | ADMIN, VENDAS |
| pdv | POST | `/api/v1/vendas/previa` | ADMIN, VENDAS |
| pdv | GET | `/api/v1/vendas/resumo-dia` | ADMIN, VENDAS |
| pdv | GET | `/api/v1/vendas/{id}` | ADMIN, VENDAS |
| pdv | POST | `/api/v1/vendas/{id}/cancelar` | ADMIN |
| compras | GET | `/api/v1/compras` | ADMIN, VENDAS |
| compras | POST | `/api/v1/compras` | ADMIN |
| compras | GET | `/api/v1/compras/{id}` | ADMIN, VENDAS |
| compras | POST | `/api/v1/compras/{id}/cancelar` | ADMIN |
| compras | POST | `/api/v1/compras/{id}/receber` | ADMIN |
| compras | GET | `/api/v1/fornecedores` | ADMIN, VENDAS |
| compras | POST | `/api/v1/fornecedores` | ADMIN |
| compras | GET | `/api/v1/fornecedores/opcoes` | ADMIN, VENDAS |
| compras | GET | `/api/v1/fornecedores/{id}` | ADMIN, VENDAS |
| compras | PUT | `/api/v1/fornecedores/{id}` | ADMIN |
| compras | PATCH | `/api/v1/fornecedores/{id}/ativar` | ADMIN |
| compras | PATCH | `/api/v1/fornecedores/{id}/desativar` | ADMIN |
| financeiro | GET | `/api/v1/financeiro/contas-a-pagar` | ADMIN |
| financeiro | GET | `/api/v1/financeiro/contas-a-pagar/{id}` | ADMIN |
| financeiro | POST | `/api/v1/financeiro/contas-a-pagar/{id}/pagar` | ADMIN |
| financeiro | GET | `/api/v1/financeiro/fluxo` | ADMIN |
| financeiro | GET | `/api/v1/financeiro/lancamentos` | ADMIN |
| financeiro | POST | `/api/v1/financeiro/lancamentos` | ADMIN |
| financeiro | GET | `/api/v1/financeiro/lancamentos/{id}` | ADMIN |
| financeiro | POST | `/api/v1/financeiro/lancamentos/{id}/estornar` | ADMIN |
| financeiro | GET | `/api/v1/financeiro/resumo-dia` | ADMIN |
| pets | GET | `/api/v1/especies` | qualquer papel |
| pets | GET | `/api/v1/especies/{especieId}/racas` | qualquer papel |
| pets | GET | `/api/v1/pets` | qualquer papel |
| pets | POST | `/api/v1/pets` | ADMIN, VENDAS |
| pets | DELETE | `/api/v1/pets/{id}` | ADMIN, VENDAS |
| pets | GET | `/api/v1/pets/{id}` | qualquer papel |
| pets | PUT | `/api/v1/pets/{id}` | ADMIN, VENDAS |
| pets | GET | `/api/v1/pets/{id}/foto` | qualquer papel |
| pets | PUT | `/api/v1/pets/{id}/foto` | ADMIN, VENDAS |
| clinica | GET | `/api/v1/especies/{especieId}/vacinas` | qualquer papel |
| clinica | GET | `/api/v1/pets/{petId}/consultas` | qualquer papel |
| clinica | POST | `/api/v1/pets/{petId}/consultas` | qualquer papel |
| clinica | GET | `/api/v1/pets/{petId}/vacinas` | qualquer papel |
| clinica | POST | `/api/v1/pets/{petId}/vacinas` | ADMIN, VENDAS |
| clinica | DELETE | `/api/v1/pets/{petId}/vacinas/{lancamentoId}` | ADMIN, VENDAS |
| agenda | GET | `/api/v1/agendamentos` | qualquer papel |
| agenda | POST | `/api/v1/agendamentos` | ADMIN, VENDAS |
| agenda | GET | `/api/v1/agendamentos/preco` | qualquer papel |
| agenda | GET | `/api/v1/agendamentos/{id}` | qualquer papel |
| agenda | PUT | `/api/v1/agendamentos/{id}` | ADMIN, VENDAS |
| agenda | POST | `/api/v1/agendamentos/{id}/status` | qualquer papel |
| agenda | GET | `/api/v1/pets/{petId}/agendamentos/proximos` | qualquer papel |
| agenda | GET | `/api/v1/servicos` | qualquer papel |
| agenda | POST | `/api/v1/servicos` | ADMIN |
| agenda | DELETE | `/api/v1/servicos/{id}` | ADMIN |
| agenda | GET | `/api/v1/servicos/{id}` | qualquer papel |
| agenda | PUT | `/api/v1/servicos/{id}` | ADMIN |
| relatorios (+caixa) | GET | `/api/v1/relatorios/caixas` | ADMIN |
| relatorios (+caixa) | GET | `/api/v1/relatorios/caixas/csv` | ADMIN |
| relatorios (+financeiro) | GET | `/api/v1/relatorios/contas` | ADMIN |
| relatorios (+financeiro) | GET | `/api/v1/relatorios/contas/csv` | ADMIN |
| relatorios (+pdv) | GET | `/api/v1/relatorios/curva-abc` | ADMIN, VENDAS |
| relatorios (+pdv) | GET | `/api/v1/relatorios/curva-abc/csv` | ADMIN, VENDAS |
| relatorios (+financeiro) | GET | `/api/v1/relatorios/despesas` | ADMIN |
| relatorios (+financeiro) | GET | `/api/v1/relatorios/despesas/csv` | ADMIN |
| relatorios (+estoque) | GET | `/api/v1/relatorios/estoque` | ADMIN |
| relatorios (+estoque) | GET | `/api/v1/relatorios/estoque/csv` | ADMIN |
| relatorios (+pdv) | GET | `/api/v1/relatorios/margem` | ADMIN |
| relatorios (+pdv) | GET | `/api/v1/relatorios/margem/csv` | ADMIN |
| relatorios (+pdv) | GET | `/api/v1/relatorios/produtos-mais-vendidos` | ADMIN, VENDAS |
| relatorios (+pdv) | GET | `/api/v1/relatorios/produtos-mais-vendidos/csv` | ADMIN, VENDAS |
| relatorios (+agenda) | GET | `/api/v1/relatorios/servicos` | ADMIN |
| relatorios (+agenda) | GET | `/api/v1/relatorios/servicos/csv` | ADMIN |
| relatorios (+pdv) | GET | `/api/v1/relatorios/vendas` | ADMIN, VENDAS |
| relatorios (+pdv) | GET | `/api/v1/relatorios/vendas/csv` | ADMIN, VENDAS |
| vacina-estoque (clinica+lotes) | GET | `/api/v1/produtos/{id}/vacinas` | ADMIN, VENDAS |
| vacina-estoque (clinica+lotes) | GET | `/api/v1/vacinas/estoque` | ADMIN |
| vacina-estoque (clinica+lotes) | DELETE | `/api/v1/vacinas/{id}/produto` | ADMIN |
| vacina-estoque (clinica+lotes) | PUT | `/api/v1/vacinas/{id}/produto` | ADMIN |
