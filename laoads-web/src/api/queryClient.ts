import { QueryClient } from '@tanstack/react-query'
import { ApiError } from './client'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 5 * 60_000,
      refetchOnWindowFocus: false,
      // Không retry lỗi nghiệp vụ 4xx — chỉ retry lỗi mạng/5xx một lần
      retry: (count, err) => !(err instanceof ApiError && err.status >= 400 && err.status < 500) && count < 1,
    },
  },
})
