import axios, { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import i18n from '@/i18n'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import type { AuthTokens, Envelope, Paged } from './types'

export const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api/v1'

/** Lỗi API đã chuẩn hoá từ envelope `{ success:false, message, statusCode }`. */
export class ApiError extends Error {
  status: number
  statusCode: string
  errors: { field: string; message: string }[]
  requestId?: string
  constructor(status: number, statusCode: string, message: string, errors: ApiError['errors'] = [], requestId?: string) {
    super(message)
    this.status = status
    this.statusCode = statusCode
    this.errors = errors
    this.requestId = requestId
  }
}

export const http = axios.create({ baseURL: API_BASE, timeout: 30_000 })

http.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  const ws = useUiStore.getState().workspaceId
  if (ws != null && !config.headers['X-Workspace-Id']) config.headers['X-Workspace-Id'] = String(ws)
  config.headers['Accept-Language'] = i18n.resolvedLanguage ?? 'vi'
  return config
})

// ── Refresh token: 1 request refresh dùng chung cho mọi request 401 TOKEN_EXPIRED đồng thời ──
let refreshing: Promise<string | null> | null = null

async function refreshAccessToken(): Promise<string | null> {
  const rt = useAuthStore.getState().refreshToken
  if (!rt) return null
  try {
    const res = await axios.post<Envelope<AuthTokens>>(`${API_BASE}/auth/refresh`, { refreshToken: rt })
    useAuthStore.getState().setTokens(res.data.data)
    return res.data.data.accessToken
  } catch {
    return null
  }
}

function forceLogout() {
  useAuthStore.getState().clear()
  if (!location.pathname.startsWith('/login')) {
    const back = encodeURIComponent(location.pathname + location.search)
    location.assign(`/login?redirect=${back}`)
  }
}

http.interceptors.response.use(
  (res) => res,
  async (error: AxiosError<Envelope<unknown>>) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
    const status = error.response?.status ?? 0
    const body = error.response?.data
    const isAuthCall = original?.url?.includes('/auth/login') || original?.url?.includes('/auth/refresh')

    if (status === 401 && original && !original._retried && !isAuthCall) {
      if (body?.statusCode === 'TOKEN_EXPIRED' || !body?.statusCode) {
        original._retried = true
        refreshing ??= refreshAccessToken().finally(() => (refreshing = null))
        const token = await refreshing
        if (token) {
          original.headers.Authorization = `Bearer ${token}`
          return http(original)
        }
      }
      forceLogout()
    }

    if (body && typeof body === 'object' && 'success' in body) {
      throw new ApiError(status, body.statusCode ?? 'ERROR', body.message ?? error.message, body.errors ?? [], body.requestId)
    }
    if (error.code === 'ECONNABORTED') throw new ApiError(0, 'TIMEOUT', i18n.t('errors.timeout'))
    if (!error.response) throw new ApiError(0, 'NETWORK', i18n.t('errors.network'))
    // Lỗi không có envelope (CORS bị chặn, proxy/nginx 502…) — không hiện chuỗi thô của axios
    throw new ApiError(status, 'HTTP_' + status, i18n.t('errors.http', { status }))
  },
)

function unwrap<T>(env: Envelope<T>, status: number): T {
  if (!env.success) throw new ApiError(status, env.statusCode ?? 'ERROR', env.message ?? 'Error', env.errors ?? [], env.requestId)
  return env.data
}

/** Gọi API trả về `data` đã bóc envelope. */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const res = await http.request<Envelope<T>>(config)
  return unwrap(res.data, res.status)
}

/** Gọi API dạng phân trang → `{ items, meta }`. */
export async function requestPage<T>(config: AxiosRequestConfig): Promise<Paged<T>> {
  const res = await http.request<Envelope<T[]>>(config)
  const items = unwrap(res.data, res.status)
  return {
    items,
    meta: res.data.metaData ?? { totalPages: 1, currentPage: 0, pageSize: items.length, totalItems: items.length },
  }
}

/** Tải file (xlsx…) — thành công không bọc envelope; lỗi (vd EXPORT_TOO_LARGE) BE trả envelope JSON trong blob. */
export async function download(url: string, params: Record<string, unknown>, filename: string) {
  let res
  try {
    res = await http.get<Blob>(url, { params, responseType: 'blob', timeout: 120_000 })
  } catch (e) {
    const data = (e as AxiosError<Blob>).response?.data
    if (data instanceof Blob) {
      try {
        const env = JSON.parse(await data.text()) as Envelope<unknown>
        throw new ApiError((e as AxiosError).response?.status ?? 0, env.statusCode ?? 'ERROR', env.message ?? 'Error', env.errors ?? [], env.requestId)
      } catch (inner) {
        if (inner instanceof ApiError) throw inner
      }
    }
    throw e
  }
  const href = URL.createObjectURL(res.data)
  const a = document.createElement('a')
  a.href = href
  a.download = filename
  a.click()
  URL.revokeObjectURL(href)
}

/** Lấy message hiển thị từ lỗi bất kỳ. */
export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) return e.message
  if (e instanceof Error) return e.message
  return String(e)
}

/** Bỏ các param rỗng/NaN trước khi gửi (NaN != null là true — không đủ để lọc). */
export function cleanParams<T extends Record<string, unknown>>(p: T): Partial<T> {
  const out: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(p)) {
    if (v === undefined || v === null || v === '') continue
    if (typeof v === 'number' && !Number.isFinite(v)) continue
    out[k] = v
  }
  return out as Partial<T>
}
