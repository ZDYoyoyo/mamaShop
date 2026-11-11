package com.mamashop.common.response;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * 分頁回應。不直接回傳 Spring 的 Page 物件：它的 JSON 結構很冗長，而且會隨 Spring 版本變動。
 *
 * @param page 目前頁碼，從 1 開始（和前端畫面上的頁碼一致）
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    /** 把 Spring Data 的 Page（頁碼從 0 開始）轉成 PageResponse，同時把 Entity 轉成 DTO */
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber() + 1,
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
