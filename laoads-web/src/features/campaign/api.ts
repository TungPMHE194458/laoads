/*
 * Chiến dịch / nhóm / phê duyệt / dashboard / tệp KH / cài đặt gửi tin / API key — BE thật (đợt 2–3).
 * Chế độ mock dùng src/mocks/handlers/campaign.ts (cùng shape).
 */
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { cleanParams, request, requestPage } from '@/api/client'
import type {
  CampaignAction,
  CampaignDetail,
  CampaignGroup,
  CampaignInput,
  CampaignReport,
  CampaignSummary,
  ChannelStat,
  DashboardSummary,
  NotificationSettings,
  PageQuery,
  Segment,
  SegmentAttribute,
  SegmentCondition,
  SegmentMember,
  SenderNumber,
  TimePoint,
} from '@/api/types'
import { useUiStore } from '@/stores/ui'

const useWs = () => useUiStore((s) => s.workspaceId)

// ───── Dashboard ─────
export function useDashboardSummary(periodDays = 7) {
  return useQuery({
    queryKey: ['dashboard', 'summary', useWs(), periodDays],
    queryFn: () => request<DashboardSummary>({ url: '/dashboard/summary', params: { periodDays } }),
  })
}
export function useDashboardTrend(days = 30) {
  return useQuery({
    queryKey: ['dashboard', 'trend', useWs(), days],
    queryFn: () => request<TimePoint[]>({ url: '/dashboard/trend', params: { days } }),
  })
}
export function useDashboardChannels(days = 30) {
  return useQuery({
    queryKey: ['dashboard', 'channels', useWs(), days],
    queryFn: () => request<ChannelStat[]>({ url: '/dashboard/channels', params: { days } }),
  })
}

// ───── Chiến dịch ─────
export interface CampaignQuery extends PageQuery {
  status?: string
  groupId?: number | ''
  mode?: string
}
export function useCampaigns(q: CampaignQuery) {
  return useQuery({
    queryKey: ['campaigns', useWs(), q],
    queryFn: () => requestPage<CampaignSummary>({ url: '/campaigns', params: cleanParams({ ...q }) }),
    placeholderData: keepPreviousData,
  })
}
export function useCampaign(id: number | null) {
  return useQuery({
    queryKey: ['campaign', id],
    queryFn: () => request<CampaignDetail>({ url: `/campaigns/${id}` }),
    enabled: id != null && Number.isFinite(id),
  })
}
export function useCampaignReport(id: number | null, bucket: 'DAY' | 'WEEK' | 'MONTH') {
  return useQuery({
    queryKey: ['campaign', id, 'report', bucket],
    queryFn: () => request<CampaignReport>({ url: `/campaigns/${id}/report`, params: { bucket } }),
    enabled: id != null && Number.isFinite(id),
  })
}
export function useCampaignGroups() {
  return useQuery({ queryKey: ['campaign-groups', useWs()], queryFn: () => request<CampaignGroup[]>({ url: '/campaign-groups' }) })
}

function useInvalidateCampaigns() {
  const qc = useQueryClient()
  return () => {
    qc.invalidateQueries({ queryKey: ['campaigns'] })
    qc.invalidateQueries({ queryKey: ['campaign'] })
    qc.invalidateQueries({ queryKey: ['approvals'] })
    qc.invalidateQueries({ queryKey: ['dashboard'] })
  }
}

export function useSaveCampaign() {
  const inv = useInvalidateCampaigns()
  return useMutation({
    mutationFn: ({ id, submit, ...body }: CampaignInput & { id?: number; submit?: boolean }) =>
      request<CampaignDetail>({
        url: id ? `/campaigns/${id}` : '/campaigns',
        method: id ? 'PUT' : 'POST',
        data: body,
        params: submit ? { submit: true } : undefined,
      }),
    onSuccess: inv,
  })
}

export type CampaignCommand = 'submit' | 'pause' | 'resume' | 'cancel' | 'duplicate'
export function useCampaignCommand() {
  const inv = useInvalidateCampaigns()
  return useMutation({
    mutationFn: ({ id, cmd }: { id: number; cmd: CampaignCommand }) =>
      request<CampaignDetail>({ url: `/campaigns/${id}/${cmd}`, method: 'POST' }),
    onSuccess: inv,
  })
}
export function useDeleteCampaign() {
  const inv = useInvalidateCampaigns()
  return useMutation({
    mutationFn: (id: number) => request<null>({ url: `/campaigns/${id}`, method: 'DELETE' }),
    onSuccess: inv,
  })
}
export function useTestSend() {
  return useMutation({
    mutationFn: ({ id, isdns, actions }: { id?: number; isdns: string[]; actions?: CampaignAction[] }) =>
      request<{ isdn: string; channel: string; status: 'SENT' | 'FAILED'; detail: string }[]>({
        url: '/campaigns/test-send',
        method: 'POST',
        // actions: nội dung đang soạn trong wizard (chưa lưu) — BE ưu tiên dùng thay cho bản đã lưu
        data: { campaignId: id, isdns, actions },
      }),
  })
}

