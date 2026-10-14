package br.com.tecnodreamer.petconnect.pet.application;

import br.com.tecnodreamer.petconnect.pet.domain.CarteiraVacina;
import br.com.tecnodreamer.petconnect.pet.domain.Pet;
import br.com.tecnodreamer.petconnect.pet.domain.Vacina;
import br.com.tecnodreamer.petconnect.pet.infrastructure.CarteiraVacinaRepository;
import br.com.tecnodreamer.petconnect.pet.infrastructure.PetRepository;
import br.com.tecnodreamer.petconnect.pet.infrastructure.VacinaRepository;
import br.com.tecnodreamer.petconnect.pet.web.LancamentoVacinaResponse;
import br.com.tecnodreamer.petconnect.pet.web.VacinaLancamentoRequest;
import br.com.tecnodreamer.petconnect.produto.application.EstoqueLoteService;
import br.com.tecnodreamer.petconnect.produto.domain.LoteEstoque;
import br.com.tecnodreamer.petconnect.shared.security.UsuarioAtual;
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
    private final EstoqueLoteService estoqueLotes;

    public CarteiraVacinaService(PetRepository pets, CarteiraVacinaRepository carteira, VacinaRepository vacinas,
            AuditoriaService auditoria, Clock clock, EstoqueLoteService estoqueLotes) {
        this.estoqueLotes = estoqueLotes;
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

    /** Usa o usuário da requisição (JWT) quando a aplicação baixa estoque. */
    @Transactional
    public LancamentoVacinaResponse registrar(Long petId, VacinaLancamentoRequest req) {
        return registrar(petId, req, UsuarioAtual.id().orElse(null));
    }

    /**
     * Registra a dose. Vacina ligada a um produto com controle de lote (e a flag de lotes ligada) baixa UMA unidade do lote
     * que vence primeiro e grava esse lote físico na carteira: o lote digitado à mão deixa de valer (o estoque é a fonte).
     * Sem lote disponível, 409 e nada é gravado. Vacina sem produto ligado segue como antes.
     */
    @Transactional
    public LancamentoVacinaResponse registrar(Long petId, VacinaLancamentoRequest req, Long usuarioId) {
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
        boolean baixaEstoque = estoqueLotes.consumoClinicoAtivo(vacina.getProduto());
        if (baixaEstoque && Textos.limpo(req.lote()) != null) {
            throw new CampoInvalidoException("lote", "O lote desta vacina é escolhido pelo estoque: deixe o campo em branco.");
        }
        if (baixaEstoque && usuarioId == null) {
            throw new CampoInvalidoException("usuario", "Não foi possível identificar o usuário que aplicou a vacina.");
        }
        CarteiraVacina salvo = carteira.save(new CarteiraVacina(pet, vacina, req.dataAplicacao(), req.dataProxima(),
                Textos.limpo(req.veterinario()), Textos.limpo(req.lote()), Textos.limpo(req.observacoes())));
        Map<String, Object> detalhes = new java.util.LinkedHashMap<>();
        detalhes.put("lancamentoId", String.valueOf(salvo.getId()));
        detalhes.put("vacina", vacina.getNome());
        if (baixaEstoque) {
            // O movimento cita só o número do lançamento: nada do pet nem do tutor vai para o estoque.
            LoteEstoque lote = estoqueLotes.baixarConsumoClinico(vacina.getProduto().getId(),
                    "Aplicação de vacina (lançamento " + salvo.getId() + ")", usuarioId);
            salvo.registrarLoteFisico(lote);
            detalhes.put("loteId", String.valueOf(lote.getId()));
            detalhes.put("codigoLote", lote.getCodigoLote());
        }
        auditoria.registrar("VACINA_PET_REGISTRADA", PetService.ENTIDADE, petId, detalhes);
        return LancamentoVacinaResponse.of(salvo, hoje);
    }

    /** Só remove se o lançamento pertencer ao pet da URL; senão responde 404. */
    @Transactional
    public void remover(Long petId, Long lancamentoId) {
        CarteiraVacina lancamento = carteira.findById(lancamentoId)
                .filter(l -> l.pertenceAoPet(petId))
                .orElseThrow(() -> new NotFoundException("Lançamento de vacina", lancamentoId));
        carteira.delete(lancamento);
        Map<String, Object> detalhes = new java.util.LinkedHashMap<>();
        detalhes.put("lancamentoId", String.valueOf(lancamentoId));
        detalhes.put("vacina", lancamento.getVacina().getNome());
        if (lancamento.getLoteEstoque() != null) {
            // Corrigir o registro na carteira não devolve a dose ao estoque: ela foi fisicamente usada. Fica dito na auditoria.
            detalhes.put("loteId", String.valueOf(lancamento.getLoteEstoque().getId()));
            detalhes.put("estoqueNaoRestituido", true);
        }
        auditoria.registrar("VACINA_PET_REMOVIDA", PetService.ENTIDADE, petId, detalhes);
    }

    private Pet petAtivo(Long petId) {
        return pets.findById(petId).filter(Pet::isAtivo).orElseThrow(() -> new NotFoundException("Pet", petId));
    }
}
