package com.productcatalog.util;

import com.productcatalog.dto.PaginationInfo;
import com.productcatalog.dto.ProductResponse;
import com.productcatalog.dto.SearchResponse;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class SearchResponseUtil {

    public SearchResponse successResponse(List<ProductResponse> data,
                                           long totalHits,
                                           int pageNumber,
                                           int pageSize,
                                           long execTimeMs) {

        int totalPages = (int) Math.ceil((double) totalHits / pageSize);

        PaginationInfo paginationInfo = PaginationInfo.builder()
                .pageNumber(pageNumber)
                .pageSize(pageSize)
                .totalPages(totalPages)
                .totalElements(totalHits)
                .build();

        return SearchResponse.builder()
                .data(data)
                .success(true)
                .message("Search successful")
                .paginationInfo(paginationInfo)
                .build();
    }

    public SearchResponse emptyResponse(String message, int pageNumber, int pageSize) {
        PaginationInfo paginationInfo = PaginationInfo.builder()
                .pageNumber(pageNumber)
                .pageSize(pageSize)
                .totalPages(0)
                .totalElements(0)
                .build();

        return SearchResponse.builder()
                .data(Collections.emptyList())
                .success(true)
                .message(message)
                .paginationInfo(paginationInfo)
                .build();
    }



}
