# LaoAds (UNICAMP) — Nền tảng chiến dịch marketing đa kênh của Unitel

Monorepo xây mới hệ thống LaoAds, kế thừa hạ tầng đã chạy ổn định của **Unitel BOC** (kết nối Oracle, JWT, audit, i18n…) và làm mới hoàn toàn giao diện theo nhận diện Unitel.

```
laoads/
├── laoads-backend/   Java 21 · Spring Boot 3.5 · Maven multi-module · Oracle 11g (dùng lại cấu hình kết nối BOC)
├── laoads-web/       React 19 · Vite 8 · TypeScript 6 · Tailwind v4 · TanStack Query · i18n vi/en/lo
├── docs/
│   ├── API_CONTRACT_V1.md        hợp đồng API đợt 1 (nguồn chung FE ↔ BE)
│   └── DANH_GIA_KE_THUA_BOC.md   đánh giá kế thừa BOC + lộ trình
└── docker-compose.yml            triển khai (build image → đẩy lên server không internet)
```

Tài liệu gốc (SRS, file quản lý dự án, ước lượng…) nằm ở thư mục gốc nhưng **không được commit** (xem `.gitignore`).

## Chạy nhanh

### Chỉ xem giao diện (không cần BE, không cần VPN)
```bash
cd laoads-web
npm install
npm run dev:mock        # http://localhost:5173 — toàn bộ API được mock
```
Tài khoản demo: `superadmin/superadmin` · `owner_bsd/owner` · `admin_bsd/admin` · `member_bsd/member`.

### FE + BE thật
```bash
# BE (cần VPN tới Oracle Unitel; dùng chung biến môi trường với BOC)
cd laoads-backend
./mvnw -pl laoads-web -am spring-boot:run      # http://localhost:8095/api

# FE
cd laoads-web
npm run dev                                     # hybrid: BE thật + mock phần BE đợt 1 chưa có
```

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
