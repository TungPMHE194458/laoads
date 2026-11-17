/*
 * Mock cho phần BE ĐÃ CÓ ở đợt 1 (auth, workspace, thành viên, quản trị, bảng giá, nhật ký).
 * Chỉ bật ở VITE_API_MODE=mock — ở hybrid/real các request này đi thẳng tới BE.
 */
import { http, HttpResponse } from 'msw'
import type { MyWorkspace, Permission, PriceQuoteInput, WorkspaceRole } from '@/api/types'
import { audit, auditLogs, members, nextMemberId, permissionDefs, priceQuotes, priceSeq, publicUser, refreshMemberCounts, roles, users, workspaces } from '../db'
import { currentUser, fail, guard, includes, ok, paged, permissionsOf, q, workspaceId } from '../utils'

const B = '*/api/v1'
const tokens = (userId: number) => ({
  accessToken: `mock-access.${userId}.${Date.now()}`,
  refreshToken: `mock-refresh.${userId}.${Date.now()}`,
  expiresIn: 1800,
  user: publicUser(users.find((u) => u.id === userId)!),
})

function myWorkspaces(userId: number): MyWorkspace[] {
  const u = users.find((x) => x.id === userId)!
  const list = u.systemRole === 'SUPER_ADMIN' ? workspaces : workspaces.filter((w) => members.some((m) => m.userId === userId && m.workspaceId === w.id && m.status === 'ACTIVE'))
  return list
    .filter((w) => w.status === 'ACTIVE' || u.systemRole === 'SUPER_ADMIN')
    .map((w) => ({
      id: w.id,
      code: w.code,
      name: w.name,
      status: w.status,
      role: members.find((m) => m.userId === userId && m.workspaceId === w.id)?.role ?? null,
      permissions: permissionsOf(userId, w.id),
    }))
}

