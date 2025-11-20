package com.productcatalog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(
        basePackages = "com.productcatalog.repository",
        includeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
                value = {com.productcatalog.repository.ProductRepository.class}
        )
)
@EnableElasticsearchRepositories(
        basePackages = "com.productcatalog.repository",
        includeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
                value = {com.productcatalog.repository.ProductSearchRepository.class}
        )
)
public class RepositoryConfiguration {
}
