package com.unitel.laoads.web.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 2 DataSource pool (copy BOC ReadOnlyDataSourceConfig):
 * <ul>
 *   <li>primary ({@code spring.datasource.*}): JPA + Flyway + JdbcTemplate (audit writer);</li>
 *   <li>readOnly ({@code laoads.read-only-datasource.*}): truy vấn báo cáo/analytical về sau. read-only=true.</li>
 * </ul>
 * Cả 2 pool mang cấu hình resilience A+++ (initialization-fail-timeout -1, timeout 5s, max-lifetime 10p...).
 * {@code @Primary} để Spring Boot autoconfig (Flyway/JPA) vẫn pickup spring.datasource.* bình thường.
 */
@Configuration
public class ReadOnlyDataSourceConfig {

    @Bean(name = "dataSource", destroyMethod = "close")
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource primaryDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "readOnlyDataSource", destroyMethod = "close")
    @ConfigurationProperties(prefix = "laoads.read-only-datasource")
    public HikariDataSource readOnlyDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean(name = "readOnlyJdbc")
    public NamedParameterJdbcTemplate readOnlyJdbc(
            @Qualifier("readOnlyDataSource") HikariDataSource readOnlyDataSource) {
        return new NamedParameterJdbcTemplate(readOnlyDataSource);
    }
}
