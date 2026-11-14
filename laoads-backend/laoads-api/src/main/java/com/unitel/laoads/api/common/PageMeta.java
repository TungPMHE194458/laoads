package com.unitel.laoads.api.common;

import org.springframework.data.domain.Page;

/** metaData phân trang — {@code currentPage} bắt đầu từ 0. */
public record PageMeta(int totalPages, int currentPage, int pageSize, long totalItems) {

    public static PageMeta of(Page<?> page) {
        return new PageMeta(page.getTotalPages(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
