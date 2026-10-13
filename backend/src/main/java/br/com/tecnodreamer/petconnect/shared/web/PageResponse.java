package br.com.tecnodreamer.petconnect.shared.web;

import org.springframework.data.domain.Page;

import java.util.List;

/** Contrato estável de paginação (não expõe a estrutura interna do Spring Data). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
