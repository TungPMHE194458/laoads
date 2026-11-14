package com.unitel.laoads.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * i18n message lỗi: Accept-Language ∈ {vi, en, lo}; thiếu header / ngôn ngữ khác → vi.
 * Bean tên {@code localeResolver} thay bean mặc định của Spring MVC; EnvelopeAuthHandler dùng lại ở tầng filter.
 */
@Configuration
public class LocaleConfig {

    public static final Locale VI = Locale.forLanguageTag("vi");
    public static final Locale EN = Locale.ENGLISH;
    public static final Locale LO = Locale.forLanguageTag("lo");

    @Bean(name = "localeResolver")
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver r = new AcceptHeaderLocaleResolver();
        r.setSupportedLocales(List.of(VI, EN, LO));
        r.setDefaultLocale(VI);
        return r;
    }
}
