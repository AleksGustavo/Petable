package br.com.tecnodreamer.petconnect.migracao.infrastructure;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Gravações e consultas da migração no banco NOVO, em SQL simples, na transação corrente.
 * Regras com invariante própria (saldo, financeiro, foto) NÃO passam por aqui: vão pelos
 * serviços de domínio (EstoqueService, FinanceiroService, FotoPetService).
 */
@Component
public class DestinoMigracao {

    public record Nomeado(Long id, String nome) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public DestinoMigracao(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** INSERT genérico com as colunas informadas (ordem preservada); devolve o id gerado. */
    public Long inserir(String tabela, Map<String, ?> valores) {
        Map<String, Object> v = new LinkedHashMap<>(valores);
        String colunas = String.join(", ", v.keySet());
        String parametros = v.keySet().stream().map(c -> ":" + c).collect(Collectors.joining(", "));
        KeyHolder chave = new GeneratedKeyHolder();
        jdbc.update("INSERT INTO " + tabela + " (" + colunas + ") VALUES (" + parametros + ")",
                new MapSqlParameterSource(v), chave, new String[] {"id"});
        return chave.getKey().longValue();
    }

    // ------------------------------------------------------------------ acesso

    public Optional<Long> papelId(String nome) {
        return um("SELECT id FROM papel WHERE nome = :nome", Map.of("nome", nome));
    }

    public boolean usernameExiste(String username) {
        return existe("SELECT COUNT(*) FROM usuario WHERE username = :v", username);
    }

    public boolean emailUsuarioExiste(String email) {
        return existe("SELECT COUNT(*) FROM usuario WHERE email = :v", email);
    }

    public Optional<Long> usuarioPorUsername(String username) {
        return um("SELECT id FROM usuario WHERE username = :v", Map.of("v", username));
    }

    public Optional<Long> usuarioDoFuncionario(Long funcionarioId) {
        return um("SELECT usuario_id FROM funcionario WHERE id = :v", Map.of("v", funcionarioId));
    }

    // ------------------------------------------------------------------ pessoas

    public boolean cpfClienteExiste(String cpf) {
        return existe("SELECT COUNT(*) FROM cliente WHERE cpf = :v", cpf);
    }

    public boolean telefoneClienteExiste(String telefone) {
        return existe("SELECT COUNT(*) FROM cliente WHERE telefone = :v", telefone);
    }

    public boolean cpfFuncionarioExiste(String cpf) {
        return existe("SELECT COUNT(*) FROM funcionario WHERE cpf = :v", cpf);
    }

    public boolean telefoneFuncionarioExiste(String telefone) {
        return existe("SELECT COUNT(*) FROM funcionario WHERE telefone = :v", telefone);
    }

    public boolean funcionarioDoUsuarioExiste(Long usuarioId) {
        return existe("SELECT COUNT(*) FROM funcionario WHERE usuario_id = :v", usuarioId);
    }

    public boolean cnpjFornecedorExiste(String cnpj) {
        return existe("SELECT COUNT(*) FROM fornecedor WHERE cnpj = :v", cnpj);
    }

    public boolean telefoneFornecedorExiste(String telefone) {
        return existe("SELECT COUNT(*) FROM fornecedor WHERE telefone = :v", telefone);
    }

    // ------------------------------------------------------------------ catálogos

    public List<Nomeado> especies() {
        return nomeados("SELECT id, nome FROM especie", Map.of());
    }

    public List<Nomeado> racas(Long especieId) {
        return nomeados("SELECT id, nome FROM raca WHERE especie_id = :e", Map.of("e", especieId));
    }

    public List<Nomeado> vacinas(Long especieId) {
        return nomeados("SELECT id, nome FROM vacina WHERE especie_id = :e", Map.of("e", especieId));
    }

    public List<Nomeado> servicos() {
        return nomeados("SELECT id, nome FROM servico", Map.of());
    }

    public List<Nomeado> categoriasProduto() {
        return nomeados("SELECT id, nome FROM categoria_produto", Map.of());
    }

    public Optional<Long> especieDaRaca(Long racaId) {
        return um("SELECT especie_id FROM raca WHERE id = :v", Map.of("v", racaId));
    }

    public Optional<Long> especieDoPet(Long petId) {
        return um("SELECT especie_id FROM pet WHERE id = :v", Map.of("v", petId));
    }

    public boolean precoPorteExiste(Long servicoId, String porte) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM preco_servico_porte WHERE servico_id = :s AND porte = :p",
                Map.of("s", servicoId, "p", porte), Integer.class);
        return n != null && n > 0;
    }

    // ------------------------------------------------------------------ produtos / serviços

    public boolean codigoBarrasExiste(String codigo) {
        return existe("SELECT COUNT(*) FROM produto WHERE codigo_barras = :v", codigo);
    }

    public boolean realizadoDoAgendamentoExiste(Long agendamentoId) {
        return existe("SELECT COUNT(*) FROM servico_realizado WHERE agendamento_id = :v", agendamentoId);
    }

    // ------------------------------------------------------------------ apoio

    private boolean existe(String sql, Object valor) {
        Integer n = jdbc.queryForObject(sql, Map.of("v", valor), Integer.class);
        return n != null && n > 0;
    }

    private Optional<Long> um(String sql, Map<String, ?> params) {
        List<Long> ids = jdbc.queryForList(sql, params, Long.class);
        return ids.isEmpty() ? Optional.empty() : Optional.ofNullable(ids.get(0));
    }

    private List<Nomeado> nomeados(String sql, Map<String, ?> params) {
        return jdbc.query(sql, params, (rs, i) -> new Nomeado(rs.getLong("id"), rs.getString("nome")));
    }
}
