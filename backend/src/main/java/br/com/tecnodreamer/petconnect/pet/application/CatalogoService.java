package br.com.tecnodreamer.petconnect.pet.application;

import br.com.tecnodreamer.petconnect.pet.infrastructure.EspecieRepository;
import br.com.tecnodreamer.petconnect.pet.infrastructure.RacaRepository;
import br.com.tecnodreamer.petconnect.pet.infrastructure.VacinaRepository;
import br.com.tecnodreamer.petconnect.pet.web.CatalogoItem;
import br.com.tecnodreamer.petconnect.pet.web.VacinaCatalogoResponse;
import br.com.tecnodreamer.petconnect.produto.application.EstoqueLoteService;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CatalogoService {

    private final EspecieRepository especies;
    private final RacaRepository racas;
    private final VacinaRepository vacinas;
    private final EstoqueLoteService estoqueLotes;

    public CatalogoService(EspecieRepository especies, RacaRepository racas, VacinaRepository vacinas, EstoqueLoteService estoqueLotes) {
        this.estoqueLotes = estoqueLotes;
        this.especies = especies;
        this.racas = racas;
        this.vacinas = vacinas;
    }

    public List<CatalogoItem> especies() {
        return especies.findAllByOrderByNomeAsc().stream().map(e -> new CatalogoItem(e.getId(), e.getNome())).toList();
    }

    public List<CatalogoItem> racas(Long especieId) {
        exigirEspecie(especieId);
        return racas.findByEspecieIdOrderByNomeAsc(especieId).stream()
                .map(r -> new CatalogoItem(r.getId(), r.getNome())).toList();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<VacinaCatalogoResponse> vacinas(Long especieId) {
        exigirEspecie(especieId);
        return vacinas.findByEspecieIdAndAtivoTrueOrderByNomeAsc(especieId).stream()
                .map(v -> VacinaCatalogoResponse.of(v, estoqueLotes.consumoClinicoAtivo(v.getProduto()))).toList();
    }

    private void exigirEspecie(Long especieId) {
        if (!especies.existsById(especieId)) {
            throw new NotFoundException("Espécie", especieId);
        }
    }
}
