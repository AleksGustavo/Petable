package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.migracao.infrastructure.DestinoMigracao;
import br.com.tecnodreamer.petconnect.shared.validation.Documentos;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Funcionários: CPF é NOT NULL no schema novo — inválido ou repetido → IGNORADO (DA-19). */
@Component
@Order(40)
public class MigradorFuncionario implements Migrador {

    private final DestinoMigracao destino;
    private final EnderecoLegado enderecos;

    public MigradorFuncionario(DestinoMigracao destino, EnderecoLegado enderecos) {
        this.destino = destino;
        this.enderecos = enderecos;
    }

    @Override
    public String entidade() {
        return "funcionario";
    }

    @Override
    public String consulta() {
        return """
                SELECT id, usuario_id, nome, cpf, data_nascimento, sexo, telefone, cep, rua, bairro, numero,
                       complemento, observacoes
                  FROM funcionario WHERE id > :ultimo ORDER BY id LIMIT :lote
                """;
    }

    @Override
    public Desfecho migrar(Linha l, Contexto ctx) {
        Optional<Long> usuario = ctx.idNovo("usuario", l.longo("usuario_id"));
        if (usuario.isEmpty()) {
            return Desfecho.ignorado("USUARIO_NAO_MIGRADO");
        }
        if (destino.funcionarioDoUsuarioExiste(usuario.get())) {
            return Desfecho.ignorado("USUARIO_JA_TEM_FUNCIONARIO");
        }
        String cpf = Documentos.somenteDigitos(l.texto("cpf"));
        if (!Documentos.cpfValido(cpf)) {
            return Desfecho.ignorado("DOCUMENTO_INVALIDO_SCHEMA_OBRIGATORIO");
        }
        if (destino.cpfFuncionarioExiste(cpf)) {
            return Desfecho.ignorado("DOCUMENTO_DUPLICADO_SCHEMA_OBRIGATORIO");
        }
        List<String> pend = new ArrayList<>();
        String telefone = Campos.telefone(l.texto("telefone"));
        if (telefone == null && l.texto("telefone") != null) {
            pend.add("TELEFONE_INVALIDO");
        } else if (telefone != null && destino.telefoneFuncionarioExiste(telefone)) {
            pend.add("TELEFONE_DUPLICADO");
            telefone = null;
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("usuario_id", usuario.get());
        v.put("nome", TextoNormalizado.limitar(l.texto("nome"), 100));
        v.put("cpf", cpf);
        v.put("data_nascimento", l.data("data_nascimento"));
        v.put("sexo", ConversorLegado.sexo(l.texto("sexo")).anotar(pend).name());
        v.put("telefone", telefone);
        v.put("endereco_id", enderecos.criar(l, null, ctx.opcoes().viaCep(), pend).orElse(null));
        v.put("observacoes", l.texto("observacoes"));
        return Desfecho.migrado(destino.inserir("funcionario", v), pend);
    }
}
