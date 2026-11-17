package com.unitel.laoads.infra.simulation;

import com.unitel.laoads.domain.engine.WhatsAppTemplateSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Danh mục template WhatsApp GIẢ LẬP (thay cho API template của nhà cung cấp WhatsApp). Tên đặt theo kiểu template
 * đang dùng trên laoads.com (phòng ban_nội dung_ngày, một template có thể có nhiều ngôn ngữ).
 */
@Component
@ConditionalOnProperty(name = "laoads.whatsapp.templates", havingValue = "simulated", matchIfMissing = true)
public class SimulatedWhatsAppTemplateSource implements WhatsAppTemplateSource {

    private static final List<Template> TEMPLATES = List.of(
            new Template("wa-tpl-0001", "bsd_data4g_promo_v1", "lo", "APPROVED"),
            new Template("wa-tpl-0002", "bsd_data4g_promo_v1", "en", "APPROVED"),
            new Template("wa-tpl-0003", "bsd_topup_bonus_v2", "lo", "APPROVED"),
            new Template("wa-tpl-0004", "bsd_topup_bonus_v2", "vi", "APPROVED"),
            new Template("wa-tpl-0005", "bcd_superapp_login_v1", "lo", "APPROVED"),
            new Template("wa-tpl-0006", "bcd_umoney_open_wallet", "lo", "APPROVED"),
            new Template("wa-tpl-0007", "bcd_umoney_open_wallet", "en", "APPROVED"),
            new Template("wa-tpl-0008", "bsd_laotv_trial_7days", "lo", "APPROVED"),
            new Template("wa-tpl-0009", "bsd_package_expiring_d3", "lo", "APPROVED"),
            new Template("wa-tpl-0010", "bsd_welcome_new_sim", "lo", "APPROVED"),
            new Template("wa-tpl-0011", "bsd_new_year_greeting", "lo", "PENDING"),
            new Template("wa-tpl-0012", "bsd_lucky_draw_dec", "lo", "REJECTED"));

    @Override
    public List<Template> templates() {
        return TEMPLATES;
    }
}
