# 01 — Pets, Carteira de Vacinação e Histórico de Consultas

> **Arquivo:** `01-pets.md`  
> **Projeto:** PetConnect Web  
> **Stack:** Spring Boot 4.1 + Java 21 + Next.js 16 + TypeScript + MySQL 8.4 + MongoDB 7  
> **Execução:** Este documento é uma especificação executável para o Claude Code trabalhar diretamente no monorepo.  
> **Regra principal:** antes de alterar qualquer arquivo, inspecione a implementação atual e preserve os padrões arquiteturais, visuais, de segurança, testes e nomenclatura já existentes. Não reescreva módulos funcionais desnecessariamente.

---

# 1. Objetivo

Implementar o módulo **Pets** do PetConnect Web, substituindo e melhorando as funcionalidades existentes no sistema legado PHP.

A implementação deve integrar:

- cadastro de pets;
- edição de pets;
- consulta de pets;
- exclusão lógica;
- foto do pet;
- espécies;
- raças dependentes da espécie;
- carteira de vacinação;
- cálculo do status de cada lançamento de vacina;
- histórico de consultas;
- vínculo pet ↔ tutor;
- listagem de pets dentro da ficha do cliente;
- item **Pets** funcional no menu principal;
- autorização por papel;
- auditoria no MongoDB;
- testes backend e frontend.

O módulo deve seguir integralmente a arquitetura já existente no projeto.

---

# 2. Análise obrigatória antes da implementação

Antes de modificar código:

1. Inspecionar `backend/`, `frontend/`, migrations Flyway existentes, módulo de clientes, configuração JWT, `AuditoriaService`, tratamento global de erros, `PageResponse`, `apiFetch`, Server Actions, schemas Zod, `AppShell`, `Campo`, `Aviso` e estilos existentes.
2. Usar o módulo de clientes como principal referência arquitetural.
3. Consultar apenas como referência funcional os arquivos PHP legados relacionados a pets, vacinas, raças e detalhes de clientes.
4. Não copiar a arquitetura PHP.
5. Não alterar migrations já aplicadas (`V1`, `V2`).
6. Não criar segunda implementação de recursos existentes.
7. Executar testes existentes antes das alterações para estabelecer baseline.
8. Ao final, executar testes, build, lint e typecheck disponíveis.

---

# 3. Escopo

## 3.1. Dentro desta etapa

### Pets
- listar, pesquisar e paginar;
- cadastrar, visualizar e editar;
- excluir logicamente;
- foto;
- vínculo com tutor;
- idade calculada;
- espécie e raça;
- peso, cor, porte, castração e observações.

### Catálogos
- listar espécies;
- listar raças por espécie;
- listar vacinas ativas por espécie.

Espécies e raças são somente leitura nesta etapa.

### Vacinação
- listar carteira;
- lançar vacina;
- remover lançamento;
- validar vacina contra espécie;
- calcular status;
- exibir próxima dose;
- estados `VENCIDA`, `VENCE_EM_30_DIAS`, `EM_DIA` e `SEM_PROXIMA_DOSE`.

### Consultas
- listar histórico;
- registrar consulta.

### Cliente
Em `/clientes/[id]`:
- listar pets ativos;
- botão **Adicionar pet**;
- acesso à ficha do pet.

### Navegação
Liberar **Pets** no `AppShell`.

---

# 4. Fora do escopo

Não implementar nesta etapa: agendamentos, PDV, vendas, estoque, serviços executados, prontuário clínico avançado, prescrições, anexos médicos, PDF da carteira, lembretes, notificações, e-mail, WhatsApp, migração automática do legado, CRUD administrativo de espécies/raças/vacinas, Firebase, Cloudinary, S3, sincronização mobile, QR Code ou compartilhamento externo.

---

# 5. Arquitetura backend

Seguir o padrão:

```text
br.com.tecnodreamer.petconnect.pet
├── domain
├── application
├── infrastructure
└── web
```

Entidades JPA sem setters públicos, com métodos de intenção. Serviços de escrita com `@Transactional` e auditoria. Controllers somente para orquestração HTTP, DTOs `record`, Bean Validation e `@PreAuthorize`.

---

# 6. Banco de dados

