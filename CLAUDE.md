# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repo layout

Monorepo for **LaoAds / UNICAMP** — Unitel Laos' multi-channel campaign platform (rebuild of the existing Next.js LaoAds). It deliberately reuses infrastructure from the sibling project **Unitel BOC** (`D:\gboc\Unitel-BOC`, read-only reference — never edit it from here).

```
laoads/
├── laoads-backend/     Spring Boot 3.5.4 · Java 21 · Maven multi-module · Oracle 11.2
├── laoads-web/         React 19 · Vite 8 · TS 6 · Tailwind v4 · TanStack Query 5 · zustand · i18next (vi/en/lo) · MSW · recharts 3
├── docs/API_CONTRACT_V1.md        ← the FE↔BE contract (read before touching either side)
├── docs/DANH_GIA_KE_THUA_BOC.md   ← what was inherited from BOC + roadmap
└── docker-compose.yml             ← deployment only (not for local dev)
```

Root-level `*.docx` / `*.xlsx` (SRS_LAOADS ~67MB, project-management sheet, estimation template) are the **source requirements** but are gitignored — the PM sheet contains a VSA ticket URL and a password-like string. Never commit them. To read them, convert to text in a scratch dir (python-docx / openpyxl); the SRS has no Word heading styles, so grep numbered lines (`^3\.2\.1`) to navigate.

**All in-product text, comments-where-needed and commit messages are Vietnamese**; identifiers stay English. Commit messages must NOT include a `Co-Authored-By` line (user preference).

## The contract (`docs/API_CONTRACT_V1.md`)

