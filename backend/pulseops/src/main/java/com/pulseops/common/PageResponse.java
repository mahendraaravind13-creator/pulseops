package com.pulseops.common;

import java.util.List;

/** Stable pagination envelope. Kept explicit so the JSON shape does not depend on Spring Data internals. */
public record PageResponse<T>(List<T> content, PageInfo page) {

    public record PageInfo(int number, int size, long totalElements, int totalPages) {
    }

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long total) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(content, new PageInfo(page, size, total, totalPages));
    }
}
