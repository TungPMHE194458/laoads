import { create } from 'zustand'
import { persist } from 'zustand/middleware'

type Theme = 'light' | 'dark' | 'system'

interface UiState {
  /** Workspace đang làm việc — gửi trong header X-Workspace-Id. */
  workspaceId: number | null
  sidebarCollapsed: boolean
  theme: Theme
  setWorkspaceId: (id: number | null) => void
  toggleSidebar: () => void
  setTheme: (t: Theme) => void
}

export const useUiStore = create<UiState>()(
  persist(
    (set) => ({
      workspaceId: null,
      sidebarCollapsed: false,
      theme: 'system',
      setWorkspaceId: (id) => set({ workspaceId: id }),
      toggleSidebar: () => set((s) => ({ sidebarCollapsed: !s.sidebarCollapsed })),
      setTheme: (theme) => {
        applyTheme(theme)
        set({ theme })
      },
    }),
    { name: 'laoads_ui' },
  ),
)

export function applyTheme(theme: Theme) {
  const dark = theme === 'dark' || (theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches)
  document.documentElement.classList.toggle('dark', dark)
  try {
    localStorage.setItem('laoads_theme', theme)
  } catch {
    /* private mode */
  }
}
