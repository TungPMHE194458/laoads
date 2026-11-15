import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Copy, KeyRound, RefreshCw, Trash2 } from 'lucide-react'
import { errorMessage } from '@/api/client'
import { useApiKey, useRevokeApiKey, useRotateApiKey } from '@/features/workspace/api'
import { ConfirmDialog } from '@/components/common/dialogs'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/misc'
import { fmtDateTime } from '@/lib/format'

/**
 * API key sự kiện của workspace (quyền SECRET_KEY_MANAGE): hệ thống ngoài gọi POST /api/v1/events kèm header
 * X-Api-Key để đưa thuê bao vào chiến dịch theo sự kiện. Key đầy đủ chỉ hiển thị đúng một lần sau khi tạo.
 */
export function ApiKeyCard({ wsId }: { wsId: number }) {
  const { t } = useTranslation()
  const info = useApiKey(wsId)
  const rotate = useRotateApiKey(wsId)
  const revoke = useRevokeApiKey(wsId)
  const [confirm, setConfirm] = React.useState<'rotate' | 'revoke' | null>(null)
  const [fresh, setFresh] = React.useState<string | null>(null)
  const k = info.data

  const doRotate = () =>
    rotate.mutate(undefined, {
      onSuccess: (r) => {
        setConfirm(null)
        setFresh(r.key)
      },
      onError: (e) => toast.error(errorMessage(e)),
    })
  const doRevoke = () =>
    revoke.mutate(undefined, {
      onSuccess: () => {
        setConfirm(null)
        toast.success(t('apiKey.revoked'))
      },
      onError: (e) => toast.error(errorMessage(e)),
    })
  const copy = async () => {
    if (!fresh) return
    try {
      await navigator.clipboard.writeText(fresh)
      toast.success(t('apiKey.copied'))
    } catch {
      toast.error(t('apiKey.copyFailed'))
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <KeyRound className="size-4 text-brand-600" />
          {t('apiKey.title')}
        </CardTitle>
        <CardDescription>{t('apiKey.desc')}</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        {info.isLoading ? (
          <Skeleton className="h-10" />
        ) : (
          <div className="rounded-lg border bg-muted/30 px-4 py-3 text-sm">
            {k?.configured ? (
              <>
                <div className="font-mono">lads_••••••••{k.hint?.replace('…', '')}</div>
                <div className="mt-1 text-xs text-muted-foreground">{t('apiKey.rotatedAt', { at: fmtDateTime(k.rotatedAt) })}</div>
              </>
            ) : (
              <span className="text-muted-foreground">{t('apiKey.none')}</span>
            )}
          </div>
        )}
        <div className="flex flex-wrap gap-2">
          <Button size="sm" onClick={() => (k?.configured ? setConfirm('rotate') : doRotate())} loading={rotate.isPending}>
            <RefreshCw />
            {k?.configured ? t('apiKey.rotate') : t('apiKey.create')}
          </Button>
          {k?.configured && (
            <Button size="sm" variant="outline" onClick={() => setConfirm('revoke')} loading={revoke.isPending}>
              <Trash2 />
              {t('apiKey.revoke')}
            </Button>
          )}
        </div>
        <p className="text-xs text-muted-foreground">{t('apiKey.usage')}</p>
      </CardContent>

      <ConfirmDialog
        open={confirm === 'rotate'}
        onOpenChange={(o) => !o && setConfirm(null)}
        title={t('apiKey.rotateTitle')}
        description={t('apiKey.rotateDesc')}
        confirmLabel={t('apiKey.rotate')}
        loading={rotate.isPending}
        onConfirm={doRotate}
      />
      <ConfirmDialog
        open={confirm === 'revoke'}
        onOpenChange={(o) => !o && setConfirm(null)}
        title={t('apiKey.revokeTitle')}
        description={t('apiKey.revokeDesc')}
        confirmLabel={t('apiKey.revoke')}
        destructive
        loading={revoke.isPending}
        onConfirm={doRevoke}
      />

      <Dialog open={!!fresh} onOpenChange={(o) => !o && setFresh(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('apiKey.freshTitle')}</DialogTitle>
            <DialogDescription>{t('apiKey.freshDesc')}</DialogDescription>
          </DialogHeader>
          <code className="block rounded-lg border bg-muted/40 p-3 font-mono text-sm break-all select-all">{fresh}</code>
          <DialogFooter>
            <Button variant="outline" onClick={copy}>
              <Copy />
              {t('apiKey.copy')}
            </Button>
            <Button onClick={() => setFresh(null)}>{t('common.done')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  )
}

