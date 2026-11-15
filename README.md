# LaoAds (UNICAMP) — Nền tảng chiến dịch marketing đa kênh của Unitel

Monorepo xây mới hệ thống LaoAds, kế thừa hạ tầng đã chạy ổn định của **Unitel BOC** (kết nối Oracle, JWT, audit, i18n…) và làm mới hoàn toàn giao diện theo nhận diện Unitel.

```
laoads/
├── laoads-backend/   Java 21 · Spring Boot 3.5 · Maven multi-module · Oracle 11g (uat/prod dùng lại cấu hình kết nối BOC)
├── laoads-web/       React 19 · Vite 8 · TypeScript 6 · Tailwind v4 · TanStack Query · i18n vi/en/lo
├── docs/                         toàn bộ tài liệu
│   ├── API_CONTRACT_V1.md        hợp đồng API đợt 1 (nguồn chung FE ↔ BE)
│   ├── DANH_GIA_KE_THUA_BOC.md   đánh giá kế thừa BOC + lộ trình
│   ├── openapi.yaml              spec export từ BE (đầu vào `npm run gen:api`)
│   └── requirements/             tài liệu gốc: SRS, quản lý dự án, đề xuất… — KHÔNG commit
├── docker-compose.dev.yml        Oracle XE local cho dev (độc lập với DB dùng chung BOC)
└── docker-compose.yml            triển khai (build image → đẩy lên server không internet)
```

Tài liệu gốc (SRS, file quản lý dự án, ước lượng…) nằm trong `docs/requirements/` và **không được commit** (xem `.gitignore`).

## Chạy nhanh

### Chỉ xem giao diện (không cần BE, không cần VPN)
```bash
cd laoads-web
npm install
npm run dev:mock        # http://localhost:5173 — toàn bộ API được mock
```
Tài khoản demo: `superadmin/superadmin` · `owner_bsd/owner` · `admin_bsd/admin` · `member_bsd/member`.

### FE + BE thật (DB Oracle local — không cần VPN, không đụng DB dùng chung)
```bash
# DB: Oracle XE 11.2 trong Docker (cần Docker Desktop đang chạy). Lần đầu tự tạo schema LAOADS.
docker compose -f docker-compose.dev.yml up -d  # chờ `docker compose -f docker-compose.dev.yml ps` báo healthy

# BE — profile dev; Flyway tự tạo bảng + seed tài khoản demo khi DB sẵn sàng
cd laoads-backend
./mvnw -pl laoads-web -am spring-boot:run      # http://localhost:8095/api  (Windows: .\start-dev.ps1 — tự bật DB luôn)

# FE
cd laoads-web
npm run dev                                     # hybrid: BE thật + mock phần BE đợt 1 chưa có
```
Profile dev **chỉ** chạy với DB localhost: không đọc `BOC_DB_*`, và `DevDatabaseGuard` chặn start nếu `LAOADS_DB_URL` trỏ ra ngoài
(trừ khi đặt `LAOADS_DEV_ALLOW_REMOTE_DB=true`). DB dùng chung với BOC chỉ dành cho uat/prod.
Xoá sạch DB local: `docker compose -f docker-compose.dev.yml down -v`.

DB local tự có **mock data** (chỉ profile dev): 20 người dùng (tài khoản mock mới dùng mật khẩu `member`; `tran.minh`, `ngo.linh` bị khoá),
workspace BSD / BCD / MKT / VAS (VAS bị khoá), thành viên (có trường hợp đã gỡ), bảng giá Q3/2026 (hết hiệu lực) → Q4/2026 (đang áp dụng)
→ Q1/2027 (nháp), ~100 dòng nhật ký. Chiến dịch / tệp KH vẫn do MSW mock vì BE chưa có. Tắt: `LAOADS_DEV_MOCK_DATA=false`.

## Chế độ API của FE (`VITE_API_MODE`)

| Mode | Gọi BE thật | Mock (MSW) |
|---|---|---|
| `mock` | — | toàn bộ |
| `hybrid` (mặc định dev) | auth, workspace, thành viên, quản trị, bảng giá, nhật ký | dashboard, chiến dịch, phê duyệt, tệp KH, cấu hình gửi tin |
| `real` (mặc định Docker) | toàn bộ | — |

## Phạm vi đợt 1

- **BE**: đăng nhập (tài khoản nội bộ + adapter VSA/CAS), refresh token, `/me`, workspace, thành viên (quy tắc SRS 3.4), ma trận vai trò–quyền, quản trị người dùng, bảng giá (khoá/áp dụng), nhật ký + xuất Excel.
- **FE**: toàn bộ màn hình — Tổng quan, Chiến dịch (danh sách, chi tiết, wizard 6 bước, A/B + nhóm đối chứng, khung giờ không gửi, bắn tin thử), Phê duyệt, Tệp khách hàng (bộ lọc thuộc tính, import file), Workspace, Thành viên, Bảng giá, Nhật ký, Quản trị (workspace, người dùng, phân quyền, cấu hình gửi tin), dark mode, responsive.
- Đợt sau: segment engine, campaign engine, gateway SMS/WhatsApp/Email/USSD, OCS/Upoint, đo chuyển đổi — xem `docs/DANH_GIA_KE_THUA_BOC.md`.
