package br.com.tecnodreamer.petconnect.pet.application;

import br.com.tecnodreamer.petconnect.pet.domain.HistoricoConsulta;
import br.com.tecnodreamer.petconnect.pet.domain.Pet;
import br.com.tecnodreamer.petconnect.pet.infrastructure.HistoricoConsultaRepository;
import br.com.tecnodreamer.petconnect.pet.infrastructure.PetRepository;
import br.com.tecnodreamer.petconnect.pet.web.ConsultaRequest;
import br.com.tecnodreamer.petconnect.pet.web.ConsultaResponse;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class HistoricoConsultaService {

    private final PetRepository pets;
    private final HistoricoConsultaRepository consultas;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public HistoricoConsultaService(PetRepository pets, HistoricoConsultaRepository consultas,
            AuditoriaService auditoria, Clock clock) {
        this.pets = pets;
        this.consultas = consultas;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /** Da mais recente para a mais antiga. */
    @Transactional(readOnly = true)
    public List<ConsultaResponse> listar(Long petId) {
        petAtivo(petId);
        return consultas.findByPetIdOrderByDataConsultaDescIdDesc(petId).stream().map(ConsultaResponse::of).toList();
    }

    @Transactional
    public ConsultaResponse registrar(Long petId, ConsultaRequest req) {
        Pet pet = petAtivo(petId);
        if (req.dataConsulta().isAfter(LocalDateTime.now(clock))) {
            throw new CampoInvalidoException("dataConsulta", "A data da consulta não pode ser futura.");
        }
        HistoricoConsulta salva = consultas.save(new HistoricoConsulta(pet, req.dataConsulta(),
                req.veterinario().trim(), Textos.limpo(req.motivo()), Textos.limpo(req.diagnostico()),
                Textos.limpo(req.tratamento()), Textos.limpo(req.observacoes())));
        auditoria.registrar("CONSULTA_PET_REGISTRADA", PetService.ENTIDADE, petId,
                Map.of("consultaId", String.valueOf(salva.getId()), "veterinario", salva.getVeterinario()));
        return ConsultaResponse.of(salva);
    }

    private Pet petAtivo(Long petId) {
        return pets.findById(petId).filter(Pet::isAtivo).orElseThrow(() -> new NotFoundException("Pet", petId));
    }
}
