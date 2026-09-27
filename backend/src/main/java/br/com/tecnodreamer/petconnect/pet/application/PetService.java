package br.com.tecnodreamer.petconnect.pet.application;

import br.com.tecnodreamer.petconnect.cliente.domain.Cliente;
import br.com.tecnodreamer.petconnect.cliente.infrastructure.ClienteRepository;
import br.com.tecnodreamer.petconnect.pet.domain.DadosPet;
import br.com.tecnodreamer.petconnect.pet.domain.Especie;
import br.com.tecnodreamer.petconnect.pet.domain.Pet;
import br.com.tecnodreamer.petconnect.pet.domain.Raca;
import br.com.tecnodreamer.petconnect.pet.infrastructure.EspecieRepository;
import br.com.tecnodreamer.petconnect.pet.infrastructure.PetRepository;
import br.com.tecnodreamer.petconnect.pet.infrastructure.RacaRepository;
import br.com.tecnodreamer.petconnect.pet.web.PetRequest;
import br.com.tecnodreamer.petconnect.pet.web.PetResponse;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

@Service
public class PetService {

    static final String ENTIDADE = "pet";

    private final PetRepository pets;
    private final ClienteRepository clientes;
    private final EspecieRepository especies;
    private final RacaRepository racas;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public PetService(PetRepository pets, ClienteRepository clientes, EspecieRepository especies,
            RacaRepository racas, AuditoriaService auditoria, Clock clock) {
        this.pets = pets;
        this.clientes = clientes;
        this.especies = especies;
        this.racas = racas;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<PetResponse> listar(String busca, Long clienteId, Pageable pageable) {
        LocalDate hoje = LocalDate.now(clock);
        return pets.buscarAtivos(Textos.limpo(busca), clienteId, pageable).map(p -> PetResponse.of(p, hoje));
    }

    @Transactional(readOnly = true)
    public PetResponse buscar(Long id) {
        return PetResponse.of(carregar(id), LocalDate.now(clock));
    }

    @Transactional
    public PetResponse criar(PetRequest req) {
        Pet salvo = pets.saveAndFlush(new Pet(resolver(req)));
        auditoria.registrar("PET_CRIADO", ENTIDADE, salvo.getId(),
                Map.of("nome", salvo.getNome(), "clienteId", salvo.getCliente().getId()));
        return PetResponse.of(salvo, LocalDate.now(clock));
    }

    @Transactional
    public PetResponse atualizar(Long id, PetRequest req) {
        Pet pet = carregar(id);
        pet.atualizar(resolver(req));
        Pet salvo = pets.saveAndFlush(pet);
        auditoria.registrar("PET_ATUALIZADO", ENTIDADE, id, Map.of("nome", salvo.getNome()));
        return PetResponse.of(salvo, LocalDate.now(clock));
    }

    /** Exclusão lógica: carteira e consultas continuam no banco. */
    @Transactional
    public void desativar(Long id) {
        Pet pet = carregar(id);
        pet.desativar();
        auditoria.registrar("PET_DESATIVADO", ENTIDADE, id, Map.of("nome", pet.getNome()));
    }

    private Pet carregar(Long id) {
        return pets.findWithDetalhesById(id).filter(Pet::isAtivo).orElseThrow(() -> new NotFoundException("Pet", id));
    }

    private DadosPet resolver(PetRequest req) {
        Cliente tutor = clientes.findById(req.clienteId())
                .filter(Cliente::isAtivo)
                .orElseThrow(() -> new CampoInvalidoException("clienteId", "Tutor não encontrado ou inativo."));
        Especie especie = especies.findById(req.especieId())
                .orElseThrow(() -> new CampoInvalidoException("especieId", "Espécie não encontrada."));
        Raca raca = null;
        if (req.racaId() != null) {
            raca = racas.findById(req.racaId())
                    .orElseThrow(() -> new CampoInvalidoException("racaId", "Raça não encontrada."));
            if (!raca.pertenceA(especie)) {
                throw new ConflictException("A raça informada não pertence à espécie selecionada.");
            }
        }
        if (req.dataNascimento() != null && req.dataNascimento().isAfter(LocalDate.now(clock))) {
            throw new CampoInvalidoException("dataNascimento", "A data de nascimento não pode ser futura.");
        }
        return new DadosPet(tutor, req.nome().trim(), especie, raca, req.dataNascimento(), req.peso(),
                Textos.limpo(req.cor()), req.porte(), Boolean.TRUE.equals(req.castrado()),
                Textos.limpo(req.observacoes()));
    }
}
