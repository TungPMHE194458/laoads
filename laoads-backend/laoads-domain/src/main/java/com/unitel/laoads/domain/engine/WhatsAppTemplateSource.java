package com.unitel.laoads.domain.engine;

import java.util.List;
import java.util.Optional;

/**
 * Port danh mục template WhatsApp đã duyệt ở nhà cung cấp (laoads.com: GET /whatsapp/message_templates — id, tên,
 * trạng thái, ngôn ngữ). Hành động WhatsApp chế độ TEMPLATE chọn một template trong danh mục này. Bản hiện tại là
 * giả lập ({@code laoads.whatsapp.templates=simulated}); adapter thật đọc từ nhà cung cấp WhatsApp.
 */
public interface WhatsAppTemplateSource {

    /** status: APPROVED | PENDING | REJECTED (theo nhà cung cấp); language: mã ngôn ngữ (vi, lo, en, en_US…). */
    record Template(String id, String name, String language, String status) {

        public boolean approved() {
            return "APPROVED".equalsIgnoreCase(status);
        }
    }

    List<Template> templates();

    default Optional<Template> find(String id) {
        return id == null ? Optional.empty() : templates().stream().filter(t -> t.id().equals(id.trim())).findFirst();
    }
}