- Base `/api/v1` (BE context-path `/api`, controllers under `/v1`). Dev BE port **8095** (BOC dev already uses 8090 — both run side by side).
- **Envelope** (kept from the old LaoAds SRS, *not* BOC's ProblemDetail): `{ success, data, message, statusCode, metaData?, requestId?, errors? }`. Paging: page index from **0**, `metaData = { totalPages, currentPage, pageSize, totalItems }`.
- Headers: `Authorization: Bearer`, **`X-Workspace-Id`** (required on workspace-scoped endpoints; missing → 400 `WORKSPACE_REQUIRED`), `Accept-Language: vi|en|lo` (picks `message` language).
- Expired access token → 401 `TOKEN_EXPIRED` (FE refreshes once). Wrong old password on change-password → **400** `WRONG_PASSWORD` (not 401, so the FE doesn't auto-logout).
- When the BE runs, export springdoc → `docs/openapi.yaml`, then `npm run gen:api` in `laoads-web` regenerates `src/api/schema.ts` (generated — never hand-edit). Until then FE types live in `src/api/types.ts`, hand-kept in sync with the contract.

## Authorization model (both sides must agree)

- **System role** on the user: `SUPER_ADMIN | USER`. SUPER_ADMIN passes every check, even without workspace membership.
- **Workspace role** on membership: `OWNER | ADMIN | MEMBER`, mapped to 12 permission codes via an editable matrix (`WORKSPACE_VIEW/UPDATE`, `MEMBER_MANAGE`, `CAMPAIGN_VIEW/CREATE/UPDATE/SUBMIT/APPROVE/DELETE`, `SEGMENT_MANAGE`, `SECRET_KEY_MANAGE`, `REPORT_VIEW`). Defaults are in the contract §2.
- Member rules (SRS 3.4): only an OWNER may grant/revoke OWNER; nobody changes their own role; OWNER can't be removed; you can't remove yourself; no duplicate membership.
- The FE hides UI by permission (`useCan()`, `<Can>`, `RequirePermission`) — that is UX only; the BE enforces with `@PreAuthorize("@perm.has('…')")`.
- "Workspace" replaces the old LaoAds "company": created by SUPER_ADMIN, no approval flow, no LaoCRM. Login is **VSA** (Viettel CAS-style SSO) + local accounts; LaoID is not used.

## Frontend (`laoads-web/`)

```bash
npm install            # .npmrc sets legacy-peer-deps (openapi-typescript peers TS5; we use TS6 — same as BOC)
npm run dev            # :5173, VITE_API_MODE=hybrid by default, /api proxied to :8095
npm run dev:mock       # everything mocked — no BE/VPN needed (demo accounts on the login page)
npm run build          # tsc -b && vite build
npm run lint           # 0 errors expected; ~13 react-refresh "only-export-components" warnings are accepted (shadcn-style files)
node scripts/check-i18n.mjs   # every t('key') must exist in vi/en/lo — run after adding strings
npm run gen:api        # ../docs/openapi.yaml → src/api/schema.ts
```
There is no test runner in the FE. Verify UI changes by running `dev:mock` and looking at the page.

The Vite dev proxy strips the `Origin` header on `/api` (same-origin via proxy) — otherwise the BE's CORS filter 403s whenever Vite auto-increments to a port outside the whitelist (BOC hit this as troubleshooting #3).

### API modes (`VITE_API_MODE`) — the most important FE concept
- `mock`: MSW mocks **core + pending** handlers.
- `hybrid` (dev default): core endpoints hit the real BE; only **pending** handlers (dashboard, campaigns, groups, approvals, segments, sending settings — BE đợt 1 doesn't have them) are mocked, with `onUnhandledRequest: 'bypass'`.
- `real` (Docker default): MSW is never loaded (`main.tsx` dynamic-imports `src/mocks/browser.ts` only when mode ≠ real, keeping ~470KB out of the main bundle).
- `src/mocks/handlers/core.ts` = endpoints the BE already implements; `pending.ts` = not yet. **When a BE module ships, delete its handlers from `pending.ts`** — otherwise hybrid mode keeps serving mock data and hides the real API. Mock data lives in `src/mocks/db.ts` (in-memory, seeded PRNG, resets on reload) and must keep the contract/SRS shape.

### Key files
- `src/api/client.ts` — axios: adds JWT, `X-Workspace-Id` (from `useUiStore`), `Accept-Language`; unwraps the envelope into `ApiError(status, statusCode, message, errors)`; single-flight refresh on 401 `TOKEN_EXPIRED`, then `forceLogout()` → `/login?redirect=`. Use `request<T>()` / `requestPage<T>()` (returns `{items, meta}`) / `download()`; `cleanParams()` drops empty/NaN params (`NaN != null` is true — don't rely on `!= null`).
- `src/api/queryClient.ts` — no retry on 4xx, one retry otherwise, `staleTime` 30s.
- `src/stores/auth.ts` (`laoads_auth`: tokens + user) and `src/stores/ui.ts` (`laoads_ui`: workspaceId, sidebar, theme). Switching workspace must `removeQueries` for everything except `me`/`auth` (see `WorkspaceSwitcher`).
- `src/features/<domain>/api.ts` — TanStack Query hooks per domain; workspace-scoped query keys include the workspace id. Mutations invalidate their list keys + related keys (e.g. member changes also invalidate `['workspace', id]`; role-matrix changes invalidate `me`).
- `src/features/auth/permissions.tsx` — `useCurrentWorkspace()`, `useCan()`, `useIsSuperAdmin()`, `<Can>`.
- `src/components/layout/guards.tsx` — `RequireAuth` (token → `/me` → valid workspace, auto-selects when the user has exactly one) and `RequirePermission`.
- `src/routes.tsx` — every page is lazy-loaded; route-level permission is declared here.
- `src/components/common/` — reuse these instead of rolling new ones: `PageHeader`, `DataTable` (pagination + checkbox selection with `isRowSelectable`), `FormDialog` + `Field`, `ConfirmDialog`, `SearchInput` (debounced), `StatCard`, `ChartCard` (chart ↔ table toggle), `EmptyState`, status badges + `CHANNEL_META` in `status.tsx`.
- `src/components/ui/` — shadcn-style primitives on the unified `radix-ui` package (`Slot.Root`, `Dialog`, …). `SheetContent` = right-side drawer built on Dialog.

### Design system (`src/index.css`)
- Brand from the Unitel logo: `--brand-500 #F85000` (accents, focus ring, active nav, charts). Primary buttons use `--brand-600 #D24400` because white text on #F85000 is only 3.4:1 (fails WCAG AA); #D24400 is 4.58:1. Don't swap them.
- Tokens are CSS variables mapped through `@theme inline`; dark mode = `.dark` class on `<html>` (set pre-render in `index.html` from `laoads_theme`). Always use tokens (`bg-card`, `text-muted-foreground`, `bg-success-soft`…), never raw hex in components.
- Fonts: Inter Variable + **Noto Sans Lao Variable** fallback (Inter has no Lao glyphs).
- Charts follow the dataviz rules: single axis only, single-series charts use `--series-brand`, channel colours are **fixed per channel** (`--series-1..4` = SMS/Email/WhatsApp/USSD, validated for CVD on `#fff` and `#17181c`), legend whenever ≥2 series, and every `ChartCard` offers a table view (needed because two light-mode series colours are < 3:1).

### i18n
Single `translation` namespace per language in `src/locales/{vi,en,lo}.json` (vi = fallback). `scripts/check-i18n.mjs` scans static `t('…')` keys; keys built dynamically (`` t(`campaignStatus.${s}`) ``) or chosen inside ternaries must be listed in its `DYNAMIC` map — add new enums there. Lao text is a best-effort translation and needs native review. Number/percent formatting goes through `src/lib/format.ts` (`Intl` with `LOCALE_MAP`); relative time uses `Intl.RelativeTimeFormat` because date-fns has no Lao locale. Never shorten a person's name for greetings — Lao names put the given name first, Vietnamese last.

### React 19 / react-hooks v7 lint rules that bite
- No `setState` inside `useEffect` to sync props/reset forms: derive (`edits ?? serverData`), adjust state during render (see `SearchInput`), reset in the event handler, or mount the dialog only while open (see `CreateSegmentDialog`).
- No impure calls in render (`Date.now()` → `useState(() => Date.now())`).
- Don't create components from function calls in render (`const I = actionIcon(t); <I/>` at component top level fails) — use `React.createElement(actionIcon(t), props)`.

## Backend (`laoads-backend/`)

Reuses BOC's backend infrastructure — see BOC's CLAUDE.md "Critical: schema sharing & resilience patterns" for the reasoning behind each piece.

```bash
./mvnw clean verify                              # build + 90 unit tests (no DB needed)
./mvnw -pl laoads-web -am spring-boot:run        # dev profile, :8095/api — non-web modules set spring-boot.run.skip, so -am is safe
./mvnw -pl laoads-domain test -Dtest=ClassName#method
.\start-dev.ps1                                  # Windows; loads gitignored .env.local.ps1 (template: .env.local.ps1.example)
./scripts/export-openapi.sh [port]               # running BE → laoads-backend/docs/openapi.yaml; copy to ../docs/openapi.yaml, then FE `npm run gen:api`
```

Modules (`com.unitel.laoads`): `laoads-domain` (entities, services, `@perm` PermissionGuard, `WorkspaceContext`, VSA port) → `laoads-infra` (DatabaseBootstrap, cache/datasource config, `CasVsaClient`, Flyway `db/migration`) → `laoads-api` (controllers + DTOs, `ApiResponse` envelope) → `laoads-web` (`LaoadsApplication`, security, `GlobalExceptionHandler`, audit AOP, request-id filter, health, `application*.yml`, `messages*.properties`).

| Env var | Notes |
|---|---|
| `LAOADS_DB_URL` / `_USERNAME` / `_PASSWORD` | fall back to BOC's `BOC_DB_*`; no password default anywhere |
| `LAOADS_DB_SCHEMA` | default `BOC` |
| `LAOADS_JWT_SECRET` | ≥ 32 bytes; only the dev profile has a default |
| `LAOADS_CORS_ALLOWED_ORIGINS` | required in prod (dev allows localhost:5173–5175) |
| `LAOADS_VSA_ENABLED` / `_CAS_URL` / `_SERVICE_URL` | VSA off by default |
| `SERVER_PORT` | dev default **8095** — 8091 is permanently taken on the dev machine by an unrelated Java app |

- **DB = the same Oracle as BOC** (user decision: reuse BOC's connection config 100%). Target is **Oracle 11.2.0.4** → no IDENTITY (sequences), no BOOLEAN (NUMBER(1)), no `FETCH FIRST`, identifiers ≤ 30 chars, VARCHAR2 ≤ 4000 bytes.
- **Shared schema safety**: every owned table/sequence is prefixed `LADS_`; Flyway history `LADS_FLYWAY_HISTORY`, `baseline-on-migrate` (baseline 0), `clean-disabled: true` in every profile. Never touch BOC's `UBOC_*` tables or the ~1100 legacy tables.
- **Starts while the DB is down** (copied from BOC — do not regress): Hikari `initialization-fail-timeout: -1` + fail-fast timeouts on both pools, lazy JPA repositories, `DatabaseBootstrap` pings every 30s, runs `flyway.migrate()` once reachable, then publishes `DatabaseReadyEvent`. The dev seeder (`DevDataSeeder`) listens to that event — unlike BOC, whose seeder runs before migration and fails on a fresh DB. New DB-touching startup code must hang off the same event (or be try/catch-wrapped).
- Full `/actuator/health` is 503 while the DB is down (by design) → container healthchecks use `/actuator/health/liveness`.
- ⚠️ Pointing a running BE at the real Oracle **creates `LADS_*` tables in the shared schema** on first connect. Smoke-test against an unreachable URL (`LAOADS_DB_URL=jdbc:oracle:thin:@//127.0.0.1:1/none`) unless you intend to migrate. As of the first commit, `V1__core_schema.sql`/`V2__seed_permissions.sql`, the seeder and every DB read/write path have **never run against Oracle** — the first real run needs a scratch Oracle (Testcontainers XE is wired in the pom) or a supervised dev run.
- Repositories bootstrap lazily, so JPQL errors would only surface on first request — `RepositoryQueryValidationTest` builds the metamodel without a DB and compiles every `@Query`/derived query; **add every new repository to it**. `MethodSecurityPropagationTest` locks in that `@perm` 400s pass through `@PreAuthorize`.
- `@perm.has / hasForPath / hasIn / isMemberOf` read `X-Workspace-Id`; a non-numeric header or one ≠ path `{id}` → 400 `INVALID_PARAM`; a LOCKED workspace → 403 for non-admins.
- `@Auditable` SpEL exposes `#result` (envelope data), `#currentUser`, `#workspaceHeader`; `detailJson` masks keys containing password/token/secret/ticket. Only workspace/member actions record `workspaceId`, so OWNERs don't see system-admin actions.
- Keep `messages.properties` + `_vi` / `_en` / `_lo` in sync; parameterised messages need `''` for a literal quote.
- Contract deviations the FE already handles (full list in the agent hand-off, summarised): extra codes `USER_LOCKED` 403, `DB_UNAVAILABLE` 503, `VSA_UNAVAILABLE` 503, `EXPORT_TOO_LARGE` 400 (returned as a JSON envelope *inside the blob* — `download()` parses it), `METHOD_NOT_ALLOWED` 405; every success is HTTP 200 (creates too); removing a member sets `REVOKED` and re-adding reactivates the same row; usernames are lowercased; workspace codes uppercased; BE minimum password length is 6 (FE asks for 8); paging params are clamped, not rejected.
- VSA: CAS 2.0 `serviceValidate`; the VSA user must already exist locally (else 403 `VSA_USER_NOT_PROVISIONED`); `service` must start with the configured service-url. FE callback route: `/auth/vsa/callback`.
- Dev seed (dev/uat only): `superadmin/superadmin`, `owner_bsd/owner`, `admin_bsd/admin`, `member_bsd/member`; workspaces `BSD`, `BCD` (BCD has no owner). The FE mock uses the same accounts.
- The read-only Hikari pool is configured (for future OCS/segment reads) but not used yet.

## Deployment

`docker-compose.yml` builds `laoads-be` (prod profile, :8080 in container) and `laoads-fe` (nginx :80, proxies `/api/` → `laoads-be:8080`, `client_max_body_size 25m`, 300s read timeout for Excel imports). Unitel servers have no internet: build locally, `docker save | gzip`, ship, `docker load` (BOC's `scripts/deploy_prod.py` is the reference for a scripted version). Prod env lives in a gitignored `.env.prod`.
