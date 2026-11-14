import { useMutation, useQuery } from '@tanstack/react-query'
import { request } from '@/api/client'
import { queryClient } from '@/api/queryClient'
import type { AuthTokens, MeResponse, VsaConfig } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'

export const meKey = ['me'] as const

export function useMe() {
  const token = useAuthStore((s) => s.accessToken)
  return useQuery({
    queryKey: meKey,
    queryFn: () => request<MeResponse>({ url: '/me' }),
    enabled: !!token,
    staleTime: 5 * 60_000,
  })
}

export function useLogin() {
  return useMutation({
    mutationFn: (body: { username: string; password: string }) =>
      request<AuthTokens>({ url: '/auth/login', method: 'POST', data: body }),
    onSuccess: (tokens) => {
      queryClient.clear()
      useAuthStore.getState().setTokens(tokens)
    },
  })
}

export function useVsaConfig() {
  return useQuery({
    queryKey: ['auth', 'vsa-config'],
    queryFn: () => request<VsaConfig>({ url: '/auth/vsa/config' }),
    staleTime: Infinity,
    retry: false,
  })
}

export function useVsaLogin() {
  return useMutation({
    mutationFn: (body: { ticket: string; service: string }) =>
      request<AuthTokens>({ url: '/auth/vsa/login', method: 'POST', data: body }),
    onSuccess: (tokens) => {
      queryClient.clear()
      useAuthStore.getState().setTokens(tokens)
    },
  })
}

export function useChangePassword() {
  return useMutation({
    mutationFn: (body: { oldPassword: string; newPassword: string }) =>
      request<null>({ url: '/auth/change-password', method: 'POST', data: body }),
  })
}

export async function logout() {
  const rt = useAuthStore.getState().refreshToken
  try {
    if (rt) await request<null>({ url: '/auth/logout', method: 'POST', data: { refreshToken: rt } })
  } catch {
    /* đăng xuất phía client vẫn tiếp tục */
  }
  useAuthStore.getState().clear()
  useUiStore.getState().setWorkspaceId(null)
  queryClient.clear()
}