Não recriar tabelas existentes. Não editar V1/V2.

Criar `V3__indices_modulo_pets.sql` apenas com índices realmente ausentes, após conferir migrations existentes:

```sql
CREATE INDEX idx_pet_cliente_ativo ON pet (cliente_id, ativo);
CREATE INDEX idx_pet_nome_ativo ON pet (nome, ativo);
CREATE INDEX idx_raca_especie ON raca (especie_id, nome);
CREATE INDEX idx_vacina_especie_ativo ON vacina (especie_id, ativo, nome);
CREATE INDEX idx_carteira_vacina_pet_data ON carteira_vacina (pet_id, data_aplicacao);
CREATE INDEX idx_historico_consulta_pet_data ON historico_consulta (pet_id, data_consulta);
```

Não duplicar índices equivalentes.

---

# 7. Foto do pet

Usar volume local persistente no Docker Compose, sem credenciais externas:

```text
/data/uploads/pets/
```

Usar `pet.foto_url`. Não armazenar imagem no banco, Base64, Git ou resources.

Aceitar somente JPEG, PNG e WebP, máximo 5 MB. Não aceitar SVG/GIF. Não confiar no nome original. Gerar UUID.

A API deve expor:

```text
/api/v1/pets/{id}/foto
```

e nunca o caminho físico.

---

# 8. API — Pets

Base: `/api/v1/pets`.

## Listar

```http
GET /api/v1/pets?page=0&size=20&q=mel
```

Qualquer autenticado. Buscar case-insensitive por nome do pet ou tutor. Somente `ativo=true`. Paginação `PageResponse`.

Validar `page >= 0`, `1 <= size <= 100` e `q <= 100`.

## Detalhar

```http
GET /api/v1/pets/{id}
```

Qualquer autenticado. Pet inexistente/inativo → 404.

A resposta deve incluir tutor, espécie, raça, nascimento, idade calculada, peso, cor, porte, castrado, foto, observações e timestamps.

## Criar

```http
POST /api/v1/pets
```

ADMIN/VENDAS.

Campos: `clienteId`, `nome`, `especieId`, `racaId`, `dataNascimento`, `peso`, `cor`, `porte`, `castrado`, `observacoes`.

Validar cliente ativo, espécie, compatibilidade espécie-raça, nascimento não futuro, peso `>0` e `<=999.99`, porte permitido e limites textuais.

Raça existente mas incompatível com espécie → `409 Conflict`.

Auditar `PET_CRIADO`.

## Atualizar

```http
PUT /api/v1/pets/{id}
```

ADMIN/VENDAS. Mesmas validações do cadastro. Não corrigir raça incompatível silenciosamente.

Auditar `PET_ATUALIZADO`.

## Excluir

```http
DELETE /api/v1/pets/{id}
```

ADMIN/VENDAS. Executar `ativo=false`. Resposta 204.

Auditar `PET_DESATIVADO`.

---

# 9. Idade

Não persistir idade. Calcular com `LocalDate` + `Period`.

Resposta sugerida:

```json
{
  "anos": 5,
  "meses": 5,
  "descricao": "5 anos e 5 meses"
}
```

Sem nascimento → `idade: null`.

Centralizar relógio usando `Clock` para permitir testes determinísticos.

---

# 10. Foto — API

```http
PUT /api/v1/pets/{id}/foto
Content-Type: multipart/form-data
```

ADMIN/VENDAS. Campo `foto`.

Erros 400 para arquivo ausente, formato inválido ou >5 MB; 404 para pet inexistente/inativo.

Auditar `PET_FOTO_ATUALIZADA`.

```http
GET /api/v1/pets/{id}/foto
```

Qualquer autenticado. Retornar bytes e Content-Type correto. Sem foto → 404.

---

# 11. Catálogos

```http
GET /api/v1/especies
GET /api/v1/especies/{especieId}/racas
GET /api/v1/especies/{especieId}/vacinas
```

Qualquer autenticado.

Ordenar espécies/raças/vacinas por nome. Vacinas: somente `ativo=true`.

---

# 12. Carteira de vacinação

Base:

```text
/api/v1/pets/{petId}/vacinas
```

## Listar

