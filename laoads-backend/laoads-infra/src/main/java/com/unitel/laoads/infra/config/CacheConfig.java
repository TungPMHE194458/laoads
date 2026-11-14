package com.unitel.laoads.infra.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.unitel.laoads.domain.common.CacheNames;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine cache (style BOC): TTL 5 phút, tối đa 2000 entry/cache. Tên cache khai báo ở
 * {@link CacheNames} (domain) để service dùng trực tiếp.
 *
 * <table>
 *   <tr><th>Cache</th><th>Nội dung</th><th>Evict</th></tr>
 *   <tr><td>{@value CacheNames#ROLE_PERMISSIONS}</td><td>role → permission codes</td>
 *       <td>PUT /admin/roles/{code}/permissions</td></tr>
 * </table>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager();
        mgr.setCacheNames(List.of(CacheNames.ROLE_PERMISSIONS));
        mgr.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(2000)
                .recordStats());
        return mgr;
    }
}
