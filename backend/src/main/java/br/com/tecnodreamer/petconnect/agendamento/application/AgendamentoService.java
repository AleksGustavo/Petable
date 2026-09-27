package br.com.tecnodreamer.petconnect.agendamento.application;

import br.com.tecnodreamer.petconnect.agendamento.domain.Agendamento;
import br.com.tecnodreamer.petconnect.agendamento.domain.ServicoRealizado;
import br.com.tecnodreamer.petconnect.agendamento.domain.StatusAgendamento;
import br.com.tecnodreamer.petconnect.agendamento.infrastructure.AgendamentoRepository;
import br.com.tecnodreamer.petconnect.agendamento.infrastructure.ServicoRealizadoRepository;
import br.com.tecnodreamer.petconnect.financeiro.application.CaixaService;
import br.com.tecnodreamer.petconnect.financeiro.application.FinanceiroService;
import br.com.tecnodreamer.petconnect.agendamento.web.AgendamentoRequest;
import br.com.tecnodreamer.petconnect.agendamento.web.AgendamentoResponse;
import br.com.tecnodreamer.petconnect.agendamento.web.PrecoResponse;
import br.com.tecnodreamer.petconnect.agendamento.web.StatusRequest;
import br.com.tecnodreamer.petconnect.funcionario.domain.Funcionario;
import br.com.tecnodreamer.petconnect.funcionario.infrastructure.FuncionarioRepository;
import br.com.tecnodreamer.petconnect.pet.application.CarteiraVacinaService;
import br.com.tecnodreamer.petconnect.pet.domain.Pet;
import br.com.tecnodreamer.petconnect.pet.infrastructure.PetRepository;
import br.com.tecnodreamer.petconnect.servico.domain.PrecoEstimado;
import br.com.tecnodreamer.petconnect.servico.domain.Servico;
import br.com.tecnodreamer.petconnect.servico.infrastructure.ServicoRepository;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static br.com.tecnodreamer.petconnect.agendamento.domain.StatusAgendamento.AGENDADO;
import static br.com.tecnodreamer.petconnect.agendamento.domain.StatusAgendamento.ATIVOS;
import static br.com.tecnodreamer.petconnect.agendamento.domain.StatusAgendamento.CANCELADO;
import static br.com.tecnodreamer.petconnect.agendamento.domain.StatusAgendamento.CONCLUIDO;
import static br.com.tecnodreamer.petconnect.agendamento.domain.StatusAgendamento.CONFIRMADO;
import static br.com.tecnodreamer.petconnect.agendamento.domain.StatusAgendamento.EM_ANDAMENTO;
import static br.com.tecnodreamer.petconnect.agendamento.domain.StatusAgendamento.NAO_COMPARECEU;

@Service
public class AgendamentoService {

    static final String ENTIDADE = "agendamento";
    /** Maior duração aceita para um serviço (ServicoRequest): limita a janela de busca de conflitos. */
    static final int DURACAO_MAXIMA_MINUTOS = 1440;
    static final int INTERVALO_MAXIMO_DIAS = 7;

    /** Quem pode executar cada transição (seção 13 da etapa 02). */
    private static final Map<StatusAgendamento, Set<String>> PAPEIS_POR_TRANSICAO = Map.of(
            CONFIRMADO, Set.of("ADMIN", "VENDAS"),
            CANCELADO, Set.of("ADMIN", "VENDAS"),
            EM_ANDAMENTO, Set.of("ADMIN", "VENDAS", "SERVICO"),
            CONCLUIDO, Set.of("ADMIN", "VENDAS", "SERVICO"),
            NAO_COMPARECEU, Set.of("ADMIN", "VENDAS", "SERVICO"));

    private final AgendamentoRepository agendamentos;
    private final PetRepository pets;
    private final ServicoRepository servicos;
    private final FuncionarioRepository funcionarios;
    private final ServicoRealizadoRepository realizados;
    private final CarteiraVacinaService carteira;
    private final AuditoriaService auditoria;
    private final CaixaService caixas;
    private final FinanceiroService financeiro;
    private final Clock clock;

