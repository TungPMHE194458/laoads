import * as React from 'react'
import { createBrowserRouter, Navigate } from 'react-router-dom'
import { AppShell } from '@/components/layout/AppShell'
import { FullScreenLoader, RequireAuth, RequirePermission } from '@/components/layout/guards'
import { ForbiddenPage, NotFoundPage } from '@/pages/misc/StatusPages'

const L = (f: () => Promise<{ default: React.ComponentType }>) => {
  const C = React.lazy(f)
  return (
    <React.Suspense fallback={<FullScreenLoader />}>
      <C />
    </React.Suspense>
  )
}

export const router = createBrowserRouter([
  { path: '/login', element: L(() => import('@/pages/auth/LoginPage')) },
  { path: '/auth/vsa/callback', element: L(() => import('@/pages/auth/VsaCallbackPage')) },
  {
    element: <RequireAuth needWorkspace={false} />,
    children: [{ path: '/select-workspace', element: L(() => import('@/pages/auth/SelectWorkspacePage')) }],
  },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppShell />,
        children: [
          { index: true, element: <Navigate to="/dashboard" replace /> },
          { path: '/dashboard', element: <RequirePermission perm="REPORT_VIEW">{L(() => import('@/pages/dashboard/DashboardPage'))}</RequirePermission> },
          { path: '/campaigns', element: <RequirePermission perm="CAMPAIGN_VIEW">{L(() => import('@/pages/campaigns/CampaignsPage'))}</RequirePermission> },
          { path: '/campaigns/new', element: <RequirePermission perm="CAMPAIGN_CREATE">{L(() => import('@/pages/campaigns/wizard/CampaignWizardPage'))}</RequirePermission> },
          { path: '/campaigns/:id', element: <RequirePermission perm="CAMPAIGN_VIEW">{L(() => import('@/pages/campaigns/CampaignDetailPage'))}</RequirePermission> },
          { path: '/campaigns/:id/edit', element: <RequirePermission perm="CAMPAIGN_UPDATE">{L(() => import('@/pages/campaigns/wizard/CampaignWizardPage'))}</RequirePermission> },
          { path: '/approvals', element: <RequirePermission perm="CAMPAIGN_APPROVE">{L(() => import('@/pages/approvals/ApprovalsPage'))}</RequirePermission> },
          { path: '/segments', element: <RequirePermission perm="CAMPAIGN_VIEW">{L(() => import('@/pages/segments/SegmentsPage'))}</RequirePermission> },
          { path: '/segments/:id', element: <RequirePermission perm="CAMPAIGN_VIEW">{L(() => import('@/pages/segments/SegmentDetailPage'))}</RequirePermission> },
          { path: '/workspace', element: <RequirePermission perm="WORKSPACE_VIEW">{L(() => import('@/pages/workspace/WorkspacePage'))}</RequirePermission> },
          { path: '/workspace/members', element: <RequirePermission perm="WORKSPACE_VIEW">{L(() => import('@/pages/workspace/MembersPage'))}</RequirePermission> },
          { path: '/price-quotes', element: L(() => import('@/pages/price/PriceQuotesPage')) },
          { path: '/audit-logs', element: <RequirePermission perm="REPORT_VIEW">{L(() => import('@/pages/audit/AuditLogPage'))}</RequirePermission> },
          { path: '/admin/workspaces', element: <RequirePermission superAdmin>{L(() => import('@/pages/admin/AdminWorkspacesPage'))}</RequirePermission> },
          { path: '/admin/users', element: <RequirePermission superAdmin>{L(() => import('@/pages/admin/AdminUsersPage'))}</RequirePermission> },
          { path: '/admin/roles', element: <RequirePermission superAdmin>{L(() => import('@/pages/admin/RolesPage'))}</RequirePermission> },
          { path: '/admin/sending', element: <RequirePermission superAdmin>{L(() => import('@/pages/admin/SendingSettingsPage'))}</RequirePermission> },
          { path: '/profile', element: L(() => import('@/pages/profile/ProfilePage')) },
          { path: '/403', element: <ForbiddenPage /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
])
