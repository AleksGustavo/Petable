# PetConnect Web — backend

Spring Boot 4.1 · Java 21 · MySQL (Flyway) + MongoDB. Arquitetura e convenções: [../docs/arquitetura.md](../docs/arquitetura.md). Banco: [../docs/banco-de-dados.md](../docs/banco-de-dados.md).

```bash
./mvnw verify                 # testes unitários (*Test) + integração (*IT, Testcontainers: precisa do Docker)
./mvnw spring-boot:test-run   # sobe a API com MySQL/Mongo descartáveis em containers
```

Variáveis de ambiente: veja `src/main/resources/application.yml` e o `.env.example` na raiz. Swagger em `/swagger-ui.html` (desligado no perfil `prod`).
