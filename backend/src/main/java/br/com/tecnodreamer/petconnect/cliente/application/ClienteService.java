package br.com.tecnodreamer.petconnect.cliente.application;

import br.com.tecnodreamer.petconnect.cliente.domain.Cliente;
import br.com.tecnodreamer.petconnect.cliente.infrastructure.ClienteRepository;
import br.com.tecnodreamer.petconnect.cliente.web.ClienteRequest;
import br.com.tecnodreamer.petconnect.cliente.web.ClienteResponse;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import br.com.tecnodreamer.petconnect.shared.validation.Documentos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class ClienteService {

    private static final String ENTIDADE = "cliente";

    private final ClienteRepository repository;
    private final AuditoriaService auditoria;

    public ClienteService(ClienteRepository repository, AuditoriaService auditoria) {
        this.repository = repository;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<ClienteResponse> listar(String busca, Pageable pageable) {
        String termo = busca == null || busca.isBlank() ? null : busca.trim();
        return repository.buscarAtivos(termo, Documentos.somenteDigitos(termo), pageable).map(ClienteResponse::of);
    }

    @Transactional(readOnly = true)
    public ClienteResponse buscar(Long id) {
        return ClienteResponse.of(carregar(id));
    }

    @Transactional
    public ClienteResponse criar(ClienteRequest req) {
        String cpf = Documentos.somenteDigitos(req.cpf());
        String telefone = Documentos.somenteDigitos(req.telefone());
        if (cpf != null && repository.existsByCpf(cpf)) {
            throw new ConflictException("Já existe um cliente com este CPF.");
        }
        if (repository.existsByTelefone(telefone)) {
            throw new ConflictException("Já existe um cliente com este telefone.");
        }
        Cliente cliente = new Cliente(req.nome().trim(), cpf, req.dataNascimento(), telefone,
                vazioParaNulo(req.email()), vazioParaNulo(req.observacoes()));
        if (req.endereco() != null) {
            cliente.definirEndereco(req.endereco().criar());
        }
        Cliente salvo = repository.saveAndFlush(cliente);
        auditoria.registrar("CRIAR", ENTIDADE, salvo.getId(), Map.of("nome", salvo.getNome()));
        return ClienteResponse.of(salvo);
    }

    @Transactional
    public ClienteResponse atualizar(Long id, ClienteRequest req) {
        Cliente cliente = carregar(id);
        String cpf = Documentos.somenteDigitos(req.cpf());
        String telefone = Documentos.somenteDigitos(req.telefone());
        if (cpf != null && repository.existsByCpfAndIdNot(cpf, id)) {
            throw new ConflictException("Já existe outro cliente com este CPF.");
        }
        if (repository.existsByTelefoneAndIdNot(telefone, id)) {
            throw new ConflictException("Já existe outro cliente com este telefone.");
        }
        cliente.atualizarDados(req.nome().trim(), cpf, req.dataNascimento(), telefone,
                vazioParaNulo(req.email()), vazioParaNulo(req.observacoes()));
        if (req.endereco() == null) {
            cliente.definirEndereco(null);
        } else if (cliente.getEndereco() == null) {
            cliente.definirEndereco(req.endereco().criar());
        } else {
            req.endereco().aplicarEm(cliente.getEndereco());
        }
        Cliente salvo = repository.saveAndFlush(cliente);
        auditoria.registrar("ATUALIZAR", ENTIDADE, id, Map.of());
        return ClienteResponse.of(salvo);
    }

    /** Exclusão lógica: preserva histórico de vendas e atendimentos. */
    @Transactional
    public void inativar(Long id) {
        Cliente cliente = carregar(id);
        cliente.inativar();
        auditoria.registrar("INATIVAR", ENTIDADE, id, Map.of());
    }

    private Cliente carregar(Long id) {
        return repository.findWithEnderecoById(id)
                .filter(Cliente::isAtivo)
                .orElseThrow(() -> new NotFoundException("Cliente", id));
    }

    private static String vazioParaNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
