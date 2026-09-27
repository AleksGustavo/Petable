package br.com.tecnodreamer.petconnect.agendamento.web;

import br.com.tecnodreamer.petconnect.agendamento.domain.Agendamento;
import br.com.tecnodreamer.petconnect.agendamento.domain.ServicoRealizado;
import br.com.tecnodreamer.petconnect.agendamento.domain.StatusAgendamento;
import br.com.tecnodreamer.petconnect.pet.web.CatalogoItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AgendamentoResponse(Long id, LocalDateTime dataHora, LocalDateTime fimPrevisto,
        StatusAgendamento status, String statusDescricao, Pet pet, CatalogoItem tutor, Servico servico,
        Funcionario funcionario, BigDecimal totalEstimado, String observacoes, Realizado realizado) {

    /** {@code disponivel=false}: conta do funcionário desativada — reatribuição necessária (DA-03). */
    public record Funcionario(Long id, String nome, boolean disponivel) {
    }

    public record Pet(Long id, String nome, String porte, Long especieId) {
    }

    public record Servico(Long id, String nome, Integer duracaoMinutos, boolean vacinacao) {
    }

    /** Preenchido só no detalhe de um agendamento concluído. */
    public record Realizado(LocalDateTime dataServico, BigDecimal valor, String observacoes) {
    }

    public static AgendamentoResponse of(Agendamento a) {
        return of(a, null);
    }

    public static AgendamentoResponse of(Agendamento a, ServicoRealizado realizado) {
        var p = a.getPet();
        var s = a.getServico();
        var c = p.getCliente();
        return new AgendamentoResponse(a.getId(), a.getDataHora(), a.fimPrevisto(), a.getStatus(),
                a.getStatus().getDescricao(),
                new Pet(p.getId(), p.getNome(), p.getPorte() == null ? null : p.getPorte().name(), p.getEspecie().getId()),
                new CatalogoItem(c.getId(), c.getNome()),
                new Servico(s.getId(), s.getNome(), s.getDuracaoMinutos(), s.isVacinacao()),
                a.getFuncionario() == null ? null
                        : new Funcionario(a.getFuncionario().getId(), a.getFuncionario().getNome(), a.getFuncionario().isDisponivel()),
                a.getTotalEstimado(), a.getObservacoes(),
                realizado == null ? null
                        : new Realizado(realizado.getDataServico(), realizado.getValor(), realizado.getObservacoes()));
    }
}
