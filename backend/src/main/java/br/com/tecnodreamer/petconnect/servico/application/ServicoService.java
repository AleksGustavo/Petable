package br.com.tecnodreamer.petconnect.servico.application;

import br.com.tecnodreamer.petconnect.pet.domain.Porte;
import br.com.tecnodreamer.petconnect.servico.domain.Servico;
import br.com.tecnodreamer.petconnect.servico.infrastructure.ServicoRepository;
import br.com.tecnodreamer.petconnect.servico.web.PrecoPorteRequest;
import br.com.tecnodreamer.petconnect.servico.web.ServicoRequest;
import br.com.tecnodreamer.petconnect.servico.web.ServicoResponse;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class ServicoService {

    static final String ENTIDADE = "servico";

    private final ServicoRepository servicos;
    private final AuditoriaService auditoria;

    public ServicoService(ServicoRepository servicos, AuditoriaService auditoria) {
        this.servicos = servicos;
        this.auditoria = auditoria;
    }

    /** {@code ativo} nulo lista todos (tela administrativa); {@code true} alimenta os selects de agendamento. */
    @Transactional(readOnly = true)
    public List<ServicoResponse> listar(Boolean ativo) {
        List<Servico> lista = ativo == null ? servicos.findAllByOrderByNomeAsc() : servicos.findByAtivoOrderByNomeAsc(ativo);
        return lista.stream().map(ServicoResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public ServicoResponse buscar(Long id) {
        return ServicoResponse.of(carregar(id));
    }

    @Transactional
    public ServicoResponse criar(ServicoRequest req) {
        String nome = req.nome().trim();
        if (servicos.existsByNomeIgnoreCase(nome)) {
            throw new ConflictException("Já existe um serviço com este nome.");
        }
        Servico salvo = servicos.saveAndFlush(new Servico(nome, limpo(req.descricao()), req.preco(),
                req.duracaoMinutos(), precos(req.precosPorPorte())));
        auditoria.registrar("SERVICO_CRIADO", ENTIDADE, salvo.getId(), Map.of("nome", nome, "preco", req.preco()));
        return ServicoResponse.of(salvo);
    }

    /** Não recalcula agendamentos existentes: o valor deles é um snapshot. */
    @Transactional
    public ServicoResponse atualizar(Long id, ServicoRequest req) {
        Servico servico = carregar(id);
        String nome = req.nome().trim();
        if (servicos.existsByNomeIgnoreCaseAndIdNot(nome, id)) {
            throw new ConflictException("Já existe outro serviço com este nome.");
        }
        servico.atualizar(nome, limpo(req.descricao()), req.preco(), req.duracaoMinutos(), precos(req.precosPorPorte()));
        Servico salvo = servicos.saveAndFlush(servico);
        auditoria.registrar("SERVICO_ATUALIZADO", ENTIDADE, id, Map.of("nome", nome, "preco", req.preco()));
        return ServicoResponse.of(salvo);
    }

    /** Exclusão lógica: agendamentos existentes continuam válidos. */
    @Transactional
    public void desativar(Long id) {
        Servico servico = carregar(id);
        servico.desativar();
        auditoria.registrar("SERVICO_DESATIVADO", ENTIDADE, id, Map.of("nome", servico.getNome()));
    }

    private Servico carregar(Long id) {
        return servicos.findById(id).orElseThrow(() -> new NotFoundException("Serviço", id));
    }

    private static Map<Porte, BigDecimal> precos(List<PrecoPorteRequest> lista) {
        Map<Porte, BigDecimal> mapa = new EnumMap<>(Porte.class);
        for (PrecoPorteRequest p : lista == null ? List.<PrecoPorteRequest>of() : lista) {
            if (mapa.put(p.porte(), p.preco()) != null) {
                throw new CampoInvalidoException("precosPorPorte", "Informe no máximo um preço por porte.");
            }
        }
        return mapa;
    }

    private static String limpo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
