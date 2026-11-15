import { HttpResponse } from 'msw'
import { queryClient } from '@/api/queryClient'
import type { MeResponse, Permission, SystemRole } from '@/api/types'
import { meKey } from '@/features/auth/api'
import { members, roles, users } from './db'

type MockUser = (typeof users)[number]

export const ok = <T,>(data: T, status = 200) => HttpResponse.json({ success: true, data, message: null, statusCode: 'OK' }, { status })

export const fail = (status: number, statusCode: string, message: string) =>
  HttpResponse.json({ success: false, data: null, message, statusCode, requestId: Math.random().toString(36).slice(2, 8) }, { status })

/** Phân trang danh sách theo query `page`/`size` (page từ 0). */
export function paged<T>(all: T[], url: URL) {
  const page = Math.max(0, Number(url.searchParams.get('page') ?? 0) || 0)
  const size = Math.min(200, Math.max(1, Number(url.searchParams.get('size') ?? 10) || 10))
  const items = all.slice(page * size, page * size + size)
  return HttpResponse.json({
    success: true,
    data: items,
    statusCode: 'OK',
    metaData: { totalPages: Math.max(1, Math.ceil(all.length / size)), currentPage: page, pageSize: size, totalItems: all.length },
  })
}

export const q = (url: URL, k: string) => url.searchParams.get(k) ?? ''
export const includes = (hay: string | null | undefined, needle: string) => !needle || (hay ?? '').toLowerCase().includes(needle.toLowerCase())

interface JwtClaims {
  sub?: string
  uid?: number
  sr?: SystemRole
  exp?: number
}

/** Đọc payload JWT (base64url) — KHÔNG kiểm chữ ký: BE thật đã xác thực token ở các endpoint core (/me…). */
function decodeJwt(token: string): JwtClaims | null {
  const part = token.split('.')[1]
  if (!part) return null
  try {
    const b64 = part.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(part.length / 4) * 4, '=')
    const bytes = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0))
    return JSON.parse(new TextDecoder().decode(bytes)) as JwtClaims
  } catch {
    return null
  }
}

type Auth = { user: MockUser; realJwt: boolean } | { error: 'UNAUTHORIZED' | 'TOKEN_EXPIRED' }

/**
 * - mock:   token mock dạng `mock-access.<userId>.<ts>` (do handler core mock cấp).
 * - hybrid: token là JWT thật do BE cấp (claims sub = username, uid, sr = system role, exp). Map sang user mock
 *           theo username (seed trùng tài khoản BE); user chỉ có ở BE thật → dựng từ claims.
 *           Hết hạn → 401 TOKEN_EXPIRED để client refresh qua BE thật (KHÔNG trả UNAUTHORIZED — client sẽ logout).
 */
function authOf(request: Request): Auth {
  const header = request.headers.get('Authorization') ?? ''
  const mock = header.match(/^Bearer mock-access\.(\d+)\./)
  if (mock) {
    const u = users.find((x) => x.id === Number(mock[1]) && x.status === 'ACTIVE')
    return u ? { user: u, realJwt: false } : { error: 'UNAUTHORIZED' }
  }
  const claims = header.startsWith('Bearer ') ? decodeJwt(header.slice(7)) : null
  if (!claims?.sub) return { error: 'UNAUTHORIZED' }
  if (claims.exp && claims.exp * 1000 <= Date.now()) return { error: 'TOKEN_EXPIRED' }
  const known = users.find((x) => x.username === claims.sub)
  const user: MockUser = known ?? {
    id: claims.uid ?? 0,
    username: claims.sub,
    password: '',
    fullName: claims.sub,
    email: null,
    phone: null,
    systemRole: claims.sr ?? 'USER',
    status: 'ACTIVE',
    lastLoginAt: null,
    createdAt: new Date().toISOString(),
  }
  return { user, realJwt: true }
}

export function currentUser(request: Request) {
  const a = authOf(request)
  return 'user' in a ? a.user : null
}

/** hybrid: quyền thật theo /me của BE (FE đã cache trước khi vào trang) — khớp ma trận quyền + thành viên thật. */
function realPermissions(wsId: number): Permission[] | null {
  const me = queryClient.getQueryData<MeResponse>(meKey)
  return me?.workspaces.find((w) => w.id === wsId)?.permissions ?? null
}

export function workspaceId(request: Request) {
  const v = Number(request.headers.get('X-Workspace-Id'))
  return Number.isFinite(v) && v > 0 ? v : null
}

export function permissionsOf(userId: number, wsId: number): Permission[] {
  const u = users.find((x) => x.id === userId)
  if (u?.systemRole === 'SUPER_ADMIN') return roles[0].permissions
  const m = members.find((x) => x.userId === userId && x.workspaceId === wsId && x.status === 'ACTIVE')
  if (!m) return []
  return roles.find((r) => r.code === m.role)?.permissions ?? []
}

/** Trả về Response lỗi nếu không đủ quyền, ngược lại null. */
export function guard(request: Request, perm?: Permission | 'SUPER_ADMIN') {
  const a = authOf(request)
  if ('error' in a) {
    return a.error === 'TOKEN_EXPIRED'
      ? fail(401, 'TOKEN_EXPIRED', 'Phiên truy cập đã hết hạn')
      : fail(401, 'UNAUTHORIZED', 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn')
  }
  const u = a.user
  if (perm === 'SUPER_ADMIN') return u.systemRole === 'SUPER_ADMIN' ? null : fail(403, 'FORBIDDEN', 'Bạn không có quyền thực hiện thao tác này')
  if (perm) {
    const ws = workspaceId(request)
    if (!ws) return fail(400, 'WORKSPACE_REQUIRED', 'Thiếu header X-Workspace-Id')
    const perms = u.systemRole === 'SUPER_ADMIN' ? roles[0].permissions : ((a.realJwt ? realPermissions(ws) : null) ?? permissionsOf(u.id, ws))
    if (!perms.includes(perm)) return fail(403, 'FORBIDDEN', 'Bạn không có quyền thực hiện thao tác này')
  }
  return null
}
