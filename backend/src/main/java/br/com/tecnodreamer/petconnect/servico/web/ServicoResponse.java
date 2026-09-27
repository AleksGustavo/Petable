package br.com.tecnodreamer.petconnect.servico.web;

import br.com.tecnodreamer.petconnect.pet.domain.Porte;
import br.com.tecnodreamer.petconnect.servico.domain.Servico;

import java.math.BigDecimal;
import java.util.List;

public record ServicoResponse(Long id, String nome, String descricao, BigDecimal preco, Integer duracaoMinutos,
        boolean ativo, boolean vacinacao, List<PrecoPorte> precosPorPorte) {

    public record PrecoPorte(Porte porte, BigDecimal preco) {
    }

    public static ServicoResponse of(Servico s) {
        return new ServicoResponse(s.getId(), s.getNome(), s.getDescricao(), s.getPreco(), s.getDuracaoMinutos(),
                s.isAtivo(), s.isVacinacao(),
                s.getPrecos().stream().map(p -> new PrecoPorte(p.getPorte(), p.getPreco())).toList());
    }
}
