# Đánh giá kế thừa BOC cho hệ thống LaoAds (UNICAMP)

Ngày lập: 15/11/2026. Nguồn: tài liệu trong `D:\laoads` (SRS_LAOADS, Luồng lấy dữ liệu BI, Quản lý dự án, bảng ước lượng UNICAMP) + khảo sát chỉ-đọc web LaoAds hiện hành + mã nguồn BOC (`D:\gboc\Unitel-BOC`).

## 1. LaoAds hiện hành (khảo sát)

- FE Next.js, API `/api/v1`, ID dạng MongoDB (24 hex), envelope `{ success, data, message, statusCode, metaData }`.
- Đăng nhập SSO **LaoID** → chọn công ty (`/select-workspace`).
- Menu: Tổng quan · Quản lý chiến dịch · Phê duyệt chiến dịch · Tệp khách hàng · Thông tin công ty · Phê duyệt công ty · Quản lý chi phí.
- Phân quyền: Super Admin + vai trò theo công ty OWNER/ADMIN/MEMBER, 12 permission (`COMPANY_*`, `MEMBER_MANAGE`, `CAMPAIGN_VIEW/CREATE/UPDATE/SUBMIT/APPROVE/DELETE`, `SEGMENT_MANAGE`, `SECRET_KEY_MANAGE`, `REPORT_VIEW`).
- Tệp KH: `FILE` / `QUERY` (nguồn OCS `all_report_stl_new`) / `EVENT` / `CAMPAIGN_OUTCOME`, có phiên bản (`segmentVersionStatus`).
- Chiến dịch: `actionChains`, A/B, `conversionGoal` (AUTO_OCS + cửa sổ ghi nhận), `outcomeExtractions`, chi phí theo `priceQuote` (13 loại hành động).

## 2. Định hướng xây mới (file Quản lý dự án)

- "Công ty" → **Workspace** do Super Admin tạo/gán; bỏ luồng phê duyệt công ty; bỏ tích hợp LaoCRM.
- Đăng nhập **VSA** (không dùng LaoID); khai báo function/role/user trên VSA.
- YC bổ sung: kênh **USSD**, danh mục **đầu số SMS** (118, 209, UNITEL, UNITEL_LOGS…), **retry** + hoàn tài nguyên, **bắn tin thử** ISDN, **thời gian chờ** giữa bước, **A/B testing**, **khung giờ không gửi**. (Chuyển kênh dự phòng WA→SMS: không làm.)

## 3. Ma trận kế thừa

| Thành phần | BOC có | LaoAds cần | Kế thừa |
|---|---|---|---|
| Kết nối DB Oracle | Hikari 2 pool, BE sống khi DB chết, Flyway async, history riêng | giống | **Dùng nguyên** (prefix `LADS_`, history `LADS_FLYWAY_HISTORY`) |
| Xác thực | JWT, filter, SecurityConfig, đổi mật khẩu | + refresh token, + VSA | ~70% |
| Phân quyền | 1 role/user + UserScope + ScopeGuard thủ công | role theo workspace + ma trận permission | Giữ pattern, viết lại (`@PreAuthorize("@perm.has(..)")`) |
| Audit | `@Auditable` + AOP + Excel | Nhật ký hệ thống + export | Dùng nguyên, + `workspace_id` |
| Sync engine + Hive JDBC | có | kéo dữ liệu BI/OCS, danh mục gói | ~80% (đợt sau) |
| SqlGuard | có | sinh SQL từ điều kiện lọc segment | Dùng nguyên (đợt sau) |
| Error handler | ProblemDetail | envelope SRS | Đổi format |
| Excel, bulk delete, Caffeine, i18n vi/en/lo, Docker, deploy script | có | giống | Dùng nguyên |
| FE nền (axios JWT, TanStack Query, i18n, DataTable, dialogs) | có | giống | Dùng pattern; **UI làm mới hoàn toàn** |

**Viết mới**: campaign engine (state machine, scheduler, A/B, control group, retry, silent hours), gateway SMS/WhatsApp/Email/USSD, OCS/Upoint, tracking link, đo chuyển đổi bằng so sánh snapshot BI, segment engine, bảng giá, phê duyệt, API sự kiện + secret key.

## 4. Lộ trình

- **Đợt 1 (repo này)**: BE thật cho auth (local + VSA adapter), `/me`, workspace, thành viên, ma trận quyền, quản trị user, bảng giá, nhật ký. FE đủ màn; phần BE chưa có chạy MSW mock (`VITE_API_MODE=hybrid`).
- **Đợt 2**: segment engine (FILE + QUERY trên OCS qua sync/Hive), campaign CRUD + state machine + phê duyệt.
- **Đợt 3**: engine thực thi + gateway (SMS SMPP/HTTP, WhatsApp Business API, Email, USSD), OCS/Upoint, tracking, conversion, báo cáo tổng hợp.
