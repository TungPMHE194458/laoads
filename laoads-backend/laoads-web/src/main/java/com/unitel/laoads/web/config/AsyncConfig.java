package com.unitel.laoads.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Bật @Async cho AuditWriter (dùng executor mặc định {@code applicationTaskExecutor} của Spring Boot). */
@Configuration
@EnableAsync
public class AsyncConfig {
}
