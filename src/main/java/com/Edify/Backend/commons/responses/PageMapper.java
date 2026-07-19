package com.Edify.Backend.commons.responses;


import org.springframework.data.domain.Page;

public class PageMapper {

    public static <T> PageResponse<T> toPageResponse(Page<T> page){

        return PageResponse.<T>builder()

                .content(page.getContent())

                .page(page.getNumber())

                .size(page.getSize())

                .totalElements(page.getTotalElements())

                .totalPages(page.getTotalPages())

                .first(page.isFirst())

                .last(page.isLast())

                .hasNext(page.hasNext())

                .hasPrevious(page.hasPrevious())

                .build();

    }

}
