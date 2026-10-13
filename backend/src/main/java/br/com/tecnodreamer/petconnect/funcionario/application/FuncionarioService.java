package br.com.tecnodreamer.petconnect.funcionario.application;

import br.com.tecnodreamer.petconnect.endereco.web.EnderecoRequest;
import br.com.tecnodreamer.petconnect.funcionario.domain.Funcionario;
import br.com.tecnodreamer.petconnect.funcionario.domain.Sexo;
import br.com.tecnodreamer.petconnect.funcionario.infrastructure.FuncionarioRepository;
import br.com.tecnodreamer.petconnect.funcionario.web.FuncionarioAtualizacaoRequest;
import br.com.tecnodreamer.petconnect.funcionario.web.FuncionarioRequest;
import br.com.tecnodreamer.petconnect.funcionario.web.FuncionarioResponse;
import br.com.tecnodreamer.petconnect.funcionario.web.FuncionarioResumo;
import br.com.tecnodreamer.petconnect.funcionario.web.MeuFuncionarioRequest;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import br.com.tecnodreamer.petconnect.shared.validation.Documentos;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class FuncionarioService {

    static final String ENTIDADE = "funcionario";

    private final FuncionarioRepository funcionarios;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public FuncionarioService(FuncionarioRepository funcionarios, UsuarioRepository usuarios, AuditoriaService auditoria,
            Clock clock) {
        this.funcionarios = funcionarios;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /** Só funcionários com conta ativa (DA-02/DA-03): é o que aparece na agenda. */
    @Transactional(readOnly = true)
    public List<FuncionarioResumo> listarDisponiveis(String busca) {
        String termo = busca == null || busca.isBlank() ? null : busca.trim();
        return funcionarios.disponiveis(termo).stream()
                .map(f -> new FuncionarioResumo(f.getId(), f.getUsuario().getId(), f.getNome())).toList();
    }

    @Transactional(readOnly = true)
    public FuncionarioResponse buscar(Long id) {
        return FuncionarioResponse.of(carregar(id));
    }

    @Transactional(readOnly = true)
    public Optional<FuncionarioResponse> doUsuario(Long usuarioId) {
        return funcionarios.findByUsuarioId(usuarioId).map(FuncionarioResponse::of);
    }

    @Transactional
    public FuncionarioResponse criar(FuncionarioRequest req) {
        Usuario usuario = usuarios.findById(req.usuarioId())
                .orElseThrow(() -> new CampoInvalidoException("usuarioId", "Usuário não encontrado."));
        if (funcionarios.existsByUsuarioId(usuario.getId())) {
            throw new ConflictException("Este usuário já possui um funcionário vinculado.");
        }
        String cpf = cpfValido(req.cpf());
        if (funcionarios.existsByCpf(cpf)) {
            throw new ConflictException("Já existe um funcionário com este CPF.");
        }
        String telefone = Documentos.somenteDigitos(req.telefone());
        if (telefone != null && funcionarios.existsByTelefone(telefone)) {
            throw new ConflictException("Já existe um funcionário com este telefone.");
        }
        Funcionario f = new Funcionario(usuario, cpf);
        aplicar(f, req.nome(), req.dataNascimento(), req.sexo(), telefone, req.endereco(), req.observacoes());
        Funcionario salvo = funcionarios.saveAndFlush(f);
        auditoria.registrar("FUNCIONARIO_CRIADO", ENTIDADE, salvo.getId(),
                Map.of("usuarioId", usuario.getId(), "nome", salvo.getNome()));
        return FuncionarioResponse.of(salvo);
    }

    @Transactional
    public FuncionarioResponse atualizar(Long id, FuncionarioAtualizacaoRequest req) {
        Funcionario f = carregar(id);
        String cpf = cpfValido(req.cpf());
        if (funcionarios.existsByCpfAndIdNot(cpf, id)) {
            throw new ConflictException("Já existe outro funcionário com este CPF.");
        }
        String telefone = telefoneUnico(req.telefone(), id);
        f.corrigirCpf(cpf);
        aplicar(f, req.nome(), req.dataNascimento(), req.sexo(), telefone, req.endereco(), req.observacoes());
        Funcionario salvo = funcionarios.saveAndFlush(f);
        auditoria.registrar("FUNCIONARIO_ATUALIZADO", ENTIDADE, id, Map.of("nome", salvo.getNome()));
        return FuncionarioResponse.of(salvo);
    }

    /** Edição pelo próprio funcionário: sem CPF e sem trocar o vínculo. */
    @Transactional
    public FuncionarioResponse atualizarDoUsuario(Long usuarioId, MeuFuncionarioRequest req) {
        Funcionario f = funcionarios.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new NotFoundException("Funcionário do usuário", usuarioId));
        String telefone = telefoneUnico(req.telefone(), f.getId());
        aplicar(f, req.nome(), req.dataNascimento(), req.sexo(), telefone, req.endereco(), req.observacoes());
        Funcionario salvo = funcionarios.saveAndFlush(f);
        auditoria.registrar("PERFIL_FUNCIONARIO_ATUALIZADO", ENTIDADE, f.getId(), Map.of("nome", salvo.getNome()));
        return FuncionarioResponse.of(salvo);
    }

    private void aplicar(Funcionario f, String nome, LocalDate nascimento, Sexo sexo, String telefone,
            EnderecoRequest endereco, String observacoes) {
        if (nascimento != null && nascimento.isAfter(LocalDate.now(clock))) {
            throw new CampoInvalidoException("dataNascimento", "A data de nascimento não pode ser futura.");
        }
        f.atualizarDados(nome.trim(), nascimento, sexo, telefone,
                observacoes == null || observacoes.isBlank() ? null : observacoes.trim());
        if (endereco == null) {
            f.definirEndereco(null);
        } else if (f.getEndereco() == null) {
            f.definirEndereco(endereco.criar());
        } else {
            endereco.aplicarEm(f.getEndereco());
        }
    }

    private String telefoneUnico(String valor, Long ignorarId) {
        String telefone = Documentos.somenteDigitos(valor);
        if (telefone != null && funcionarios.existsByTelefoneAndIdNot(telefone, ignorarId)) {
            throw new ConflictException("Já existe outro funcionário com este telefone.");
        }
        return telefone;
    }

    private static String cpfValido(String valor) {
        if (!Documentos.cpfValido(valor)) {
            throw new CampoInvalidoException("cpf", "CPF inválido");
        }
        return Documentos.somenteDigitos(valor);
    }

    private Funcionario carregar(Long id) {
        return funcionarios.findWithDetalhesById(id).orElseThrow(() -> new NotFoundException("Funcionário", id));
    }
}
