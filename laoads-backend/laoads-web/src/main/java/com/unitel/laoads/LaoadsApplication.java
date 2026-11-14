package com.unitel.laoads;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Không dùng UserDetailsService (JWT tự dựng principal) → tắt auto-config sinh mật khẩu mặc định. */
@SpringBootApplication(scanBasePackages = "com.unitel.laoads", exclude = UserDetailsServiceAutoConfiguration.class)
@EntityScan(basePackages = "com.unitel.laoads.domain")
@EnableJpaRepositories(basePackages = "com.unitel.laoads.domain")
public class LaoadsApplication {

    public static void main(String[] args) {
        SpringApplication.run(LaoadsApplication.class, args);
    }
}
