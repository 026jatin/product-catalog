package com.productcatalog.service;

import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import com.productcatalog.dto.CreateProductRequest;
import com.productcatalog.dto.ProductResponse;
import com.productcatalog.dto.SearchResponse;
import com.productcatalog.exception.DuplicateSkuException;
import com.productcatalog.exception.ProductAlreadyDeletedException;
import com.productcatalog.exception.ProductNotFoundException;
import com.productcatalog.model.Product;
import com.productcatalog.repository.ProductRepository;
import com.productcatalog.repository.ProductSearchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductSearchRepository productSearchRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        log.info("Creating product with SKU: {}", request.getSku());

        if (productRepository.findBySku(request.getSku()).isPresent()) {
            log.error("Product with SKU {} already exists", request.getSku());
            throw new DuplicateSkuException("Product with SKU '" + request.getSku() + "' already exists");
        }

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .category(request.getCategory())
                .sku(request.getSku())
                .build();

        Product savedProduct = productRepository.save(product);
        log.info("Product created successfully with ID: {}", savedProduct.getId());

        return mapToResponse(savedProduct);
    }

    @Transactional(readOnly = true)
    public SearchResponse searchProducts(String query, int limit, int offset) {
        log.info("Search request received: query='{}', limit={}, offset={}", query, limit, offset);
        long startTime = System.currentTimeMillis();

        if (query == null || query.trim().isEmpty()) {
            return SearchResponse.builder()
                    .results(Collections.emptyList())
                    .totalHits(0)
                    .executionTimeMs(0)
                    .message("Query cannot be empty")
                    .build();
        }

        if (limit < 1 || limit > 100) {
            log.warn("Invalid limit '{}', using default value 20", limit);
            limit = 20;
        }

        if (offset < 0) {
            log.warn("Invalid offset '{}', using default value 0", offset);
            offset = 0;
        }

        int pageNumber = offset / limit;

        try {

            Query multiMatchQuery = QueryBuilders.multiMatch(m -> m
                    .fields("name^2", "description", "category")
                    .query(query)
                    .fuzziness("AUTO")
                    .operator(Operator.Or)
            );

            Pageable pageable = PageRequest.of(pageNumber, limit);

            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(multiMatchQuery)
                    .withPageable(pageable)
                    .build();

            SearchHits<Product> searchHits = elasticsearchOperations.search(nativeQuery, Product.class);


            List<ProductResponse> results = searchHits.getSearchHits()
                    .stream()
                    .map(hit -> mapToResponse(hit.getContent()))
                    .collect(Collectors.toList());

            long executionTime = System.currentTimeMillis() - startTime;

            log.info("Search completed: {} results in {} ms", results.size(), executionTime);


            return SearchResponse.builder()
                    .results(results)
                    .totalHits(searchHits.getTotalHits())
                    .executionTimeMs(executionTime)
                    .message("Search successful")
                    .build();

        } catch (Exception e) {

            log.error("Search failed for query '{}'", query, e);

            return SearchResponse.builder()
                    .results(Collections.emptyList())
                    .totalHits(0)
                    .executionTimeMs(0)
                    .message("Search failed: " + e.getMessage())
                    .build();
        }
    }

    @Transactional
    public void softDeleteProduct(Long id) {
        log.info("Soft deleting product with ID: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product with ID " + id + " not found"));

        if (product.getDeletedAt() != null) {
            log.warn("Product {} is already deleted", id);
            throw new ProductAlreadyDeletedException("Product already deleted");
        }

        product.setDeletedAt(LocalDateTime.now());
        productRepository.save(product);

        log.info("Product {} soft deleted successfully", id);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id) {
        log.info("Fetching product with ID: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product with ID " + id + " not found"));

        return mapToResponse(product);
    }

    private ProductResponse mapToResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .category(product.getCategory())
                .sku(product.getSku())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}

