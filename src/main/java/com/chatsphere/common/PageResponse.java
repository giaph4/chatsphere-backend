package com.chatsphere.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Bọc kết quả phân trang thành contract JSON ổn định của riêng dự án.
 * <p>
 * KHÔNG serialize thẳng {@link Page}: Spring cảnh báo "Serializing PageImpl instances
 * as-is is not supported" vì cấu trúc JSON của nó là chi tiết nội bộ, có thể đổi giữa
 * các version Spring Data → vỡ frontend.
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static <E, T> PageResponse<T> from(Page<E> source, Function<E, T> mapper) {
        return new PageResponse<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.hasNext()
        );
    }

    public static <T> PageResponse<T> empty(Page<?> source) {
        return new PageResponse<>(List.of(), source.getNumber(), source.getSize(), 0, 0, false);
    }
}