    public AgendamentoService(AgendamentoRepository agendamentos, PetRepository pets, ServicoRepository servicos,
            FuncionarioRepository funcionarios, ServicoRealizadoRepository realizados, CarteiraVacinaService carteira,
            AuditoriaService auditoria, CaixaService caixas, FinanceiroService financeiro, Clock clock) {
        this.agendamentos = agendamentos;
        this.pets = pets;
        this.servicos = servicos;
        this.funcionarios = funcionarios;
        this.realizados = realizados;
        this.carteira = carteira;
        this.auditoria = auditoria;
        this.caixas = caixas;
        this.financeiro = financeiro;
        this.clock = clock;
    }

    // ------------------------------------------------------------ leitura

    @Transactional(readOnly = true)
    public Page<AgendamentoResponse> listar(FiltroAgenda f, Pageable pageable) {
        if (f.fim().isBefore(f.inicio())) {
            throw new CampoInvalidoException("fim", "A data final não pode ser anterior à inicial.");
        }
        if (ChronoUnit.DAYS.between(f.inicio(), f.fim()) >= INTERVALO_MAXIMO_DIAS) {
            throw new CampoInvalidoException("fim", "O intervalo máximo é de 7 dias.");
        }
        String termo = f.termo() == null || f.termo().isBlank() ? null : f.termo().trim();
        return agendamentos.buscar(f.inicio().atStartOfDay(), f.fim().plusDays(1).atStartOfDay(), f.status(),
                f.petId(), f.clienteId(), f.servicoId(), termo, pageable).map(AgendamentoResponse::of);
    }

    @Transactional(readOnly = true)
    public AgendamentoResponse buscar(Long id) {
        Agendamento a = carregar(id);
        return AgendamentoResponse.of(a, realizados.findByAgendamentoId(id).orElse(null));
    }

    @Transactional(readOnly = true)
    public PrecoResponse precoPrevisto(Long petId, Long servicoId) {
        Pet pet = petAtivo(petId);
        Servico servico = servicoAtivo(servicoId);
        PrecoEstimado preco = servico.estimarPara(pet.getPorte());
        return new PrecoResponse(petId, servicoId, pet.getPorte(), preco.origem(), preco.valor(),
                servico.getDuracaoMinutos());
    }

    @Transactional(readOnly = true)
    public List<AgendamentoResponse> proximos(Long petId, int limite) {
        petAtivo(petId);
        return agendamentos.proximos(petId, EM_ANDAMENTO, Set.of(AGENDADO, CONFIRMADO), LocalDateTime.now(clock),
                PageRequest.of(0, limite)).stream().map(AgendamentoResponse::of).toList();
    }

    // ------------------------------------------------------------ escrita

    @Transactional
    public AgendamentoResponse criar(AgendamentoRequest req) {
        Dados d = resolver(req, null);
        Agendamento salvo = agendamentos.saveAndFlush(new Agendamento(d.pet(), d.servico(), d.funcionario(),
                req.dataHora(), d.preco().valor(), limpo(req.observacoes())));
        auditoria.registrar("AGENDAMENTO_CRIADO", ENTIDADE, salvo.getId(), detalhes(salvo));
        return AgendamentoResponse.of(salvo);
    }

    @Transactional
    public AgendamentoResponse atualizar(Long id, AgendamentoRequest req) {
        Agendamento a = carregar(id);
        if (!a.getStatus().editavel()) {
            throw new ConflictException("Só é possível editar agendamentos com status Agendado ou Confirmado.");
        }
        Dados d = resolver(req, id);
        a.reagendar(d.pet(), d.servico(), d.funcionario(), req.dataHora(), d.preco().valor(), limpo(req.observacoes()));
        Agendamento salvo = agendamentos.saveAndFlush(a);
        auditoria.registrar("AGENDAMENTO_ATUALIZADO", ENTIDADE, id, detalhes(salvo));
        return AgendamentoResponse.of(salvo);
    }

