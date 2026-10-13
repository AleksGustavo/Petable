package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.migracao.infrastructure.DestinoMigracao;
import br.com.tecnodreamer.petconnect.shared.validation.Documentos;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Clientes (DA-17, DA-19). Telefone é NOT NULL/UNIQUE no schema novo: sem telefone
 * válido ou repetido, o cliente é IGNORADO (nunca se inventa telefone). CPF inválido ou
 * repetido vira NULL + pendência e uma observação no próprio cadastro (sem o número).
 */
@Component
@Order(30)
public class MigradorCliente implements Migrador {

    static final String OBS_CPF_INVALIDO = "CPF informado no sistema antigo era inválido.";
    static final String OBS_CPF_DUPLICADO = "CPF informado no sistema antigo já pertencia a outro cliente.";

    private final DestinoMigracao destino;
    private final EnderecoLegado enderecos;

    public MigradorCliente(DestinoMigracao destino, EnderecoLegado enderecos) {
        this.destino = destino;
        this.enderecos = enderecos;
    }

    @Override
    public String entidade() {
        return "cliente";
    }

    @Override
    public String consulta() {
        return """
                SELECT id, nome, cpf, data_nascimento, telefone, email, cep, rua, bairro, numero, complemento,
                       observacoes, ativo, estado
                  FROM cliente WHERE id > :ultimo ORDER BY id LIMIT :lote
                """;
    }

    @Override
    public Desfecho migrar(Linha l, Contexto ctx) {
        String telefone = Campos.telefone(l.texto("telefone"));
        if (telefone == null) {
            return Desfecho.ignorado("TELEFONE_INVALIDO_SCHEMA_OBRIGATORIO");
        }
        if (destino.telefoneClienteExiste(telefone)) {
            return Desfecho.ignorado("TELEFONE_DUPLICADO_SCHEMA_OBRIGATORIO");
        }
        List<String> pend = new ArrayList<>();
        String observacoes = l.texto("observacoes");
        String cpf = Documentos.somenteDigitos(l.texto("cpf"));
        if (cpf != null && !Documentos.cpfValido(cpf)) {
            pend.add("CPF_INVALIDO");
            observacoes = Campos.observacao(observacoes, OBS_CPF_INVALIDO);
            cpf = null;
        } else if (cpf != null && destino.cpfClienteExiste(cpf)) {
            pend.add("CPF_DUPLICADO");
            observacoes = Campos.observacao(observacoes, OBS_CPF_DUPLICADO);
            cpf = null;
        }
        String email = Campos.email(l.texto("email"));
        if (email == null && l.texto("email") != null) {
            pend.add("EMAIL_INVALIDO");
        }
        Long enderecoId = enderecos.criar(l, l.texto("estado"), ctx.opcoes().viaCep(), pend).orElse(null);
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("nome", TextoNormalizado.limitar(l.texto("nome"), 100));
        v.put("cpf", cpf);
        v.put("data_nascimento", l.data("data_nascimento"));
        v.put("telefone", telefone);
        v.put("email", email);
        v.put("endereco_id", enderecoId);
        v.put("observacoes", observacoes);
        v.put("ativo", Campos.ativo(l, pend));
        return Desfecho.migrado(destino.inserir("cliente", v), pend);
    }
}
