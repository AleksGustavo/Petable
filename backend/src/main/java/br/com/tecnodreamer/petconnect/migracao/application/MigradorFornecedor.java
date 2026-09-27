package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.migracao.infrastructure.DestinoMigracao;
import br.com.tecnodreamer.petconnect.shared.validation.Documentos;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fornecedores: CNPJ é NOT NULL no schema novo — inválido ou repetido → IGNORADO. */
@Component
@Order(50)
public class MigradorFornecedor implements Migrador {

    private final DestinoMigracao destino;
    private final EnderecoLegado enderecos;

    public MigradorFornecedor(DestinoMigracao destino, EnderecoLegado enderecos) {
        this.destino = destino;
        this.enderecos = enderecos;
    }

    @Override
    public String entidade() {
        return "fornecedor";
    }

    @Override
    public String consulta() {
        return """
                SELECT id, nome_fantasia, razao_social, cnpj, telefone, email, contato, cep, rua, bairro, numero,
                       complemento, observacoes, ativo
                  FROM fornecedor WHERE id > :ultimo ORDER BY id LIMIT :lote
                """;
    }

    @Override
    public Desfecho migrar(Linha l, Contexto ctx) {
        String cnpj = Documentos.somenteDigitos(l.texto("cnpj"));
        if (!Documentos.cnpjValido(cnpj)) {
            return Desfecho.ignorado("DOCUMENTO_INVALIDO_SCHEMA_OBRIGATORIO");
        }
        if (destino.cnpjFornecedorExiste(cnpj)) {
            return Desfecho.ignorado("DOCUMENTO_DUPLICADO_SCHEMA_OBRIGATORIO");
        }
        List<String> pend = new ArrayList<>();
        String telefone = Campos.telefone(l.texto("telefone"));
        if (telefone == null && l.texto("telefone") != null) {
            pend.add("TELEFONE_INVALIDO");
        } else if (telefone != null && destino.telefoneFornecedorExiste(telefone)) {
            pend.add("TELEFONE_DUPLICADO");
            telefone = null;
        }
        String email = Campos.email(l.texto("email"));
        if (email == null && l.texto("email") != null) {
            pend.add("EMAIL_INVALIDO");
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("nome_fantasia", TextoNormalizado.limitar(l.texto("nome_fantasia"), 100));
        v.put("razao_social", TextoNormalizado.limitar(l.texto("razao_social"), 150));
        v.put("cnpj", cnpj);
        v.put("telefone", telefone);
        v.put("email", email);
        v.put("contato", TextoNormalizado.limitar(l.texto("contato"), 100));
        v.put("endereco_id", enderecos.criar(l, null, ctx.opcoes().viaCep(), pend).orElse(null));
        v.put("observacoes", l.texto("observacoes"));
        v.put("ativo", Campos.ativo(l, pend));
        return Desfecho.migrado(destino.inserir("fornecedor", v), pend);
    }
}