    /**
     * Ordem da seção 15: carrega, confere papel, valida a transição, executa os
     * efeitos (serviço realizado, carteira), persiste e audita — tudo numa
     * transação: se a vacina falhar, nada é concluído.
     */
    @Transactional
    public AgendamentoResponse alterarStatus(Long id, StatusRequest req, Set<String> papeis, Long usuarioId) {
        Agendamento a = carregar(id);
        StatusAgendamento anterior = a.getStatus();
        StatusAgendamento novo = req.status();

        Set<String> permitidos = PAPEIS_POR_TRANSICAO.getOrDefault(novo, Set.of());
        if (papeis.stream().noneMatch(permitidos::contains)) {
            throw new AccessDeniedException("Papel sem permissão para a transição " + novo);
        }
        anterior.exigirTransicaoPara(novo);
        if (novo != CONCLUIDO && req.formaPagamento() != null) {
            throw new CampoInvalidoException("formaPagamento", "A forma de pagamento só é informada na conclusão.");
        }
        if (novo != CONCLUIDO && (req.valorFinal() != null || req.vacina() != null || req.observacoesConclusao() != null)) {
            throw new CampoInvalidoException(req.vacina() != null ? "vacina" : "valorFinal",
                    "Valor final, observações de conclusão e vacina só podem ser informados na conclusão.");
        }

        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("statusAnterior", anterior.name());
        detalhes.put("statusNovo", novo.name());
        String acao = switch (novo) {
            case CANCELADO -> {
                String motivo = limpo(req.motivo());
                if (motivo == null || motivo.length() < 3) {
                    throw new CampoInvalidoException("motivo", "Informe o motivo do cancelamento (mínimo 3 caracteres).");
                }
                a.anexarObservacao("CANCELAMENTO", motivo);
                detalhes.put("motivo", motivo);
                yield "AGENDAMENTO_CANCELADO";
            }
            case NAO_COMPARECEU -> {
                if (a.getDataHora().isAfter(LocalDateTime.now(clock))) {
                    throw new ConflictException("O agendamento ainda não atingiu o horário previsto e não pode ser "
                            + "marcado como não comparecimento.");
                }
                String motivo = limpo(req.motivo());
                if (motivo != null) {
                    a.anexarObservacao("NÃO COMPARECIMENTO", motivo);
                    detalhes.put("motivo", motivo);
                }
                yield "AGENDAMENTO_NAO_COMPARECEU";
            }
            case CONCLUIDO -> {
                BigDecimal valor = concluir(a, req, usuarioId);
                detalhes.put("valorFinal", valor);
                yield "AGENDAMENTO_CONCLUIDO";
            }
            default -> "AGENDAMENTO_STATUS_ALTERADO";
        };

        a.mudarStatus(novo);
        Agendamento salvo = agendamentos.saveAndFlush(a);
        auditoria.registrar(acao, ENTIDADE, id, detalhes);
        return AgendamentoResponse.of(salvo, novo == CONCLUIDO ? realizados.findByAgendamentoId(id).orElse(null) : null);
    }

    /** Efeitos da conclusão. A carteira é chamada ANTES de criar o serviço realizado: se falhar, nada fica gravado. */
    private BigDecimal concluir(Agendamento a, StatusRequest req, Long usuarioId) {
        if (realizados.existsByAgendamentoId(a.getId())) {
            throw new ConflictException("Este agendamento já foi concluído.");
        }
        // DA-08: forma de pagamento obrigatória e caixa aberto de quem conclui (falha antes de qualquer efeito).
        if (req.formaPagamento() == null) {
            throw new CampoInvalidoException("formaPagamento", "Informe a forma de pagamento do serviço.");
        }
        caixas.exigirAberto(usuarioId);
        boolean vacinacao = a.getServico().isVacinacao();
        if (vacinacao && req.vacina() == null) {
            throw new CampoInvalidoException("vacina", "Informe os dados da vacina aplicada.");
        }
        if (!vacinacao && req.vacina() != null) {
            throw new CampoInvalidoException("vacina", "Dados de vacina só se aplicam ao serviço Vacinação.");
        }
        if (vacinacao) {
            // Mesma regra (e mesma auditoria) do módulo de pets: catálogo, espécie e datas.
            carteira.registrar(a.getPet().getId(), req.vacina());
        }
        BigDecimal valor = req.valorFinal() != null ? req.valorFinal() : a.getTotalEstimado();
        ServicoRealizado realizado = realizados.saveAndFlush(
                new ServicoRealizado(a, LocalDateTime.now(clock), valor, limpo(req.observacoesConclusao())));
        // Receita na mesma transação: se o lançamento falhar, a conclusão (e a vacina) são desfeitas.
        financeiro.registrarServico(realizado, req.formaPagamento(), caixas.travarAberto(usuarioId), usuarioId);
        return valor;
    }

