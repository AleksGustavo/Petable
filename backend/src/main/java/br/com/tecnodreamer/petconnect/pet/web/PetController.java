package br.com.tecnodreamer.petconnect.pet.web;

import br.com.tecnodreamer.petconnect.pet.application.FotoPetService;
import br.com.tecnodreamer.petconnect.pet.application.PetService;
import br.com.tecnodreamer.petconnect.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/pets")
public class PetController {

    static final String PODE_EDITAR = "hasAnyRole('ADMIN', 'VENDAS')";

    private final PetService service;
    private final FotoPetService fotos;

    public PetController(PetService service, FotoPetService fotos) {
        this.service = service;
        this.fotos = fotos;
    }

    /** Busca por nome do pet ou do tutor; {@code clienteId} restringe aos pets de um tutor. */
    @GetMapping
    public PageResponse<PetResponse> listar(@RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(service.listar(q, clienteId, PageRequest.of(page, size, Sort.by("nome", "id"))));
    }

    @GetMapping("/{id}")
    public PetResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    @PreAuthorize(PODE_EDITAR)
    public ResponseEntity<PetResponse> criar(@Valid @RequestBody PetRequest request) {
        PetResponse criado = service.criar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(criado.id());
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    @PreAuthorize(PODE_EDITAR)
    public PetResponse atualizar(@PathVariable Long id, @Valid @RequestBody PetRequest request) {
        return service.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(PODE_EDITAR)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(@PathVariable Long id) {
        service.desativar(id);
    }

    @PutMapping(path = "/{id}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(PODE_EDITAR)
    public FotoResponse atualizarFoto(@PathVariable Long id,
            @RequestPart(name = "foto", required = false) MultipartFile foto) {
        return fotos.atualizar(id, foto);
    }

    @GetMapping("/{id}/foto")
    public ResponseEntity<byte[]> foto(@PathVariable Long id) {
        FotoPetService.Foto foto = fotos.carregar(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(foto.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePrivate())
                .body(foto.conteudo());
    }
}
