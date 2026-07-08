package com.tool.config.datasource;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "app.dynamic-datasource")
public class DynamicDataSourceProperties {

    private String defaultKey;

    private Map<String, DataSourceConfig> sources = new LinkedHashMap<String, DataSourceConfig>();

    public String getDefaultKey() {
        return defaultKey;
    }

    public void setDefaultKey(String defaultKey) {
        this.defaultKey = defaultKey;
    }

    public Map<String, DataSourceConfig> getSources() {
        return sources;
    }

    public void setSources(Map<String, DataSourceConfig> sources) {
        this.sources = sources;
    }

    public static class DataSourceConfig {
        private String url;
        private String username;
        private String password;
        private String driverClassName;
        private Integer maximumPoolSize;
        private Integer minimumIdle;
        private Long idleTimeout;
        private Long maxLifetime;
        private Long connectionTimeout;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public Integer getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(Integer maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }

        public Integer getMinimumIdle() {
            return minimumIdle;
        }

        public void setMinimumIdle(Integer minimumIdle) {
            this.minimumIdle = minimumIdle;
        }

        public Long getIdleTimeout() {
            return idleTimeout;
        }

        public void setIdleTimeout(Long idleTimeout) {
            this.idleTimeout = idleTimeout;
        }

        public Long getMaxLifetime() {
            return maxLifetime;
        }

        public void setMaxLifetime(Long maxLifetime) {
            this.maxLifetime = maxLifetime;
        }

        public Long getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(Long connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }
    }
}