    // ------------------------------------------------------------ apoio

    private record Dados(Pet pet, Servico servico, Funcionario funcionario, PrecoEstimado preco) {
    }

    private Dados resolver(AgendamentoRequest req, Long ignorarId) {
        // Trava a linha do pet: dois agendamentos simultâneos do mesmo pet são serializados (R03).
        Pet pet = pets.travarParaAgendamento(req.petId()).filter(Pet::isAtivo)
                .orElseThrow(() -> new CampoInvalidoException("petId", "Pet não encontrado ou inativo."));
        Servico servico = servicos.findById(req.servicoId()).filter(Servico::isAtivo)
                .orElseThrow(() -> new CampoInvalidoException("servicoId", "Serviço não encontrado ou inativo."));
        if (servico.getDuracaoMinutos() == null || servico.getPreco() == null) {
            throw new ConflictException("O serviço não possui preço ou duração configurados.");
        }
        Funcionario funcionario = null;
        if (req.funcionarioId() != null) {
            funcionario = funcionarios.findById(req.funcionarioId())
                    .orElseThrow(() -> new CampoInvalidoException("funcionarioId", "Funcionário não encontrado."));
            if (!funcionario.isDisponivel()) {
                throw new ConflictException("O funcionário selecionado não está disponível para novos agendamentos.");
            }
        }
        if (!req.dataHora().isAfter(LocalDateTime.now(clock))) {
            throw new CampoInvalidoException("dataHora", "Escolha uma data e hora futuras.");
        }
        LocalDateTime inicio = req.dataHora();
        LocalDateTime fim = inicio.plusMinutes(servico.getDuracaoMinutos());
        LocalDateTime desde = inicio.minusMinutes(DURACAO_MAXIMA_MINUTOS);
        if (conflita(agendamentos.candidatosPorPet(pet.getId(), ATIVOS, desde, fim), inicio, fim, ignorarId)) {
            throw new ConflictException("O pet já possui um agendamento que se sobrepõe a este horário.");
        }
        if (funcionario != null && conflita(agendamentos.candidatosPorFuncionario(funcionario.getId(), ATIVOS, desde, fim),
                inicio, fim, ignorarId)) {
            throw new ConflictException("O funcionário já possui um agendamento que se sobrepõe a este horário.");
        }
        return new Dados(pet, servico, funcionario, servico.estimarPara(pet.getPorte()));
    }

    private static boolean conflita(List<Agendamento> candidatos, LocalDateTime inicio, LocalDateTime fim, Long ignorarId) {
        return candidatos.stream()
                .filter(a -> ignorarId == null || !ignorarId.equals(a.getId()))
                .anyMatch(a -> a.sobrepoe(inicio, fim));
    }

    private Agendamento carregar(Long id) {
        return agendamentos.findWithDetalhesById(id).orElseThrow(() -> new NotFoundException("Agendamento", id));
    }

    private Pet petAtivo(Long petId) {
        return pets.findById(petId).filter(Pet::isAtivo).orElseThrow(() -> new NotFoundException("Pet", petId));
    }

    private Servico servicoAtivo(Long servicoId) {
        return servicos.findById(servicoId).filter(Servico::isAtivo)
                .orElseThrow(() -> new NotFoundException("Serviço", servicoId));
    }

    private static Map<String, Object> detalhes(Agendamento a) {
        return Map.of("petId", a.getPet().getId(), "servicoId", a.getServico().getId(),
                "dataHora", a.getDataHora().toString(), "totalEstimado", a.getTotalEstimado());
    }

    private static String limpo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