```http
GET /api/v1/pets/{petId}/vacinas
```

Qualquer autenticado. Ordenar `data_aplicacao DESC`.

Cada item deve trazer vacina, aplicação, próxima dose, veterinário, lote, observações, `status` e `statusDescricao`.

## Status

- `dataProxima == null` → `SEM_PROXIMA_DOSE`
- `dataProxima < hoje` → `VENCIDA`
- `hoje <= dataProxima <= hoje + 30 dias` → `VENCE_EM_30_DIAS`
- `dataProxima > hoje + 30 dias` → `EM_DIA`

Não persistir status. Usar `Clock`.

## Registrar

```http
POST /api/v1/pets/{petId}/vacinas
```

ADMIN/VENDAS.

Request:

```json
{
  "vacinaId": 4,
  "dataAplicacao": "2026-09-20",
  "dataProxima": "2027-09-20",
  "veterinario": "Dra. Ana Souza",
  "lote": "RAI-2026-001",
  "observacoes": "Aplicação sem intercorrências."
}
```

Vacina deve existir, estar ativa e pertencer à espécie do pet. Incompatibilidade → 409.

Aplicação não pode ser futura. Próxima dose, se informada, deve ser >= aplicação.

Auditar `VACINA_PET_REGISTRADA`.

## Remover

```http
DELETE /api/v1/pets/{petId}/vacinas/{lancamentoId}
```

ADMIN/VENDAS. Validar pertencimento ao pet. Resposta 204.

Auditar `VACINA_PET_REMOVIDA`.

---

# 13. Histórico de consultas

Base:

```text
/api/v1/pets/{petId}/consultas
```

## Listar

```http
GET /api/v1/pets/{petId}/consultas
```

Qualquer autenticado. Mais recente primeiro.

## Registrar

```http
POST /api/v1/pets/{petId}/consultas
```

ADMIN/VENDAS/SERVICO.

Campos: `dataConsulta`, `veterinario`, `motivo`, `diagnostico`, `tratamento`, `observacoes`.

Data obrigatória e não futura. Veterinário obrigatório, 2–150 caracteres.

Auditar `CONSULTA_PET_REGISTRADA`.

Não implementar edição/exclusão nesta etapa.

---

# 14. Autorização

| Operação | ADMIN | VENDAS | SERVICO |
|---|---:|---:|---:|
| Ler pets | Sim | Sim | Sim |
| Criar/editar/excluir pet | Sim | Sim | Não |
| Alterar foto | Sim | Sim | Não |
| Ver catálogos | Sim | Sim | Sim |
| Ver carteira | Sim | Sim | Sim |
| Lançar/remover vacina | Sim | Sim | Não |
| Ver consultas | Sim | Sim | Sim |
| Registrar consulta | Sim | Sim | Sim |

Implementar no backend com `@PreAuthorize`.

---

# 15. Auditoria MongoDB

Usar `AuditoriaService.registrar(acao, entidade, id, detalhes)`.

Registrar:
- `PET_CRIADO`
- `PET_ATUALIZADO`
- `PET_DESATIVADO`
- `PET_FOTO_ATUALIZADA`
- `VACINA_PET_REGISTRADA`
- `VACINA_PET_REMOVIDA`
- `CONSULTA_PET_REGISTRADA`

Não auditar leitura. Nunca registrar JWT, senha ou bytes de imagem.

---

# 16. Frontend

Criar:

```text
/pets
/pets/novo
/pets/[id]
```

Usar Server Components para leitura, `apiFetch<T>()`, Server Actions para escrita, Zod e `useActionState`. JWT permanece em cookie httpOnly.

## `/pets`

Busca "Buscar por pet ou tutor", paginação, botão "Novo pet".

Desktop `md+`: tabela. Mobile: cards.

Estados vazios:
- "Nenhum pet cadastrado. Cadastre o primeiro pet para começar."
- busca vazia: "Nenhum pet encontrado para esta busca."

## `/pets/novo`

Campos: tutor, nome, espécie, raça, nascimento, peso, cor, porte, castrado, observações e foto.

Select dependente espécie → raça. Sem espécie, raça desabilitada. Ao mudar espécie, limpar raça anterior.

