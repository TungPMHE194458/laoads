import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { RotateCcw, Save, ShieldCheck } from 'lucide-react'
import { errorMessage } from '@/api/client'
import type { Permission, WorkspaceRole } from '@/api/types'
import { usePermissionDefs, useRoles, useSaveRolePermissions } from '@/features/admin/api'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Checkbox, Skeleton } from '@/components/ui/misc'
import { cn } from '@/lib/utils'

const ROLES: WorkspaceRole[] = ['OWNER', 'ADMIN', 'MEMBER']

export default function RolesPage() {
  const { t } = useTranslation()
  const defs = usePermissionDefs()
  const roles = useRoles()
  const save = useSaveRolePermissions()
  const original = React.useMemo(() => {
    if (!roles.data) return null
    return Object.fromEntries(roles.data.map((r) => [r.code, new Set(r.permissions)])) as Record<WorkspaceRole, Set<Permission>>
  }, [roles.data])
  // Bản chỉnh sửa cục bộ; null = đang hiển thị đúng dữ liệu server
  const [edits, setMatrix] = React.useState<Record<WorkspaceRole, Set<Permission>> | null>(null)
  const matrix = edits ?? original

  const dirtyRoles = ROLES.filter((r) => matrix && original && (matrix[r].size !== original[r].size || [...matrix[r]].some((p) => !original[r].has(p))))
  const toggle = (r: WorkspaceRole, p: Permission) =>
    setMatrix((cur) => {
      const m = cur ?? original
      if (!m) return cur
      const s = new Set(m[r])
      if (s.has(p)) s.delete(p)
      else s.add(p)
      return { ...m, [r]: s }
    })

  const groups = React.useMemo(() => {
    const g = new Map<string, Permission[]>()
    defs.data?.forEach((d) => g.set(d.group, [...(g.get(d.group) ?? []), d.code]))
    return [...g.entries()]
  }, [defs.data])

  const saveAll = async () => {
    if (!matrix) return
    try {
      for (const r of dirtyRoles) await save.mutateAsync({ code: r, permissions: [...matrix[r]] })
      setMatrix(null)
      toast.success(t('roles.saved'))
    } catch (e) {
      toast.error(errorMessage(e))
    }
  }

  return (
    <>
      <PageHeader
        icon={<ShieldCheck />}
        title={t('nav.adminRoles')}
        description={t('roles.desc')}
        actions={
          <>
            <Button variant="outline" disabled={!dirtyRoles.length} onClick={() => setMatrix(null)}>
              <RotateCcw />
              {t('roles.reset')}
            </Button>
            <Button disabled={!dirtyRoles.length} loading={save.isPending} onClick={saveAll}>
              <Save />
              {t('common.saveChanges')}
            </Button>
          </>
        }
      />
      <Card className="overflow-hidden">
        {!matrix || defs.isLoading ? (
          <Skeleton className="m-6 h-96" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-muted/50">
                  <th className="px-5 py-3 text-left text-xs font-medium text-muted-foreground uppercase">{t('roles.permission')}</th>
                  {ROLES.map((r) => (
                    <th key={r} className="w-40 px-4 py-3 text-center">
                      <div className="text-sm font-semibold">{t(`role.${r}`)}</div>
                      <div className="text-xs font-normal text-muted-foreground tabular">{t('roles.count', { n: matrix[r].size })}</div>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {groups.map(([g, perms]) => (
                  <React.Fragment key={g}>
                    <tr className="bg-muted/25">
                      <td colSpan={4} className="px-5 py-2 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
                        {t(`permGroup.${g}`)}
                      </td>
                    </tr>
                    {perms.map((p) => (
                      <tr key={p} className="border-b last:border-0 hover:bg-muted/30">
                        <td className="px-5 py-3">
                          <div className="font-medium">{t(`perm.${p}`)}</div>
                          <div className="font-mono text-[11px] text-muted-foreground">{p}</div>
                        </td>
                        {ROLES.map((r) => {
                          const changed = original && original[r].has(p) !== matrix[r].has(p)
                          return (
                            <td key={r} className={cn('px-4 py-3 text-center', changed && 'bg-accent/60')}>
                              <Checkbox className="mx-auto" checked={matrix[r].has(p)} onCheckedChange={() => toggle(r, p)} aria-label={`${r} ${p}`} />
                            </td>
                          )
                        })}
                      </tr>
                    ))}
                  </React.Fragment>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
      <p className="mt-3 text-xs text-muted-foreground">{t('roles.note')}</p>
    </>
  )
}
