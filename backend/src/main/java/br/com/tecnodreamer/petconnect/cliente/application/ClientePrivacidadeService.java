package br.com.tecnodreamer.petconnect.cliente.application;

import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Direitos do titular (LGPD, DA-25/DA-26) sem hard-delete:
 * <ul>
 *   <li>exportação: tudo o que o sistema guarda sobre o cliente, em JSON;</li>
 *   <li>anonimização: irreversível; dados pessoais viram NULL/marcadores, histórico de
 *       vendas e atendimentos continua ligado ao cliente (agora anônimo).</li>
 * </ul>
 * A auditoria registra só o fato (e o motivo informado pelo ADMIN), nunca os dados.
 */
@Service
public class ClientePrivacidadeService {

    public static final String PREFIXO_ANONIMO = "Cliente anonimizado #";
    static final String ENTIDADE = "cliente";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditoriaService auditoria;

    public ClientePrivacidadeService(NamedParameterJdbcTemplate jdbc, AuditoriaService auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportar(Long id) {
        Map<String, Object> cliente = cliente(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cliente", cliente);
        Object enderecoId = cliente.get("endereco_id");
        m.put("endereco", enderecoId == null ? null : jdbc.queryForMap(
                "SELECT cep, logradouro, numero, complemento, bairro, cidade, uf FROM endereco WHERE id = :id",
                Map.of("id", enderecoId)));
        List<Map<String, Object>> pets = jdbc.queryForList("""
                SELECT p.id, p.nome, e.nome AS especie, r.nome AS raca, p.data_nascimento, p.peso, p.cor, p.porte,
                       p.castrado, p.observacoes, p.ativo
                  FROM pet p JOIN especie e ON e.id = p.especie_id LEFT JOIN raca r ON r.id = p.raca_id
                 WHERE p.cliente_id = :id ORDER BY p.id""", Map.of("id", id));
        for (Map<String, Object> pet : pets) {
            Map<String, Object> petId = Map.of("pet", pet.get("id"));
            pet.put("vacinas", jdbc.queryForList("""
                    SELECT v.nome AS vacina, c.data_aplicacao, c.data_proxima, c.veterinario, c.lote, c.observacoes
                      FROM carteira_vacina c JOIN vacina v ON v.id = c.vacina_id WHERE c.pet_id = :pet
                     ORDER BY c.data_aplicacao""", petId));
            pet.put("consultas", jdbc.queryForList("""
                    SELECT data_consulta, veterinario, motivo, diagnostico, tratamento, observacoes
                      FROM historico_consulta WHERE pet_id = :pet ORDER BY data_consulta""", petId));
            pet.put("agendamentos", jdbc.queryForList("""
                    SELECT a.data_hora, s.nome AS servico, a.status, a.total_estimado
                      FROM agendamento a JOIN servico s ON s.id = a.servico_id WHERE a.pet_id = :pet
                     ORDER BY a.data_hora""", petId));
        }
        m.put("pets", pets);
        m.put("vendas", jdbc.queryForList("""
                SELECT id, data_venda, valor_total, desconto, forma_pagamento, status
                  FROM venda WHERE cliente_id = :id ORDER BY data_venda""", Map.of("id", id)));
        auditoria.registrar("CLIENTE_DADOS_EXPORTADOS", ENTIDADE, id, Map.of());
        return m;
    }

    @Transactional
    public void anonimizar(Long id, String motivo) {
        String texto = motivo == null ? "" : motivo.trim();
        if (texto.length() < 5 || texto.length() > 500) {
            throw new CampoInvalidoException("motivo", "Informe o motivo (5 a 500 caracteres).");
        }
        Map<String, Object> cliente = cliente(id);
        String nome = (String) cliente.get("nome");
        if (nome != null && nome.startsWith(PREFIXO_ANONIMO)) {
            throw new ConflictException("Este cliente já foi anonimizado.");
        }
        Object enderecoId = cliente.get("endereco_id");
        jdbc.update("""
                UPDATE cliente SET nome = :nome, cpf = NULL, data_nascimento = NULL, telefone = :telefone,
                       email = NULL, observacoes = NULL, endereco_id = NULL, ativo = FALSE
                 WHERE id = :id""", Map.of("id", id, "nome", PREFIXO_ANONIMO + id, "telefone", telefoneMarcador(id)));
        if (enderecoId != null) {
            // Sem DELETE: a linha fica, sem nenhum dado e desvinculada.
            jdbc.update("""
                    UPDATE endereco SET cep = '00000000', logradouro = 'Anonimizado', numero = NULL, complemento = NULL,
                           bairro = NULL, cidade = 'Anonimizado', uf = 'NI' WHERE id = :id""", Map.of("id", enderecoId));
        }
        jdbc.update("UPDATE pet SET observacoes = NULL WHERE cliente_id = :id", Map.of("id", id));
        auditoria.registrar("CLIENTE_ANONIMIZADO", ENTIDADE, id, Map.of("motivo", texto));
    }

    /** Telefone é NOT NULL/UNIQUE: 0 + id com zeros (11 dígitos, DDD 0 não existe). */
    static String telefoneMarcador(Long id) {
        return String.format("0%010d", id);
    }

    private Map<String, Object> cliente(Long id) {
        List<Map<String, Object>> r = jdbc.queryForList("""
                SELECT id, nome, cpf, data_nascimento, telefone, email, endereco_id, observacoes, ativo, created_at
                  FROM cliente WHERE id = :id""", Map.of("id", id));
        if (r.isEmpty()) {
            throw new NotFoundException("Cliente", id);
        }
        return new LinkedHashMap<>(r.get(0));
    }
}
