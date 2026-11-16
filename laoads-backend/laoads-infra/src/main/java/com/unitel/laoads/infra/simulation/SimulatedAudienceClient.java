package com.unitel.laoads.infra.simulation;

import com.unitel.laoads.domain.audience.Audience;
import com.unitel.laoads.domain.audience.Audience.DataType;
import com.unitel.laoads.domain.audience.AudienceClient;
import com.unitel.laoads.domain.audience.AudienceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Audience API GIẢ LẬP cho dev (chưa có kết nối tới OCS / BCCS3): catalog dựng từ bảng staging LADS_SUBSCRIBER.
 * <ul>
 *   <li>OCS: bảng {@code all_report_stl_new}, kiểu cột viết thường như Hive ("string", "int", "double"…);</li>
 *   <li>BCCS3: bảng {@code sale_tran}, kiểu đã chuẩn hoá (STRING, DOUBLE, DATE…).</li>
 * </ul>
 * verify / stream theo đúng luật tài liệu (cột phải tồn tại, kiểu khớp, operator hợp kiểu; OCS hỗ trợ offset).
 * Thay bằng {@code laoads.audience.client=http} khi có URL + API key thật.
 */
@Component
@ConditionalOnProperty(name = "laoads.audience.client", havingValue = "simulated", matchIfMissing = true)
@Slf4j
public class SimulatedAudienceClient implements AudienceClient {

    record Col(String name, String rawType, DataType type, String sql) {
    }

    private static Col c(String name, String rawType, DataType type) {
        return new Col(name, rawType, type, name.toUpperCase(Locale.ROOT));
    }

    static final Map<String, List<Col>> OCS = Map.of("all_report_stl_new", List.of(
            c("isdn", "string", DataType.STRING),
            c("snapshot_day", "date", DataType.DATE),
            c("province_code_active", "string", DataType.STRING),
            c("customer_age", "int", DataType.INT),
            c("gender", "string", DataType.STRING),
            c("tenure", "int", DataType.INT),
            c("act_status", "string", DataType.STRING),
            c("device_type", "string", DataType.STRING),
            c("infras_data_4g", "boolean", DataType.BOOLEAN),
            c("is_smartphone", "boolean", DataType.BOOLEAN),
            c("real_package_data", "string", DataType.STRING),
            c("data_package", "string", DataType.STRING),
            c("g_volume", "double", DataType.DOUBLE),
            c("t_org_charge", "double", DataType.DOUBLE),
            c("laostv_total_volume", "double", DataType.DOUBLE),
            c("login_superapp", "int", DataType.INT),
            c("active_umoney", "boolean", DataType.BOOLEAN),
            c("balance_basic", "double", DataType.DOUBLE)));

    static final Map<String, List<Col>> BCCS3 = Map.of("sale_tran", List.of(
            c("isdn", "STRING", DataType.STRING),
            c("province_code_active", "STRING", DataType.STRING),
            c("act_status", "STRING", DataType.STRING),
            c("real_package_data", "STRING", DataType.STRING),
            c("t_org_charge", "DOUBLE", DataType.DOUBLE),
            c("snapshot_day", "DATE", DataType.DATE)));

    private final JdbcTemplate jdbc;

    public SimulatedAudienceClient(JdbcTemplate jdbc) {
        JdbcTemplate t = new JdbcTemplate(jdbc.getDataSource());
        t.setFetchSize(1000);
        this.jdbc = t;
    }

    @Override
    public String mode() {
        return "simulated";
    }

    @Override
    public boolean configured(Audience.Source source) {
        return true;
    }

    @Override
    public List<String> tables(Audience.Source source) {
        return List.copyOf(catalog(source).keySet());
    }

    @Override
    public List<RawColumn> columns(Audience.Source source, String table) {
        List<Col> cols = catalog(source).get(table);
        if (cols == null) {
            throw new AudienceException(AudienceException.Kind.BAD_REQUEST, "Bảng " + table + " không tồn tại");
        }
        return cols.stream().map(c -> new RawColumn(c.name(), c.rawType())).toList();
    }

    @Override
    public Audience.VerifyResult verify(Audience.Source source, Audience.Request r) {
        Optional<String> problem = problem(source, r);
        return new Audience.VerifyResult(problem.isEmpty(), problem.orElse(null));
    }

