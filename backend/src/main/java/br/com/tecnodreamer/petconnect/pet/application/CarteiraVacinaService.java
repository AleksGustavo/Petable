package br.com.tecnodreamer.petconnect.pet.application;

import br.com.tecnodreamer.petconnect.pet.domain.CarteiraVacina;
import br.com.tecnodreamer.petconnect.pet.domain.Pet;
import br.com.tecnodreamer.petconnect.pet.domain.Vacina;
import br.com.tecnodreamer.petconnect.pet.infrastructure.CarteiraVacinaRepository;
import br.com.tecnodreamer.petconnect.pet.infrastructure.PetRepository;
import br.com.tecnodreamer.petconnect.pet.infrastructure.VacinaRepository;
import br.com.tecnodreamer.petconnect.pet.web.LancamentoVacinaResponse;
import br.com.tecnodreamer.petconnect.pet.web.VacinaLancamentoRequest;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class CarteiraVacinaService {

    private final PetRepository pets;
    private final CarteiraVacinaRepository carteira;
    private final VacinaRepository vacinas;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public CarteiraVacinaService(PetRepository pets, CarteiraVacinaRepository carteira, VacinaRepository vacinas,
            AuditoriaService auditoria, Clock clock) {
        this.pets = pets;
        this.carteira = carteira;
        this.vacinas = vacinas;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<LancamentoVacinaResponse> listar(Long petId) {
        petAtivo(petId);
        LocalDate hoje = LocalDate.now(clock);
        return carteira.findByPetIdOrderByDataAplicacaoDescIdDesc(petId).stream()
                .map(l -> LancamentoVacinaResponse.of(l, hoje)).toList();
    }

    @Transactional
    public LancamentoVacinaResponse registrar(Long petId, VacinaLancamentoRequest req) {
        Pet pet = petAtivo(petId);
        Vacina vacina = vacinas.findById(req.vacinaId())
                .orElseThrow(() -> new CampoInvalidoException("vacinaId", "Vacina não encontrada."));
        if (!vacina.isAtivo()) {
            throw new ConflictException("Esta vacina está inativa no catálogo.");
        }
        if (!vacina.indicadaPara(pet.getEspecie())) {
            throw new ConflictException("A vacina selecionada não é indicada para a espécie deste pet.");
        }
        LocalDate hoje = LocalDate.now(clock);
        if (req.dataAplicacao().isAfter(hoje)) {
            throw new CampoInvalidoException("dataAplicacao", "A data de aplicação não pode ser futura.");
        }
        if (req.dataProxima() != null && req.dataProxima().isBefore(req.dataAplicacao())) {
            throw new CampoInvalidoException("dataProxima", "A próxima dose não pode ser anterior à aplicação.");
        }
        CarteiraVacina salvo = carteira.save(new CarteiraVacina(pet, vacina, req.dataAplicacao(), req.dataProxima(),
                Textos.limpo(req.veterinario()), Textos.limpo(req.lote()), Textos.limpo(req.observacoes())));
        auditoria.registrar("VACINA_PET_REGISTRADA", PetService.ENTIDADE, petId,
                Map.of("lancamentoId", String.valueOf(salvo.getId()), "vacina", vacina.getNome()));
        return LancamentoVacinaResponse.of(salvo, hoje);
    }

    /** Só remove se o lançamento pertencer ao pet da URL; senão responde 404. */
    @Transactional
    public void remover(Long petId, Long lancamentoId) {
        CarteiraVacina lancamento = carteira.findById(lancamentoId)
                .filter(l -> l.pertenceAoPet(petId))
                .orElseThrow(() -> new NotFoundException("Lançamento de vacina", lancamentoId));
        carteira.delete(lancamento);
        auditoria.registrar("VACINA_PET_REMOVIDA", PetService.ENTIDADE, petId,
                Map.of("lancamentoId", String.valueOf(lancamentoId), "vacina", lancamento.getVacina().getNome()));
    }

    private Pet petAtivo(Long petId) {
        return pets.findById(petId).filter(Pet::isAtivo).orElseThrow(() -> new NotFoundException("Pet", petId));
    }
}
