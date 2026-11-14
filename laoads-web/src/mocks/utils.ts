import { HttpResponse } from 'msw'
import type { Permission } from '@/api/types'
import { members, roles, users } from './db'

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

/** Token mock có dạng `mock-access.<userId>.<ts>`. */
export function currentUser(request: Request) {
  const auth = request.headers.get('Authorization') ?? ''
  const m = auth.match(/^Bearer mock-access\.(\d+)\./)
  if (!m) return null
  return users.find((u) => u.id === Number(m[1]) && u.status === 'ACTIVE') ?? null
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
  const u = currentUser(request)
  if (!u) return fail(401, 'UNAUTHORIZED', 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn')
  if (perm === 'SUPER_ADMIN') return u.systemRole === 'SUPER_ADMIN' ? null : fail(403, 'FORBIDDEN', 'Bạn không có quyền thực hiện thao tác này')
  if (perm) {
    const ws = workspaceId(request)
    if (!ws) return fail(400, 'WORKSPACE_REQUIRED', 'Thiếu header X-Workspace-Id')
    if (!permissionsOf(u.id, ws).includes(perm)) return fail(403, 'FORBIDDEN', 'Bạn không có quyền thực hiện thao tác này')
  }
  return null
}
