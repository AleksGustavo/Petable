package br.com.tecnodreamer.petconnect.fornecedor.application;

import br.com.tecnodreamer.petconnect.compra.domain.StatusCompra;
import br.com.tecnodreamer.petconnect.compra.infrastructure.CompraRepository;
import br.com.tecnodreamer.petconnect.fornecedor.domain.Fornecedor;
import br.com.tecnodreamer.petconnect.fornecedor.infrastructure.FornecedorRepository;
import br.com.tecnodreamer.petconnect.fornecedor.web.FornecedorOpcao;
import br.com.tecnodreamer.petconnect.fornecedor.web.FornecedorRequest;
import br.com.tecnodreamer.petconnect.fornecedor.web.FornecedorResponse;
import br.com.tecnodreamer.petconnect.shared.audit.AuditoriaService;
import br.com.tecnodreamer.petconnect.shared.error.CampoInvalidoException;
import br.com.tecnodreamer.petconnect.shared.error.ConflictException;
import br.com.tecnodreamer.petconnect.shared.error.NotFoundException;
import br.com.tecnodreamer.petconnect.shared.validation.Documentos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class FornecedorService {

    static final String ENTIDADE = "fornecedor";
    static final String COMPRA_ABERTA = "O fornecedor possui compras em aberto e não pode ser desativado.";

    private final FornecedorRepository fornecedores;
    private final CompraRepository compras;
    private final AuditoriaService auditoria;

    public FornecedorService(FornecedorRepository fornecedores, CompraRepository compras, AuditoriaService auditoria) {
        this.fornecedores = fornecedores;
        this.compras = compras;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<FornecedorResponse> listar(String busca, Boolean ativo, Pageable pageable) {
        String termo = limpo(busca);
        return fornecedores.buscar(termo, Documentos.somenteDigitos(termo), ativo, pageable).map(FornecedorResponse::resumo);
    }

    @Transactional(readOnly = true)
    public List<FornecedorOpcao> opcoes(String busca) {
        return fornecedores.opcoes(limpo(busca), PageRequest.of(0, 50)).stream()
                .map(f -> new FornecedorOpcao(f.getId(), f.getNomeFantasia(), f.getCnpj())).toList();
    }

    @Transactional(readOnly = true)
    public FornecedorResponse buscar(Long id) {
        return FornecedorResponse.of(carregar(id));
    }

    @Transactional
    public FornecedorResponse criar(FornecedorRequest req) {
        String cnpj = cnpjValido(req.cnpj());
        if (fornecedores.existsByCnpj(cnpj)) {
            throw new ConflictException("Já existe um fornecedor com este CNPJ.");
        }
        String telefone = Documentos.somenteDigitos(req.telefone());
        if (telefone != null && fornecedores.existsByTelefone(telefone)) {
            throw new ConflictException("Já existe um fornecedor com este telefone.");
        }
        Fornecedor f = new Fornecedor(req.nomeFantasia().trim(), req.razaoSocial().trim(), cnpj, telefone,
                limpo(req.email()), limpo(req.contato()), limpo(req.observacoes()));
        f.definirEndereco(req.endereco().criar());
        Fornecedor salvo = fornecedores.saveAndFlush(f);
        auditoria.registrar("FORNECEDOR_CRIADO", ENTIDADE, salvo.getId(), Map.of("nomeFantasia", salvo.getNomeFantasia()));
        return FornecedorResponse.of(salvo);
    }

    @Transactional
    public FornecedorResponse atualizar(Long id, FornecedorRequest req) {
        Fornecedor f = carregar(id);
        String cnpj = cnpjValido(req.cnpj());
        if (fornecedores.existsByCnpjAndIdNot(cnpj, id)) {
            throw new ConflictException("Já existe outro fornecedor com este CNPJ.");
        }
        String telefone = Documentos.somenteDigitos(req.telefone());
        if (telefone != null && fornecedores.existsByTelefoneAndIdNot(telefone, id)) {
            throw new ConflictException("Já existe outro fornecedor com este telefone.");
        }
        f.atualizar(req.nomeFantasia().trim(), req.razaoSocial().trim(), cnpj, telefone, limpo(req.email()),
                limpo(req.contato()), limpo(req.observacoes()));
        if (f.getEndereco() == null) {
            f.definirEndereco(req.endereco().criar());
        } else {
            req.endereco().aplicarEm(f.getEndereco());
        }
        Fornecedor salvo = fornecedores.saveAndFlush(f);
        auditoria.registrar("FORNECEDOR_ATUALIZADO", ENTIDADE, id, Map.of("nomeFantasia", salvo.getNomeFantasia()));
        return FornecedorResponse.of(salvo);
    }

    /** Não desativa com compra em PEDIDO (DA-11). */
    @Transactional
    public FornecedorResponse desativar(Long id) {
        Fornecedor f = carregar(id);
        if (f.isAtivo()) {
            if (compras.existsByFornecedorIdAndStatus(id, StatusCompra.PEDIDO)) {
                throw new ConflictException(COMPRA_ABERTA);
            }
            f.desativar();
            auditoria.registrar("FORNECEDOR_DESATIVADO", ENTIDADE, id, Map.of("nomeFantasia", f.getNomeFantasia()));
        }
        return FornecedorResponse.of(f);
    }

    @Transactional
    public FornecedorResponse ativar(Long id) {
        Fornecedor f = carregar(id);
        if (!f.isAtivo()) {
            f.ativar();
            auditoria.registrar("FORNECEDOR_ATIVADO", ENTIDADE, id, Map.of("nomeFantasia", f.getNomeFantasia()));
        }
        return FornecedorResponse.of(f);
    }

    private static String cnpjValido(String valor) {
        if (!Documentos.cnpjValido(valor)) {
            throw new CampoInvalidoException("cnpj", "CNPJ inválido");
        }
        return Documentos.somenteDigitos(valor);
    }

    private Fornecedor carregar(Long id) {
        return fornecedores.findWithEnderecoById(id).orElseThrow(() -> new NotFoundException("Fornecedor", id));
    }

    private static String limpo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
