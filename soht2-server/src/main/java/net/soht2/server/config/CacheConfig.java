/* SOHT2 © Licensed under MIT 2025. */
package net.soht2.server.config;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableCaching
@Configuration
public class CacheConfig {

  @Bean
  Cache userCache(CacheManager cacheManager) {
    return cacheManager.getCache("userCache");
  }
}