export const coreHandlers = [
  // ───── Auth ─────
  http.post(`${B}/auth/login`, async ({ request }) => {
    const { username, password } = (await request.json()) as { username: string; password: string }
    const u = users.find((x) => x.username === username)
    if (!u || u.password !== password) return fail(401, 'UNAUTHORIZED', 'Tên đăng nhập hoặc mật khẩu không đúng')
    if (u.status !== 'ACTIVE') return fail(403, 'ACCOUNT_LOCKED', 'Tài khoản đã bị khoá')
    u.lastLoginAt = new Date().toISOString()
    audit(u.username, 'LOGIN', 'AUTH', null, 'Đăng nhập', null)
    return ok(tokens(u.id))
  }),
  http.post(`${B}/auth/refresh`, async ({ request }) => {
    const { refreshToken } = (await request.json()) as { refreshToken: string }
    const m = refreshToken?.match(/^mock-refresh\.(\d+)\./)
    if (!m) return fail(401, 'UNAUTHORIZED', 'Refresh token không hợp lệ')
    return ok(tokens(Number(m[1])))
  }),
  http.post(`${B}/auth/logout`, () => ok(null)),
  http.get(`${B}/auth/vsa/config`, () => ok({ enabled: false, loginUrl: null })),
  http.post(`${B}/auth/vsa/login`, () => fail(400, 'VSA_DISABLED', 'Đăng nhập VSA chưa được cấu hình')),
  http.post(`${B}/auth/change-password`, async ({ request }) => {
    const u = currentUser(request)
    if (!u) return fail(401, 'UNAUTHORIZED', 'Chưa đăng nhập')
    const { oldPassword, newPassword } = (await request.json()) as { oldPassword: string; newPassword: string }
    if (oldPassword !== u.password) return fail(400, 'WRONG_PASSWORD', 'Mật khẩu hiện tại không đúng')
    u.password = newPassword
    return ok(null)
  }),
  http.get(`${B}/me`, ({ request }) => {
    const u = currentUser(request)
    if (!u) return fail(401, 'UNAUTHORIZED', 'Chưa đăng nhập')
    return ok({ user: publicUser(u), workspaces: myWorkspaces(u.id) })
  }),

  // ───── Workspace ─────
  http.get(`${B}/workspaces/my`, ({ request }) => {
    const u = currentUser(request)
    if (!u) return fail(401, 'UNAUTHORIZED', 'Chưa đăng nhập')
    return ok(myWorkspaces(u.id))
  }),
  http.get(`${B}/workspaces/:id`, ({ request, params }) => {
    const u = currentUser(request)
    if (!u) return fail(401, 'UNAUTHORIZED', 'Chưa đăng nhập')
    const w = workspaces.find((x) => x.id === Number(params.id))
    if (!w) return fail(404, 'NOT_FOUND', 'Không tìm thấy workspace')
    return ok(w)
  }),
  http.put(`${B}/workspaces/:id`, async ({ request, params }) => {
    const g = guard(request, 'WORKSPACE_UPDATE')
    if (g) return g
    const w = workspaces.find((x) => x.id === Number(params.id))
    if (!w) return fail(404, 'NOT_FOUND', 'Không tìm thấy workspace')
    Object.assign(w, await request.json(), { updatedAt: new Date().toISOString() })
    audit(currentUser(request)!.username, 'UPDATE', 'WORKSPACE', String(w.id), `Cập nhật thông tin workspace ${w.code}`, w.id)
    return ok(w)
  }),
  http.get(`${B}/admin/workspaces`, ({ request }) => {
    const g = guard(request, 'SUPER_ADMIN')
    if (g) return g
    const url = new URL(request.url)
    const list = workspaces.filter((w) => (includes(w.name, q(url, 'query')) || includes(w.code, q(url, 'query'))) && (!q(url, 'status') || w.status === q(url, 'status')))
    return paged(list, url)
  }),
  http.post(`${B}/admin/workspaces`, async ({ request }) => {
    const g = guard(request, 'SUPER_ADMIN')
    if (g) return g
    const body = (await request.json()) as { code: string; name: string; description?: string; ownerUsername: string }
    if (workspaces.some((w) => w.code.toLowerCase() === body.code.toLowerCase())) return fail(409, 'WORKSPACE_CODE_DUPLICATE', 'Mã workspace đã tồn tại')
    const owner = users.find((u) => u.username === body.ownerUsername)
    if (!owner) return fail(404, 'USER_NOT_FOUND', 'Không tìm thấy người dùng làm chủ sở hữu')
    const w = { id: Math.max(...workspaces.map((x) => x.id)) + 1, code: body.code.toUpperCase(), name: body.name, description: body.description ?? null, status: 'ACTIVE' as const, contactName: owner.fullName, contactEmail: owner.email, contactPhone: owner.phone, memberCount: 1, createdAt: new Date().toISOString(), updatedAt: null }
    workspaces.push(w)
    members.push({ id: nextMemberId(), workspaceId: w.id, userId: owner.id, username: owner.username, fullName: owner.fullName, email: owner.email, phone: owner.phone, role: 'OWNER', status: 'ACTIVE', joinedAt: w.createdAt })
    audit(currentUser(request)!.username, 'CREATE', 'WORKSPACE', String(w.id), `Tạo workspace ${w.code}`, null)
    return ok(w, 201)
  }),
  http.patch(`${B}/admin/workspaces/:id/status`, async ({ request, params }) => {
    const g = guard(request, 'SUPER_ADMIN')
    if (g) return g
    const w = workspaces.find((x) => x.id === Number(params.id))
    if (!w) return fail(404, 'NOT_FOUND', 'Không tìm thấy workspace')
    w.status = ((await request.json()) as { status: 'ACTIVE' | 'LOCKED' }).status
    audit(currentUser(request)!.username, w.status === 'LOCKED' ? 'LOCK' : 'UNLOCK', 'WORKSPACE', String(w.id), `${w.status === 'LOCKED' ? 'Khoá' : 'Mở khoá'} workspace ${w.code}`, null)
    return ok(w)
  }),

  // ───── Thành viên ─────
  http.get(`${B}/workspaces/:id/members`, ({ request, params }) => {
    const g = guard(request, 'WORKSPACE_VIEW')
    if (g) return g
    const url = new URL(request.url)
    const list = members.filter(
      (m) =>
        m.workspaceId === Number(params.id) &&
        (includes(m.fullName, q(url, 'query')) || includes(m.username, q(url, 'query')) || includes(m.email, q(url, 'query'))) &&
        (!q(url, 'role') || m.role === q(url, 'role')) &&
        (!q(url, 'status') || m.status === q(url, 'status')),
    )
    return paged(list, url)
  }),
  http.post(`${B}/workspaces/:id/members`, async ({ request, params }) => {
    const g = guard(request, 'MEMBER_MANAGE')
    if (g) return g
    const me = currentUser(request)!
    const wsId = Number(params.id)
    const { identifier, role } = (await request.json()) as { identifier: string; role: WorkspaceRole }
    const u = users.find((x) => x.username === identifier || x.email === identifier || x.phone === identifier)
    if (!u) return fail(404, 'USER_NOT_FOUND', 'Không tìm thấy tài khoản với thông tin đã nhập')
    if (members.some((m) => m.userId === u.id && m.workspaceId === wsId && m.status === 'ACTIVE')) return fail(409, 'MEMBER_DUPLICATE', 'Tài khoản đã là thành viên của workspace')
    const myRole = members.find((m) => m.userId === me.id && m.workspaceId === wsId)?.role
    if (role === 'OWNER' && myRole !== 'OWNER' && me.systemRole !== 'SUPER_ADMIN') return fail(403, 'OWNER_ONLY', 'Chỉ Chủ sở hữu được gán vai trò Chủ sở hữu')
    // Giống BE: user từng bị gỡ (REVOKED) → kích hoạt lại đúng bản ghi cũ, giữ nguyên member id
    const revoked = members.find((x) => x.userId === u.id && x.workspaceId === wsId && x.status === 'REVOKED')
    const m = revoked ?? { id: nextMemberId(), workspaceId: wsId, userId: u.id, username: u.username, fullName: u.fullName, email: u.email, phone: u.phone, role, status: 'ACTIVE' as const, joinedAt: new Date().toISOString() }
    if (revoked) Object.assign(revoked, { role, status: 'ACTIVE', joinedAt: new Date().toISOString() })
    else members.push(m)
    refreshMemberCounts()
    audit(me.username, 'CREATE', 'MEMBER', String(m.id), `Thêm ${u.fullName} vào workspace`, wsId)
    return ok(m, 201)
  }),
  http.put(`${B}/workspaces/:id/members/:memberId/role`, async ({ request, params }) => {
    const g = guard(request, 'MEMBER_MANAGE')
    if (g) return g
    const me = currentUser(request)!
    const wsId = Number(params.id)
    const m = members.find((x) => x.id === Number(params.memberId) && x.workspaceId === wsId)
    if (!m) return fail(404, 'NOT_FOUND', 'Không tìm thấy thành viên')
    const { role } = (await request.json()) as { role: WorkspaceRole }
    if (m.userId === me.id) return fail(400, 'CANNOT_CHANGE_SELF', 'Không thể đổi vai trò của chính bạn')
    const myRole = members.find((x) => x.userId === me.id && x.workspaceId === wsId)?.role
    if ((role === 'OWNER' || m.role === 'OWNER') && myRole !== 'OWNER' && me.systemRole !== 'SUPER_ADMIN') return fail(403, 'OWNER_ONLY', 'Chỉ Chủ sở hữu được gán/thu hồi vai trò Chủ sở hữu')
    m.role = role
    audit(me.username, 'CHANGE_ROLE', 'MEMBER', String(m.id), `Đổi vai trò ${m.fullName} → ${role}`, wsId)
    return ok(m)
  }),
  http.delete(`${B}/workspaces/:id/members/:memberId`, ({ request, params }) => {
    const g = guard(request, 'MEMBER_MANAGE')
    if (g) return g
    const me = currentUser(request)!
    const m = members.find((x) => x.id === Number(params.memberId) && x.workspaceId === Number(params.id) && x.status === 'ACTIVE')
    if (!m) return fail(404, 'NOT_FOUND', 'Không tìm thấy thành viên')
    if (m.role === 'OWNER') return fail(400, 'CANNOT_REMOVE_OWNER', 'Không thể gỡ Chủ sở hữu của workspace')
    if (m.userId === me.id) return fail(400, 'CANNOT_REMOVE_SELF', 'Không thể tự gỡ chính bạn')
    m.status = 'REVOKED' // giống BE: gỡ = thu hồi, không xoá bản ghi
    refreshMemberCounts()
    audit(me.username, 'DELETE', 'MEMBER', String(m.id), `Gỡ ${m.fullName} khỏi workspace`, m.workspaceId)
    return ok(null)
  }),

  // ───── Người dùng & ma trận quyền ─────
  http.get(`${B}/admin/users`, ({ request }) => {
    const g = guard(request, 'SUPER_ADMIN')
    if (g) return g
    const url = new URL(request.url)
    const list = users
      .filter((u) => (includes(u.fullName, q(url, 'query')) || includes(u.username, q(url, 'query')) || includes(u.email, q(url, 'query'))) && (!q(url, 'status') || u.status === q(url, 'status')) && (!q(url, 'systemRole') || u.systemRole === q(url, 'systemRole')))
      .map(publicUser)
    return paged(list, url)
  }),
  http.post(`${B}/admin/users`, async ({ request }) => {
    const g = guard(request, 'SUPER_ADMIN')
    if (g) return g
    const b = (await request.json()) as { username: string; fullName: string; email?: string; phone?: string; password: string; systemRole: 'SUPER_ADMIN' | 'USER' }
    if (users.some((u) => u.username === b.username)) return fail(409, 'USERNAME_DUPLICATE', 'Tên đăng nhập đã tồn tại')
    const u = { id: Math.max(...users.map((x) => x.id)) + 1, username: b.username, password: b.password, fullName: b.fullName, email: b.email ?? null, phone: b.phone ?? null, systemRole: b.systemRole, status: 'ACTIVE' as const, lastLoginAt: null, createdAt: new Date().toISOString() }
    users.push(u)
    audit(currentUser(request)!.username, 'CREATE', 'USER', String(u.id), `Tạo người dùng ${u.username}`, null)
    return ok(publicUser(u), 201)
  }),
  http.put(`${B}/admin/users/:id`, async ({ request, params }) => {
    const g = guard(request, 'SUPER_ADMIN')
    if (g) return g
    const u = users.find((x) => x.id === Number(params.id))
    if (!u) return fail(404, 'NOT_FOUND', 'Không tìm thấy người dùng')
    Object.assign(u, await request.json())
    return ok(publicUser(u))
  }),
  http.patch(`${B}/admin/users/:id/status`, async ({ request, params }) => {
    const g = guard(request, 'SUPER_ADMIN')
    if (g) return g
    const me = currentUser(request)!
    const u = users.find((x) => x.id === Number(params.id))
    if (!u) return fail(404, 'NOT_FOUND', 'Không tìm thấy người dùng')
    if (u.id === me.id) return fail(400, 'CANNOT_LOCK_SELF', 'Không thể tự khoá tài khoản của bạn')
    u.status = ((await request.json()) as { status: 'ACTIVE' | 'LOCKED' }).status
    audit(me.username, u.status === 'LOCKED' ? 'LOCK' : 'UNLOCK', 'USER', String(u.id), `${u.status === 'LOCKED' ? 'Khoá' : 'Mở khoá'} ${u.username}`, null)
    return ok(publicUser(u))
  }),
  http.post(`${B}/admin/users/:id/reset-password`, async ({ request, params }) => {
    const g = guard(request, 'SUPER_ADMIN')
    if (g) return g
    const u = users.find((x) => x.id === Number(params.id))
    if (!u) return fail(404, 'NOT_FOUND', 'Không tìm thấy người dùng')
    u.password = ((await request.json()) as { newPassword: string }).newPassword
    audit(currentUser(request)!.username, 'RESET_PASSWORD', 'USER', String(u.id), `Đặt lại mật khẩu ${u.username}`, null)
    return ok(null)
  }),
  http.get(`${B}/admin/permissions`, ({ request }) => guard(request, 'SUPER_ADMIN') ?? ok(permissionDefs)),
  http.get(`${B}/admin/roles`, ({ request }) => guard(request, 'SUPER_ADMIN') ?? ok(roles)),
  http.put(`${B}/admin/roles/:code/permissions`, async ({ request, params }) => {
    const g = guard(request, 'SUPER_ADMIN')
    if (g) return g
    const r = roles.find((x) => x.code === params.code)
    if (!r) return fail(404, 'NOT_FOUND', 'Không tìm thấy vai trò')
    r.permissions = ((await request.json()) as { permissions: Permission[] }).permissions
    audit(currentUser(request)!.username, 'UPDATE', 'ROLE', r.code, `Cập nhật quyền vai trò ${r.name}`, null)
    return ok(r)
  }),

  // ───── Bảng giá ─────
  http.get(`${B}/price-quotes`, ({ request }) => {
    if (!currentUser(request)) return fail(401, 'UNAUTHORIZED', 'Chưa đăng nhập')
    const url = new URL(request.url)
    return paged(priceQuotes.filter((p) => !q(url, 'status') || p.status === q(url, 'status')), url)
  }),
  http.get(`${B}/price-quotes/active`, () => ok(priceQuotes.find((p) => p.status === 'LOCKED') ?? null)),
  http.get(`${B}/price-quotes/:id`, ({ params }) => {
    const p = priceQuotes.find((x) => x.id === Number(params.id))
    return p ? ok(p) : fail(404, 'NOT_FOUND', 'Không tìm thấy bảng giá')
  }),
  http.post(`${B}/price-quotes`, async ({ request }) => {
    const g = guard(request, 'SUPER_ADMIN')
    if (g) return g
    const b = (await request.json()) as PriceQuoteInput
    const p = { id: ++priceSeq.v, name: b.name, note: b.note ?? null, status: 'DRAFT' as const, currency: 'LAK' as const, items: b.items, lockedAt: null, lockedBy: null, createdAt: new Date().toISOString(), createdBy: currentUser(request)!.username, updatedAt: null }
    priceQuotes.unshift(p)
    audit(p.createdBy, 'CREATE', 'PRICE_QUOTE', String(p.id), `Tạo bảng giá "${p.name}"`, null)
    return ok(p, 201)
  }),
  http.put(`${B}/price-quotes/:id`, async ({ request, params }) => {
    const g = guard(request, 'SUPER_ADMIN')
    if (g) return g
    const p = priceQuotes.find((x) => x.id === Number(params.id))
    if (!p) return fail(404, 'NOT_FOUND', 'Không tìm thấy bảng giá')
    if (p.status !== 'DRAFT') return fail(409, 'PRICE_QUOTE_NOT_DRAFT', 'Chỉ sửa được bảng giá ở trạng thái Nháp')
    Object.assign(p, await request.json(), { updatedAt: new Date().toISOString() })
    return ok(p)
  }),
  http.post(`${B}/price-quotes/:id/lock`, ({ request, params }) => {
    const g = guard(request, 'SUPER_ADMIN')
    if (g) return g
    const p = priceQuotes.find((x) => x.id === Number(params.id))
    if (!p) return fail(404, 'NOT_FOUND', 'Không tìm thấy bảng giá')
    if (p.status !== 'DRAFT') return fail(409, 'PRICE_QUOTE_NOT_DRAFT', 'Chỉ khoá được bảng giá ở trạng thái Nháp')
    priceQuotes.forEach((x) => x.status === 'LOCKED' && (x.status = 'ENDED'))
    Object.assign(p, { status: 'LOCKED', lockedAt: new Date().toISOString(), lockedBy: currentUser(request)!.username })
    audit(currentUser(request)!.username, 'LOCK', 'PRICE_QUOTE', String(p.id), `Khoá bảng giá "${p.name}"`, null)
    return ok(p)
  }),
  http.delete(`${B}/price-quotes/:id`, ({ request, params }) => {
    const g = guard(request, 'SUPER_ADMIN')
    if (g) return g
    const idx = priceQuotes.findIndex((x) => x.id === Number(params.id))
    if (idx < 0) return fail(404, 'NOT_FOUND', 'Không tìm thấy bảng giá')
    if (priceQuotes[idx].status !== 'DRAFT') return fail(409, 'PRICE_QUOTE_NOT_DRAFT', 'Chỉ xoá được bảng giá ở trạng thái Nháp')
    priceQuotes.splice(idx, 1)
    return ok(null)
  }),

  // ───── Nhật ký ─────
  http.get(`${B}/audit-logs`, ({ request }) => {
    const u = currentUser(request)
    if (!u) return fail(401, 'UNAUTHORIZED', 'Chưa đăng nhập')
    const url = new URL(request.url)
    const ws = workspaceId(request)
    if (u.systemRole !== 'SUPER_ADMIN' && (!ws || !permissionsOf(u.id, ws).includes('REPORT_VIEW'))) return fail(403, 'FORBIDDEN', 'Bạn không có quyền xem nhật ký')
    const from = q(url, 'from')
    const to = q(url, 'to')
    const list = auditLogs.filter(
      (a) =>
        (u.systemRole === 'SUPER_ADMIN' || a.workspaceId === ws) &&
        includes(a.actor, q(url, 'actor')) &&
        (!q(url, 'actionType') || a.actionType === q(url, 'actionType')) &&
        (!q(url, 'entityType') || a.entityType === q(url, 'entityType')) &&
        (!q(url, 'result') || a.result === q(url, 'result')) &&
        (!from || a.createdAt >= from) &&
        (!to || a.createdAt <= to + 'T23:59:59'),
    )
    return paged(list, url)
  }),
  http.get(`${B}/audit-logs/export`, () => {
    const rows = ['Thời gian,Người thực hiện,Hành động,Đối tượng,Nội dung', ...auditLogs.map((a) => [a.createdAt, a.actor, a.actionType, a.entityType, `"${a.summary ?? ''}"`].join(','))]
    return new HttpResponse('﻿' + rows.join('\n'), { headers: { 'Content-Type': 'text/csv' } })
  }),
]
