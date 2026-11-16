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
npm run dev                                     # hybrid: mọi màn hình gọi BE thật
```
Profile dev **chỉ** chạy với DB localhost: không đọc `BOC_DB_*`, và `DevDatabaseGuard` chặn start nếu `LAOADS_DB_URL` trỏ ra ngoài
(trừ khi đặt `LAOADS_DEV_ALLOW_REMOTE_DB=true`). DB dùng chung với BOC chỉ dành cho uat/prod.
Xoá sạch DB local: `docker compose -f docker-compose.dev.yml down -v`.

DB local tự có **mock data** (chỉ profile dev): 20 người dùng (tài khoản mock mới dùng mật khẩu `member`; `tran.minh`, `ngo.linh` bị khoá),
workspace BSD / BCD / MKT / VAS (VAS bị khoá), thành viên (có trường hợp đã gỡ), bảng giá Q3/2026 (hết hiệu lực) → Q4/2026 (đang áp dụng)
→ Q1/2027 (nháp), ~100 dòng nhật ký; thêm 30.000 thuê bao giả, 4 tệp KH, 3 nhóm và 8 chiến dịch đủ mọi trạng thái (chiến dịch đã duyệt chạy thật qua gateway giả lập). Tắt: `LAOADS_DEV_MOCK_DATA=false`.

## Chế độ API của FE (`VITE_API_MODE`)

| Mode | Gọi BE thật | Mock (MSW) |
|---|---|---|
| `mock` | — | toàn bộ |
| `hybrid` (mặc định dev) | toàn bộ | chỉ endpoint trong `handlers/pending.ts` (hiện trống) |
| `real` (mặc định Docker) | toàn bộ | — |

## Phạm vi hiện tại

- **BE (đợt 1)**: đăng nhập (tài khoản nội bộ + adapter VSA/CAS), refresh token, `/me`, workspace, thành viên (quy tắc SRS 3.4), ma trận vai trò–quyền, quản trị người dùng, bảng giá, nhật ký + xuất Excel.
- **BE (đợt 2–3)**: tệp khách hàng (lọc thuộc tính bằng SQL an toàn, import .xlsx/.csv), chiến dịch + phê duyệt (vòng đời đầy đủ), nhóm chiến dịch, cấu hình đầu số / retry / khung giờ không gửi, dashboard workspace + Super Admin (xuất Excel top workspace), **engine thực thi**: lịch chạy ngay / một lần / lặp lại, A/B + nhóm đối chứng, chuỗi hành động có thời gian chờ, khung giờ không gửi, retry + hoàn tài nguyên, dừng khi đạt mục tiêu / ngân sách, link tracking, đo chuyển đổi, API sự kiện (API key theo workspace), bắn tin thử.
- **Giả lập**: gateway SMS / Email / WhatsApp / USSD / OCS và nguồn đo chuyển đổi (BI) đều là **giả lập** — engine chạy đầu-cuối nhưng chưa gửi ra ngoài. Cần thông số kết nối thật để thay adapter (`laoads.engine.*`).
- **FE**: toàn bộ màn hình, gọi BE thật ở chế độ hybrid; chế độ mock vẫn chạy được không cần BE.
- Còn lại: adapter gateway / OCS / trích xuất BI thật, chạy nhiều instance BE (job hiện giả định 1 instance) — xem `docs/DANH_GIA_KE_THUA_BOC.md`.