    @Override
    public long stream(Audience.Source source, Audience.Request r, long offset, RowHandler handler) {
        problem(source, r).ifPresent(p -> {
            throw new AudienceException(AudienceException.Kind.BAD_REQUEST, p);
        });
        Map<String, Col> cols = byName(catalog(source).get(r.tableName()));
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder();
        List<Audience.Condition> conds = r.filters();
        for (int i = 0; i < conds.size(); i++) {
            Audience.Condition cond = conds.get(i);
            where.append('(').append(expr(cols.get(cond.columnName().toLowerCase(Locale.ROOT)), cond, params)).append(')');
            if (i < conds.size() - 1) {
                where.append(cond.logicalOperator() == Audience.Logical.OR ? " OR " : " AND ");
            }
        }
        List<Col> selected = r.columns().stream().map(sc -> cols.get(sc.columnName().toLowerCase(Locale.ROOT))).toList();
        String select = String.join(", ", selected.stream().map(Col::sql).distinct().toList());
        String sql = "SELECT " + select + " FROM (SELECT " + select + ", ROW_NUMBER() OVER (ORDER BY ISDN) RN FROM LADS_SUBSCRIBER"
                + (where.isEmpty() ? "" : " WHERE " + where) + ") WHERE RN > ? ORDER BY RN";
        params.add(source.supportsOffset() ? Math.max(0, offset) : 0);
        long[] count = {0};
        jdbc.query(sql, rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < selected.size(); i++) {
                row.put(r.columns().get(i).columnName(), value(rs, selected.get(i)));
            }
            handler.row(row);
            count[0]++;
        }, params.toArray());
        log.debug("[SIM-AUDIENCE] {} {} offset {} → {} dòng", source, r.tableName(), offset, count[0]);
        return count[0];
    }

    // ───────────── nội bộ ─────────────

    private static Map<String, List<Col>> catalog(Audience.Source source) {
        return source == Audience.Source.OCS ? OCS : BCCS3;
    }

    private static Map<String, Col> byName(List<Col> cols) {
        Map<String, Col> m = new LinkedHashMap<>();
        cols.forEach(c -> m.put(c.name().toLowerCase(Locale.ROOT), c));
        return m;
    }

    /** Lý do request không hợp lệ (giống phản hồi data.valid=false của service), hoặc rỗng nếu hợp lệ. */
    static Optional<String> problem(Audience.Source source, Audience.Request r) {
        List<Col> table = catalog(source).get(r.tableName());
        if (table == null) {
            return Optional.of("Bảng " + r.tableName() + " không tồn tại");
        }
        Map<String, Col> cols = byName(table);
        for (Audience.SelectedColumn sc : r.columns()) {
            Col col = cols.get(sc.columnName().toLowerCase(Locale.ROOT));
            if (col == null) {
                return Optional.of("Cột " + sc.columnName() + " không có trong bảng " + r.tableName());
            }
            if (col.type() != sc.dataType()) {
                return Optional.of("Cột " + sc.columnName() + " có kiểu " + col.type() + ", không phải " + sc.dataType());
            }
        }
        for (Audience.Condition cond : r.filters()) {
            Col col = cols.get(cond.columnName().toLowerCase(Locale.ROOT));
            if (col == null) {
                return Optional.of("Cột điều kiện " + cond.columnName() + " không có trong bảng " + r.tableName());
            }
            if (col.type() != cond.dataType()) {
                return Optional.of("Cột " + cond.columnName() + " có kiểu " + col.type() + ", không phải " + cond.dataType());
            }
            if (!cond.operator().accepts(col.type())) {
                return Optional.of("Toán tử " + cond.operator() + " không áp dụng cho kiểu " + col.type());
            }
        }
        long phones = r.columns().stream().filter(sc -> Audience.PHONE.equals(sc.targetField())).count();
        if (phones != 1) {
            return Optional.of("Cần đúng một cột targetField = PHONE");
        }
        return Optional.empty();
    }

    private static String expr(Col col, Audience.Condition cond, List<Object> params) {
        String column = col.type() == DataType.DATE ? "TRUNC(" + col.sql() + ")" : col.sql();
        Object v = bind(col.type(), cond.value());
        return switch (cond.operator()) {
            case IS -> add(params, v, column + " = ?");
            case NOT -> add(params, v, "(" + column + " <> ? OR " + column + " IS NULL)");
            case LIKE -> add(params, likePattern(cond.value()), "UPPER(" + column + ") LIKE ? ESCAPE '!'");
            case NOT_LIKE -> add(params, likePattern(cond.value()), "(" + column + " IS NULL OR UPPER(" + column + ") NOT LIKE ? ESCAPE '!')");
            case GREATER_THAN -> add(params, v, column + " > ?");
            case GREATER_EQUAL_THAN -> add(params, v, column + " >= ?");
            case LESS_THAN -> add(params, v, column + " < ?");
            case LESS_EQUAL_THAN -> add(params, v, column + " <= ?");
        };
    }

    private static String add(List<Object> params, Object v, String sql) {
        params.add(v);
        return sql;
    }

    private static Object bind(DataType type, String value) {
        return switch (type) {
            case INT, LONG -> Long.parseLong(value.trim());
            case DOUBLE -> new BigDecimal(value.trim());
            case DATE -> Date.valueOf(LocalDate.parse(value.trim()));
            case BOOLEAN -> "true".equalsIgnoreCase(value.trim()) ? 1 : 0;
            case STRING -> value;
        };
    }

    private static String likePattern(String v) {
        String upper = v.toUpperCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + upper + "%";
    }

    private static Object value(ResultSet rs, Col col) throws SQLException {
        return switch (col.type()) {
            case DATE -> {
                Date d = rs.getDate(col.sql());
                yield d == null ? null : d.toLocalDate().toString();
            }
            case BOOLEAN -> {
                int n = rs.getInt(col.sql());
                yield rs.wasNull() ? null : n == 1;
            }
            case INT, LONG -> {
                long n = rs.getLong(col.sql());
                yield rs.wasNull() ? null : n;
            }
            case DOUBLE -> rs.getBigDecimal(col.sql());
            case STRING -> rs.getString(col.sql());
        };
    }
}
