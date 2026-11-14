import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { AuthTokens, UserDto } from '@/api/types'

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  user: UserDto | null
  setTokens: (t: AuthTokens) => void
  setUser: (u: UserDto) => void
  clear: () => void
}

/** Token + user lưu localStorage key `laoads_auth`. */
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      setTokens: (t) => set({ accessToken: t.accessToken, refreshToken: t.refreshToken, user: t.user }),
      setUser: (u) => set({ user: u }),
      clear: () => set({ accessToken: null, refreshToken: null, user: null }),
    }),
    { name: 'laoads_auth' },
  ),
)
