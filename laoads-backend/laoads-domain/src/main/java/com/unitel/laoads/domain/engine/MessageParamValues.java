package com.unitel.laoads.domain.engine;

import com.unitel.laoads.domain.campaign.MessageParams;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Đọc giá trị các tham số nguồn SUBSCRIBER ({@link MessageParams}) từ LADS_SUBSCRIBER cho một lô thuê bao. */
@Component
@RequiredArgsConstructor
public class MessageParamValues {

    /** Oracle giới hạn 1000 phần tử trong một danh sách IN. */
    static final int CHUNK = 1000;

    private final JdbcTemplate jdbc;

    /** ISDN → (key tham số → giá trị). Thuê bao không có trong LADS_SUBSCRIBER thì không có trong map. */
    public Map<String, Map<String, String>> load(Collection<String> isdns) {
        List<MessageParams.Param> params = MessageParams.subscriberParams();
        if (isdns == null || isdns.isEmpty() || params.isEmpty()) {
            return Collections.emptyMap();
        }
        // tên cột lấy từ danh mục cố định trong mã nguồn → ghép vào SQL an toàn; ISDN luôn là bind parameter
        String columns = params.stream().map(MessageParams.Param::column).collect(Collectors.joining(", "));
        List<String> all = new ArrayList<>(isdns);
        Map<String, Map<String, String>> out = new HashMap<>();
        for (int from = 0; from < all.size(); from += CHUNK) {
            List<String> chunk = all.subList(from, Math.min(all.size(), from + CHUNK));
            String in = String.join(", ", Collections.nCopies(chunk.size(), "?"));
            jdbc.query("SELECT ISDN, " + columns + " FROM LADS_SUBSCRIBER WHERE ISDN IN (" + in + ")", (RowCallbackHandler) rs -> {
                Map<String, String> values = new HashMap<>();
                for (MessageParams.Param p : params) {
                    values.put(p.key(), rs.getString(p.column()));
                }
                out.put(rs.getString("ISDN"), values);
            }, chunk.toArray());
        }
        return out;
    }
}
