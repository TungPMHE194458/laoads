import {
  BadgeCheck,
  Building2,
  LayoutDashboard,
  Layers,
  Megaphone,
  ScrollText,
  Send,
  ShieldCheck,
  UserCog,
  Users,
  UsersRound,
  Wallet,
  type LucideIcon,
} from 'lucide-react'
import type { Permission } from '@/api/types'

export interface NavItem {
  key: string
  to: string
  icon: LucideIcon
  /** Quyền cần trong workspace hiện tại. */
  perm?: Permission
  /** Chỉ SUPER_ADMIN. */
  superAdmin?: boolean
  /** Hiện nếu SUPER_ADMIN hoặc có perm. */
  badge?: 'approvals'
}
export interface NavGroup {
  key: string
  items: NavItem[]
}

export const NAV: NavGroup[] = [
  {
    key: 'operate',
    items: [
      { key: 'dashboard', to: '/dashboard', icon: LayoutDashboard, perm: 'REPORT_VIEW' },
      { key: 'campaigns', to: '/campaigns', icon: Megaphone, perm: 'CAMPAIGN_VIEW' },
      { key: 'approvals', to: '/approvals', icon: BadgeCheck, perm: 'CAMPAIGN_APPROVE', badge: 'approvals' },
      { key: 'segments', to: '/segments', icon: UsersRound, perm: 'CAMPAIGN_VIEW' },
    ],
  },
  {
    key: 'workspace',
    items: [
      { key: 'workspaceInfo', to: '/workspace', icon: Building2, perm: 'WORKSPACE_VIEW' },
      { key: 'members', to: '/workspace/members', icon: UserCog, perm: 'WORKSPACE_VIEW' },
      { key: 'priceQuotes', to: '/price-quotes', icon: Wallet },
      { key: 'auditLogs', to: '/audit-logs', icon: ScrollText, perm: 'REPORT_VIEW' },
    ],
  },
  {
    key: 'system',
    items: [
      { key: 'adminWorkspaces', to: '/admin/workspaces', icon: Layers, superAdmin: true },
      { key: 'adminUsers', to: '/admin/users', icon: Users, superAdmin: true },
      { key: 'adminRoles', to: '/admin/roles', icon: ShieldCheck, superAdmin: true },
      { key: 'adminSending', to: '/admin/sending', icon: Send, superAdmin: true },
    ],
  },
]
