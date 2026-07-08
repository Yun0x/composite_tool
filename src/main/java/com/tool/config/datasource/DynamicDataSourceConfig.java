package com.tool.config.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(DynamicDataSourceProperties.class)
public class DynamicDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DynamicDataSourceConfig.class);

    @Bean
    @Primary
    public DataSource dataSource(DynamicDataSourceProperties properties) {
        if (!StringUtils.hasText(properties.getDefaultKey())) {
            throw new IllegalStateException("app.dynamic-datasource.default-key must not be empty");
        }
        if (CollectionUtils.isEmpty(properties.getSources())) {
            throw new IllegalStateException("app.dynamic-datasource.sources must not be empty");
        }

        Map<Object, Object> targetDataSources = new HashMap<Object, Object>();
        for (Map.Entry<String, DynamicDataSourceProperties.DataSourceConfig> entry : properties.getSources().entrySet()) {
            targetDataSources.put(entry.getKey(), createDataSource(entry.getKey(), entry.getValue()));
        }

        Object defaultDataSource = targetDataSources.get(properties.getDefaultKey());
        if (defaultDataSource == null) {
            throw new IllegalStateException("Default datasource key not found: " + properties.getDefaultKey());
        }

        DynamicRoutingDataSource routingDataSource = new DynamicRoutingDataSource();
        routingDataSource.setDefaultTargetDataSource(defaultDataSource);
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.afterPropertiesSet();
        log.info("Dynamic datasource initialized. defaultKey={}, keys={}", properties.getDefaultKey(), targetDataSources.keySet());
        return routingDataSource;
    }

    private DataSource createDataSource(String key, DynamicDataSourceProperties.DataSourceConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("hikari-" + key);
        hikariConfig.setJdbcUrl(config.getUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        if (StringUtils.hasText(config.getDriverClassName())) {
            hikariConfig.setDriverClassName(config.getDriverClassName());
        }
        if (config.getMaximumPoolSize() != null) {
            hikariConfig.setMaximumPoolSize(config.getMaximumPoolSize());
        }
        if (config.getMinimumIdle() != null) {
            hikariConfig.setMinimumIdle(config.getMinimumIdle());
        }
        if (config.getIdleTimeout() != null) {
            hikariConfig.setIdleTimeout(config.getIdleTimeout());
        }
        if (config.getMaxLifetime() != null) {
            hikariConfig.setMaxLifetime(config.getMaxLifetime());
        }
        if (config.getConnectionTimeout() != null) {
            hikariConfig.setConnectionTimeout(config.getConnectionTimeout());
        }
        return new HikariDataSource(hikariConfig);
    }
}
