package com.unitel.laoads.api.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Chuẩn hoá {@code page}/{@code size} (contract §1): page bắt đầu từ 0, size mặc định 10, tối đa 200.
 * Giá trị ngoài khoảng được kẹp (page &lt; 0 → 0; size &lt; 1 → 10; size &gt; 200 → 200) thay vì báo lỗi.
 * Thứ tự sắp xếp do từng query JPQL quy định (ORDER BY cố định + id làm tiebreaker).
 */
public final class PageParams {

    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 200;

    private PageParams() {
    }

    public static Pageable of(Integer page, Integer size) {
        int p = page == null || page < 0 ? 0 : page;
        int s;
        if (size == null || size < 1) {
            s = DEFAULT_SIZE;
        } else {
            s = Math.min(size, MAX_SIZE);
        }
        return PageRequest.of(p, s);
    }
}
