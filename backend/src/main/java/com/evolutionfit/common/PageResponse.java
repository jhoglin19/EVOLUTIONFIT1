package com.evolutionfit.common;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Respuesta paginada estable para el frontend (no expone la estructura interna de Spring Data).
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
