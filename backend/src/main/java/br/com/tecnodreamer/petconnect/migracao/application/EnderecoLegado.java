package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.migracao.infrastructure.DestinoMigracao;
import br.com.tecnodreamer.petconnect.shared.validation.Documentos;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Endereço embutido no legado → linha em {@code endereco} (DA-17). A tabela nova exige
 * CEP, logradouro, cidade e UF. Sem CEP válido ou sem rua, não há endereço (pendência).
 * Cidade não existe no legado: vem do ViaCEP quando habilitado; senão fica
 * "Não informada" (e UF "NI" se também desconhecida), sempre com pendência — o cadastro
 * pede correção na primeira edição.
 */
@Component
public class EnderecoLegado {

    static final String CIDADE_NAO_INFORMADA = "Não informada";
    static final String UF_NAO_INFORMADA = "NI";

    private final DestinoMigracao destino;
    private final ConsultaCep cep;

    public EnderecoLegado(DestinoMigracao destino, ConsultaCep cep) {
        this.destino = destino;
        this.cep = cep;
    }

    public Optional<Long> criar(Linha l, String estado, boolean viaCep, List<String> pendencias) {
        String rua = l.texto("rua");
        String cepLegado = l.texto("cep");
        boolean algum = cepLegado != null || rua != null || l.texto("bairro") != null || l.texto("numero") != null
                || l.texto("complemento") != null || estado != null;
        if (!algum) {
            return Optional.empty();
        }
        if (!Documentos.cepValido(cepLegado) || rua == null) {
            pendencias.add("ENDERECO_INCOMPLETO");
            return Optional.empty();
        }
        String digitos = Documentos.somenteDigitos(cepLegado);
        String cidade = null;
        String uf = Ufs.sigla(estado).orElse(null);
        if (viaCep) {
            try {
                Optional<ConsultaCep.Localidade> loc = cep.consultar(digitos);
                if (loc.isPresent()) {
                    cidade = TextoNormalizado.limitar(loc.get().cidade(), 100);
                    if (uf == null) {
                        uf = Ufs.sigla(loc.get().uf()).orElse(null);
                    }
                }
            } catch (RuntimeException e) {
                pendencias.add("VIACEP_INDISPONIVEL");
            }
        }
        if (cidade == null) {
            cidade = CIDADE_NAO_INFORMADA;
            pendencias.add("ENDERECO_CIDADE_NAO_INFORMADA");
        }
        if (uf == null) {
            uf = UF_NAO_INFORMADA;
            pendencias.add("ENDERECO_UF_NAO_INFORMADA");
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("cep", digitos);
        v.put("logradouro", TextoNormalizado.limitar(rua, 150));
        v.put("numero", TextoNormalizado.limitar(l.texto("numero"), 20));
        v.put("complemento", TextoNormalizado.limitar(l.texto("complemento"), 100));
        v.put("bairro", TextoNormalizado.limitar(l.texto("bairro"), 100));
        v.put("cidade", cidade);
        v.put("uf", uf);
        return Optional.of(destino.inserir("endereco", v));
    }
}