Fluxo de foto:
1. criar pet;
2. receber ID;
3. enviar foto;
4. abrir `/pets/{id}`.

Falha da foto não deve desfazer cadastro do pet.

## `/pets/[id]`

Cabeçalho com foto, nome, espécie/raça, tutor, idade, peso, porte e castração.

Seções/abas:
- Dados
- Vacinas
- Consultas

Mobile sem overflow horizontal.

---

# 17. Vacinas no frontend

Mostrar botão **Registrar vacina** conforme autorização.

Badges devem usar texto e não depender apenas da cor:

- `✓ Em dia`
- `⚠ Vence em até 30 dias`
- `! Vencida`
- `— Sem próxima dose`

Formulário deve usar catálogo da espécie; não aceitar vacina em texto livre.

Antes de remover, pedir confirmação.

---

# 18. Cliente → Pets

Em `/clientes/[id]`, listar pets ativos.

Adicionar botão:

```text
Adicionar pet
```

Destino:

```text
/pets/novo?clienteId={id}
```

Pré-selecionar tutor após validar o ID no servidor.

Permitir navegação cliente → pet e pet → cliente.

---

# 19. Menu

Remover "em breve" do item Pets no `AppShell`.

Destino:

```text
/pets
```

Preservar padrões visuais, estado ativo e menu mobile.

---

# 20. Estados e erros

Criar loading/skeleton adequado em `/pets` e `/pets/[id]`.

Tratamento:
- 400 → erros nos campos;
- 401 → fluxo global existente;
- 403 → "Você não possui permissão para realizar esta ação.";
- 404 → "Pet não encontrado.";
- 409 → mensagem da API;
- rede/500 → "Não foi possível concluir a operação. Tente novamente."

Usar `Aviso`.

---

# 21. Regras de negócio

1. Pet ativo deve possuir cliente ativo.
2. Espécie é obrigatória.
3. Raça é opcional.
4. Raça informada deve pertencer à espécie.
5. Nascimento não pode ser futuro.
6. Idade é calculada, nunca persistida.
7. Peso deve ser >0 e <=999.99.
8. Porte: `PEQUENO`, `MEDIO`, `GRANDE`.
9. Exclusão de pet é lógica.
10. Pets inativos não aparecem em consultas normais.
11. Foto somente JPEG/PNG/WebP, máximo 5 MB.
12. Vacina deve pertencer à espécie do pet.
13. Aplicação não pode ser futura.
14. Próxima dose não pode anteceder aplicação.
15. Status da vacina é dinâmico.
16. Lançamento só pode ser removido do pet ao qual pertence.
17. Consulta exige pet ativo.
18. Consulta não pode ser futura.
19. Veterinário é obrigatório na consulta.
20. Busca pesquisa nome do pet e tutor.
21. Autorização nunca depende somente do frontend.

---

# 22. Testes unitários backend

## PetServiceTest

- `deveCriarPetComDadosValidos`
- `deveFalharAoCriarPetComClienteInexistente`
- `deveFalharAoCriarPetComClienteInativo`
- `deveFalharAoCriarPetComEspecieInexistente`
- `deveFalharQuandoRacaNaoPertenceAEspecie`
- `deveAtualizarPet`
- `deveFalharAoAlterarEspecieMantendoRacaIncompativel`
- `deveDesativarPet`
- `deveCalcularIdadeEmAnosEMeses`
- `deveRetornarIdadeNulaSemDataNascimento`

## CarteiraVacinaServiceTest

- `deveRegistrarVacinaDaMesmaEspecie`
- `deveFalharQuandoVacinaForDeOutraEspecie`
- `deveFalharQuandoVacinaEstiverInativa`
- `deveFalharQuandoDataAplicacaoForFutura`
- `deveFalharQuandoDataProximaForAnteriorAAplicacao`
- `deveCalcularStatusVencida`
- `deveCalcularStatusVenceEmTrintaDias`
- `deveConsiderarHojeComoVenceEmTrintaDias`
- `deveCalcularStatusEmDia`
- `deveCalcularStatusSemProximaDose`
- `deveRemoverLancamentoDoPet`
- `deveFalharAoRemoverLancamentoDeOutroPet`