// ───── Phê duyệt ─────
export function useApprovals(q: PageQuery & { mode?: string }, enabled = true) {
  return useQuery({
    queryKey: ['approvals', useWs(), q],
    queryFn: () => requestPage<CampaignSummary>({ url: '/campaign-approvals', params: cleanParams({ ...q }) }),
    placeholderData: keepPreviousData,
    enabled,
  })
}
export function useReviewCampaign() {
  const inv = useInvalidateCampaigns()
  return useMutation({
    mutationFn: ({ id, decision, reason }: { id: number; decision: 'approve' | 'reject'; reason?: string }) =>
      request<CampaignDetail>({ url: `/campaign-approvals/${id}/${decision}`, method: 'POST', data: { reason } }),
    onSuccess: inv,
  })
}

// ───── Tệp khách hàng ─────
export interface SegmentQuery extends PageQuery {
  type?: string
  status?: string
}
export function useSegments(q: SegmentQuery) {
  return useQuery({
    queryKey: ['segments', useWs(), q],
    queryFn: () => requestPage<Segment>({ url: '/segments', params: cleanParams({ ...q }) }),
    placeholderData: keepPreviousData,
  })
}
export function useSegment(id: number | null) {
  return useQuery({
    queryKey: ['segment', id],
    queryFn: () => request<Segment>({ url: `/segments/${id}` }),
    enabled: id != null && Number.isFinite(id),
  })
}
export function useSegmentMembers(id: number | null, page: number) {
  return useQuery({
    queryKey: ['segment', id, 'members', page],
    queryFn: () => requestPage<SegmentMember>({ url: `/segments/${id}/members`, params: { page, size: 10 } }),
    enabled: id != null,
    placeholderData: keepPreviousData,
  })
}
export function useSegmentAttributes() {
  return useQuery({ queryKey: ['segment-attributes'], queryFn: () => request<SegmentAttribute[]>({ url: '/segment-attributes' }), staleTime: Infinity })
}
export function useEstimateSegment() {
  return useMutation({
    mutationFn: (conditions: SegmentCondition[]) =>
      request<{ estimatedCount: number }>({ url: '/segments/estimate', method: 'POST', data: { conditions } }),
  })
}
export function useCreateSegment() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { name: string; description?: string; type: 'QUERY' | 'FILE'; conditions?: SegmentCondition[]; file?: File }) => {
      if (body.type === 'FILE' && body.file) {
        // Tệp từ file: gửi file thật (multipart) — BE đọc cột isdn / msisdn / phone hoặc cột đầu tiên
        const form = new FormData()
        form.append('file', body.file)
        form.append('name', body.name)
        if (body.description) form.append('description', body.description)
        return request<Segment>({ url: '/segments/import', method: 'POST', data: form, timeout: 300_000 })
      }
      return request<Segment>({ url: '/segments', method: 'POST', data: { name: body.name, description: body.description, type: 'QUERY', conditions: body.conditions } })
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['segments'] }),
  })
}
export function useDeleteSegments() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (ids: number[]) => request<{ deleted: number; failed: { id: number; reason: string }[] }>({ url: '/segments/bulk-delete', method: 'POST', data: { ids } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['segments'] }),
  })
}

// ───── Cài đặt gửi tin ─────
export function useSenderNumbers() {
  return useQuery({ queryKey: ['settings', 'senders'], queryFn: () => request<SenderNumber[]>({ url: '/settings/sender-numbers' }) })
}
export function useSaveSender() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...body }: Partial<SenderNumber> & { id?: number }) =>
      request<SenderNumber>({ url: id ? `/settings/sender-numbers/${id}` : '/settings/sender-numbers', method: id ? 'PUT' : 'POST', data: body }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['settings', 'senders'] }),
  })
}
export function useNotificationSettings() {
  return useQuery({ queryKey: ['settings', 'notification'], queryFn: () => request<NotificationSettings>({ url: '/settings/notification' }) })
}
export function useSaveNotificationSettings() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: NotificationSettings) => request<NotificationSettings>({ url: '/settings/notification', method: 'PUT', data: body }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['settings', 'notification'] }),
  })
}

// ───── Nhóm chiến dịch ─────
export function useCreateCampaignGroup() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (name: string) => request<CampaignGroup>({ url: '/campaign-groups', method: 'POST', data: { name } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['campaign-groups'] }),
  })
}

// ───── Mục tiêu MANUAL_FILE: tải danh sách số thuê bao đã chuyển đổi (SRS 3.2.6.2) ─────
export function useImportConversions(id: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => {
      const form = new FormData()
      form.append('file', file)
      return request<{ matched: number; rows: number; invalidRows: number }>({ url: `/campaigns/${id}/conversions/import`, method: 'POST', data: form, timeout: 300_000 })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['campaign', id] })
      qc.invalidateQueries({ queryKey: ['campaigns'] })
    },
  })
}
