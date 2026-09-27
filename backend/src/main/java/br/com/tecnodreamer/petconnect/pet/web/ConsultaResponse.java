package br.com.tecnodreamer.petconnect.pet.web;

import br.com.tecnodreamer.petconnect.pet.domain.HistoricoConsulta;

import java.time.Instant;
import java.time.LocalDateTime;

public record ConsultaResponse(Long id, LocalDateTime dataConsulta, String veterinario, String motivo,
        String diagnostico, String tratamento, String observacoes, Instant createdAt) {

    public static ConsultaResponse of(HistoricoConsulta h) {
        return new ConsultaResponse(h.getId(), h.getDataConsulta(), h.getVeterinario(), h.getMotivo(),
                h.getDiagnostico(), h.getTratamento(), h.getObservacoes(), h.getCreatedAt());
    }
}