## HistoricoConsultaServiceTest

- `deveRegistrarConsulta`
- `deveFalharComPetInexistente`
- `deveFalharComPetInativo`
- `deveFalharComDataConsultaFutura`
- `deveListarConsultasDaMaisRecenteParaMaisAntiga`

## FotoPetServiceTest

- `deveAceitarJpeg`
- `deveAceitarPng`
- `deveAceitarWebp`
- `deveRejeitarTipoNaoPermitido`
- `deveRejeitarArquivoMaiorQueCincoMegabytes`
- `deveGerarNomeInternoSemUsarNomeOriginal`
- `deveSubstituirFotoExistente`

---

# 23. Testes de integração

Criar `PetControllerIT`, `CatalogoPetControllerIT`, `CarteiraVacinaControllerIT`, `HistoricoConsultaControllerIT` e `FotoPetControllerIT`, estendendo `IntegrationTest`.

Cobrir:
- leitura autenticada;
- busca pet/tutor;
- paginação;
- pet inativo;
- CRUD autorizado;
- 403 para papéis sem permissão;
- 409 espécie/raça;
- catálogos;
- vacina incompatível;
- remoção de lançamento;
- consultas por ADMIN/VENDAS/SERVICO;
- foto válida/inválida;
- auditoria Mongo nas escritas principais.

---

# 24. Testes frontend

Criar casos equivalentes:

### `pet-schema.test.ts`
- pet válido;
- nome vazio;
- nascimento futuro;
- peso zero;
- porte inválido.

### `vacina-schema.test.ts`
- lançamento válido;
- próxima dose nula;
- aplicação futura;
- próxima dose anterior.

### `consulta-schema.test.ts`
- consulta válida;
- veterinário vazio;
- consulta futura.

### `pet-status.test.tsx`
- todos os quatro badges com texto.

### `pet-form.test.tsx`
- raça desabilitada sem espécie;
- limpa raça ao alterar espécie;
- erro da Server Action no campo.

### `pet-list.test.tsx`
- estado vazio;
- pet e tutor;
- resultado de busca.

### `cliente-pets.test.tsx`
- pets do cliente;
- adicionar pet com cliente pré-selecionado.

---

# 25. Segurança

Garantir:
- JWT nunca chega a Client Component;
- cookie httpOnly;
- `@PreAuthorize`;
- UUID para nomes de arquivo;
- proteção contra path traversal;
- MIME e tamanho validados;
- SVG proibido;
- caminho físico não exposto;
- DTOs não aceitam `ativo`, `createdAt` ou `updatedAt`;
- IDs sempre validados no backend.

---

# 26. Docker Compose

Criar volume persistente, por exemplo:

```yaml
volumes:
  petconnect_uploads:
```

Backend:

```yaml
volumes:
  - petconnect_uploads:/data/uploads
```

Propriedade:

```text
petconnect.storage.pets=/data/uploads/pets
```

Testes devem usar diretório temporário.

---

# 27. Critérios de aceite

- [ ] V1/V2 intactas.
- [ ] V3 contém apenas índices necessários.
- [ ] Flyway funciona em banco limpo e upgrade V2→V3.
- [ ] Pets podem ser listados, buscados e paginados.
- [ ] CRUD funciona com exclusão lógica.
- [ ] Idade é calculada.
- [ ] Raça incompatível gera 409.
- [ ] Upload JPEG/PNG/WebP funciona.
- [ ] Arquivo >5 MB/tipo inválido é rejeitado.
- [ ] Foto persiste após restart do container.
- [ ] Espécies/raças/vacinas são filtradas corretamente.
- [ ] Carteira funciona com quatro estados.
- [ ] Vacina incompatível gera 409.
- [ ] Remoção exige confirmação.
- [ ] Histórico de consultas funciona.
- [ ] SERVICO pode registrar consulta.
- [ ] `/clientes/[id]` lista pets e possui "Adicionar pet".
- [ ] `/pets`, `/pets/novo` e `/pets/[id]` funcionam.
- [ ] Menu Pets está habilitado.
- [ ] Desktop e mobile seguem layouts definidos.
- [ ] Auditorias estão registradas.
- [ ] Testes unitários passam.
- [ ] Testes de integração passam.
- [ ] Testes frontend passam.
- [ ] Build backend passa.
- [ ] Build frontend passa.
- [ ] TypeScript passa.
- [ ] Lint passa.

