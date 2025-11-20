package com.productcatalog.service;

import com.productcatalog.dto.CreateProductRequest;
import com.productcatalog.dto.ProductResponse;
import com.productcatalog.dto.SearchResponse;
import com.productcatalog.exception.DuplicateSkuException;
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
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductSearchRepository productSearchRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

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
        log.info("Searching products with query: '{}', limit: {}, offset: {}", query, limit, offset);

        long startTime = System.currentTimeMillis();

        try {
            // Create fuzzy multi-match query
            Query multiMatchQuery = QueryBuilders.multiMatch(m -> m
                    .fields("name^2", "description")
                    .query(query)
                    .fuzziness("AUTO")
                    .operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.Or)
            );

            Pageable pageable = PageRequest.of(offset / limit, limit);

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

            log.info("Search completed. Found {} results in {}ms", results.size(), executionTime);

            return SearchResponse.builder()
                    .results(results)
                    .totalHits(searchHits.getTotalHits())
                    .executionTimeMs(executionTime)
                    .build();

        } catch (Exception e) {
            log.error("Error searching products", e);
            throw new RuntimeException("Search failed: " + e.getMessage(), e);
        }
    }

    public void softDeleteProduct(Long id) {
        log.info("Soft deleting product with ID: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product with ID " + id + " not found"));

        product.setDeletedAt(LocalDateTime.now());
        productRepository.save(product);

        log.info("Product soft deleted successfully");
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

