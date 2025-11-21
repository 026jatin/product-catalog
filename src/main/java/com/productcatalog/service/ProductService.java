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
import com.productcatalog.util.SearchResponseUtil;
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

@Slf4j
@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductSearchRepository productSearchRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;
    
    @Autowired
    private SearchResponseUtil searchResponseUtil;

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

        long start = System.currentTimeMillis();
        log.info("Search request: query='{}', limit={}, offset={}", query, limit, offset);

        if (isEmpty(query)) {
            return searchResponseUtil.emptyResponse("Query cannot be empty", 0, limit);
        }

        limit = validateLimit(limit);
        offset = validateOffset(offset);

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

            SearchHits<Product> searchHits =
                    elasticsearchOperations.search(nativeQuery, Product.class);

            long totalHits = searchHits.getTotalHits();
            List<ProductResponse> data = searchHits.getSearchHits()
                    .stream()
                    .map(hit -> mapToResponse(hit.getContent()))
                    .toList();

            if (data.isEmpty()) {
                return searchResponseUtil.emptyResponse("No products found", pageNumber, limit);
            }

            long execTime = System.currentTimeMillis() - start;

            return searchResponseUtil.successResponse(data, totalHits, pageNumber, limit, execTime);

        } catch (Exception e) {
            log.error("Search failed for query '{}'", query, e);
            return searchResponseUtil.emptyResponse("Search failed: " + e.getMessage(), 0, limit);
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

    private boolean isEmpty(String query) {
        return query == null || query.trim().isEmpty();
    }

    private int validateLimit(int limit) {
        if (limit < 1 || limit > 100) {
            log.warn("Invalid limit '{}', using default 20", limit);
            return 20;
        }
        return limit;
    }

    private int validateOffset(int offset) {
        if (offset < 0) {
            log.warn("Invalid offset '{}', using default 0", offset);
            return 0;
        }
        return offset;
    }
}