---

# 28. Riscos

**Upload local:** adequado para Docker Compose/instância única; encapsular em serviço para futura troca por storage externo.

**Crescimento de armazenamento:** limitar a 5 MB; compressão futura fora de escopo.

**Integridade espécie-raça/vacina:** validar obrigatoriamente no application service.

**Datas:** centralizar relógio com `Clock`, usando `Clock.fixed(...)` nos testes.

---

# 29. Próximas etapas

Não implementar agora:
- agendamentos;
- PDV;
- estoque;
- serviços;
- migração de dados do legado.

A migração deve ser projeto/etapa específica e tratar clientes, pets, fotos, espécies, raças, vacinas antigas em texto livre, carteira e histórico.

---

# 30. Ordem obrigatória de implementação

1. Analisar arquitetura.
2. Executar baseline.
3. Analisar V1/V2.
4. Criar V3 somente se necessário.
5. Implementar catálogos.
6. Implementar pets.
7. Implementar idade com `Clock`.
8. Implementar carteira/status.
9. Implementar consultas.
10. Implementar storage de fotos.
11. Configurar Docker.
12. Implementar autorização.
13. Implementar auditoria.
14. Criar testes backend.
15. Criar `/pets`.
16. Criar `/pets/novo`.
17. Criar `/pets/[id]`.
18. Integrar vacinas/consultas.
19. Integrar `/clientes/[id]`.
20. Liberar menu.
21. Criar testes frontend.
22. Executar lint/typecheck.
23. Executar testes.
24. Executar builds.
25. Revisar `git diff`.

---

# 31. Restrições para o Claude Code

- NÃO editar V1/V2.
- NÃO modificar `legacy/php/`.
- NÃO remover testes.
- NÃO desabilitar testes/lint para obter build verde.
- NÃO usar `any` para contornar TypeScript.
- NÃO expor JWT.
- NÃO fazer hard delete de pet.
- NÃO aceitar vacina textual.
- NÃO recriar `vacinado`.
- NÃO persistir idade/status calculado.
- NÃO adicionar serviços externos.
- NÃO implementar itens fora do escopo.
- NÃO fazer commit/push automaticamente.

Se a estrutura real do repositório divergir desta especificação, preservar a arquitetura consolidada sem violar as regras funcionais deste documento.

---

# 32. Relatório final obrigatório

Ao concluir, apresentar:

```text
## Implementação concluída

### Banco
- migrations
- índices

### Backend
- arquivos criados/alterados
- endpoints

### Frontend
- rotas
- componentes

### Segurança
- autorizações

### Auditoria
- eventos

### Testes
- unitários: X aprovados / Y falharam
- integração: X aprovados / Y falharam
- frontend: X aprovados / Y falharam

### Validações finais
- backend build: PASS/FAIL
- frontend build: PASS/FAIL
- lint: PASS/FAIL
- typecheck: PASS/FAIL

### Pendências
- somente pendências reais
```

Não declarar funcionalidade concluída sem implementação e validação.

---

# 33. Definição de pronto

O fluxo deve funcionar ponta a ponta:

```text
Login
  ↓
Pets
  ↓
Buscar/listar
  ↓
Cadastrar
  ↓
Selecionar tutor
  ↓
Selecionar espécie
  ↓
Carregar raças
  ↓
Cadastrar dados
  ↓
Enviar foto
  ↓
Ficha do pet
  ├── Dados
  ├── Vacinas
  │     ↓
  │   Vacina compatível
  │     ↓
  │   Registrar
  │     ↓
  │   Status calculado
  └── Consultas
        ↓
      Registrar consulta
```

Também:

```text
Clientes
  ↓
Detalhes do cliente
  ↓
Pets do cliente
  ↓
Abrir pet
```

E:

```text
Pet → Tutor
Tutor → Pet
```

A etapa somente está pronta quando os fluxos estiverem funcionais, protegidos pelas permissões definidas, auditados nas escritas e cobertos pelos testes especificados.
