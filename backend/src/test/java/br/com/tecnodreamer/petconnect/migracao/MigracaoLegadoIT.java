package br.com.tecnodreamer.petconnect.migracao;

import br.com.tecnodreamer.petconnect.migracao.application.ResultadoEntidade;
import br.com.tecnodreamer.petconnect.migracao.application.ResultadoMigracao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MigracaoLegadoIT extends MigracaoIntegrationTest {

    @Test
    void deveMigrarFixtureCompleta() {
        assertThat(cenario.real().status()).isEqualTo(ResultadoMigracao.Status.CONCLUIDA);
        assertThat(cenario.real().conferenciasOk()).isTrue();

        // Usuários: colisão com o bootstrap, super usuário → ADMIN, sem e-mail → inativo.
        assertThat(jdbc.queryForObject("SELECT u.username FROM usuario u WHERE u.id = ?", String.class, idNovo("usuario", 1)))
                .isEqualTo("admin_legado");
        assertThat(jdbc.queryForObject("SELECT p.nome FROM usuario u JOIN papel p ON p.id = u.papel_id WHERE u.id = ?",
                String.class, idNovo("usuario", 1))).isEqualTo("ADMIN");
        assertThat(jdbc.queryForObject("SELECT ativo FROM usuario WHERE id = ?", Boolean.class, idNovo("usuario", 3))).isFalse();
        assertThat(motivo("usuario", 3)).contains("USUARIO_SEM_EMAIL_RECUPERACAO");

        // Clientes: CPF limpo, inválido/duplicado → NULL, telefone repetido → IGNORADO.
        assertThat(jdbc.queryForObject("SELECT cpf FROM cliente WHERE id = ?", String.class, idNovo("cliente", 1)))
                .isEqualTo("61350824771");
        assertThat(jdbc.queryForObject("SELECT cpf FROM cliente WHERE id = ?", String.class, idNovo("cliente", 2))).isNull();
        assertThat(motivo("cliente", 3)).contains("CPF_DUPLICADO").contains("ENDERECO_INCOMPLETO");
        assertThat(situacao("cliente", 4)).isEqualTo("IGNORADO");
        assertThat(jdbc.queryForObject("SELECT e.uf FROM cliente c JOIN endereco e ON e.id = c.endereco_id WHERE c.id = ?",
                String.class, idNovo("cliente", 1))).isEqualTo("SP");

        // Documento obrigatório inválido → IGNORADO.
        assertThat(motivo("funcionario", 2)).isEqualTo("DOCUMENTO_INVALIDO_SCHEMA_OBRIGATORIO");
        assertThat(situacao("fornecedor", 2)).isEqualTo("IGNORADO");

        // Catálogos: casados por nome normalizado ou criados.
        assertThat(jdbc.queryForObject("SELECT nome FROM especie WHERE id = ?", String.class, idNovo("especie", 1))).isEqualTo("Cão");
        assertThat(jdbc.queryForObject("SELECT nome FROM raca WHERE id = ?", String.class, idNovo("raca", 3))).isEqualTo("Siamês");
        assertThat(situacao("especie", 3)).isEqualTo("AJUSTADO");
        assertThat(motivo("preco_servico_porte", 1)).isEqualTo("PRECO_PORTE_JA_EXISTE");

        // Pets, foto, carteira.
        assertThat(jdbc.queryForObject("SELECT foto_url FROM pet WHERE id = ?", String.class, idNovo("pet", 1))).isNotNull();
        assertThat(motivo("pet_foto", 2)).isEqualTo("FOTO_AUSENTE");
        assertThat(motivo("pet_foto", 3)).isEqualTo("FOTO_CAMINHO_INVALIDO");
        assertThat(jdbc.queryForObject("SELECT porte FROM pet WHERE id = ?", String.class, idNovo("pet", 3))).isEqualTo("MEDIO");
        assertThat(motivo("carteira_vacina", 2)).isEqualTo("VACINA_CRIADA");

        // Agendamentos com status convertidos.
        assertThat(jdbc.queryForObject("SELECT status FROM agendamento WHERE id = ?", String.class, idNovo("agendamento", 2)))
                .isEqualTo("NAO_COMPARECEU");
        assertThat(jdbc.queryForObject("SELECT status FROM agendamento WHERE id = ?", String.class, idNovo("agendamento", 4)))
                .isEqualTo("CANCELADO");
    }

    @Test
    void deveRespeitarOrdemDeForeignKeys() {
        assertThat(migracao.ordem()).containsExactly("papel", "usuario", "cliente", "funcionario", "fornecedor", "especie",
                "raca", "vacina", "servico", "preco_servico_porte", "categoria_produto", "pet", "pet_foto", "carteira_vacina",
                "historico_consulta", "produto", "produto_estoque", "compra", "agendamento", "servico_realizado", "venda",
                "movimentacao_financeira");
        assertThat(jdbc.queryForObject("SELECT cliente_id FROM pet WHERE id = ?", Long.class, idNovo("pet", 1)))
                .isEqualTo(idNovo("cliente", 1));
    }

    @Test
    void deveMigrarEmLotes() {
        // Lote de 2 com 4 produtos: o laço precisou de mais de um lote e contou todos.
        assertThat(cenario.opcoesReais().lote()).isEqualTo(2);
        assertThat(cenario.falha().daEntidade("produto").lidos()).isEqualTo(4);
        assertThat(cenario.falha().daEntidade("cliente").lidos()).isEqualTo(4);
    }

    @Test
    void senhaAntigaDeveRetornar401() throws Exception {
        tentarLogin("admin_legado", "admin123").andExpect(status().isUnauthorized());
        tentarLogin("maria.vendas", "Maria@123").andExpect(status().isUnauthorized());
        // O ADMIN do bootstrap continua intacto.
        tentarLogin(ADMIN_USER, ADMIN_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void deveCriarMapaParaRegistrosProcessados() {
        Map<String, Long> porSituacao = new java.util.HashMap<>();
        jdbc.query("SELECT situacao, COUNT(*) n FROM migracao_legado_mapa WHERE entidade = 'venda' GROUP BY situacao",
                rs -> {
                    porSituacao.put(rs.getString("situacao"), rs.getLong("n"));
                });
        assertThat(porSituacao).containsEntry("MIGRADO", 1L).containsEntry("AJUSTADO", 1L).containsEntry("IGNORADO", 2L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migracao_legado_mapa WHERE entidade = 'cliente'", Long.class))
                .isEqualTo(4L);
    }

    @Test
    void deveGerarApenasContagensNaExecucao() {
        Map<String, Object> exec = jdbc.queryForMap("SELECT * FROM migracao_legado_execucao WHERE id = ?",
                cenario.real().execucaoId());
        assertThat(exec).containsEntry("modo", "REAL").containsEntry("status", "CONCLUIDA");
        assertThat(exec.get("mensagem")).isNull();
        assertThat(((Number) exec.get("total_lidos")).longValue()).isEqualTo(cenario.real().total(ResultadoEntidade::lidos));
        assertThat(auditoria.findAll()).filteredOn(e -> e.acao().equals("MIGRACAO_LEGADO_EXECUTADA"))
                .anySatisfy(e -> assertThat(e.detalhes()).containsOnlyKeys("execucaoId", "status", "lidos", "migrados",
                        "ajustados", "ignorados", "jaMigrados", "pendencias"));
    }
}
