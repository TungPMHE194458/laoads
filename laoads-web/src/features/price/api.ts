import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { cleanParams, request, requestPage } from '@/api/client'
import type { PageQuery, PriceQuote, PriceQuoteInput } from '@/api/types'

const KEY = ['price-quotes'] as const

export function usePriceQuotes(q: PageQuery & { status?: string }) {
  return useQuery({
    queryKey: [...KEY, 'list', q],
    queryFn: () => requestPage<PriceQuote>({ url: '/price-quotes', params: cleanParams({ ...q }) }),
    placeholderData: keepPreviousData,
  })
}

export function useActivePriceQuote() {
  return useQuery({ queryKey: [...KEY, 'active'], queryFn: () => request<PriceQuote | null>({ url: '/price-quotes/active' }) })
}

export function usePriceQuote(id: number | null) {
  return useQuery({
    queryKey: [...KEY, id],
    queryFn: () => request<PriceQuote>({ url: `/price-quotes/${id}` }),
    enabled: id != null && Number.isFinite(id),
  })
}

export function useSavePriceQuote() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...body }: PriceQuoteInput & { id?: number }) =>
      id
        ? request<PriceQuote>({ url: `/price-quotes/${id}`, method: 'PUT', data: body })
        : request<PriceQuote>({ url: '/price-quotes', method: 'POST', data: body }),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

export function useLockPriceQuote() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => request<PriceQuote>({ url: `/price-quotes/${id}/lock`, method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

export function useDeletePriceQuote() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => request<null>({ url: `/price-quotes/${id}`, method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}
