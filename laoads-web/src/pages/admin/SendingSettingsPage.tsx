import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Hash, MoonStar, Plus, RefreshCcw, Send, Tag } from 'lucide-react'
import { errorMessage } from '@/api/client'
import type { NotificationSettings, SenderNumber } from '@/api/types'
import { useNotificationSettings, useSaveNotificationSettings, useSaveSender, useSenderNumbers } from '@/features/campaign/api'
import { PageHeader } from '@/components/common/PageHeader'
import { FormDialog, Field } from '@/components/common/dialogs'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Skeleton, Switch } from '@/components/ui/misc'

export default function SendingSettingsPage() {
  const { t } = useTranslation()
  const senders = useSenderNumbers()
  const saveSender = useSaveSender()
  const settings = useNotificationSettings()
  const saveSettings = useSaveNotificationSettings()
  // Bản chỉnh sửa cục bộ; null = đang hiển thị đúng dữ liệu server
  const [edit, setForm] = React.useState<NotificationSettings | null>(null)
  const form = edit ?? settings.data ?? null
  const [addOpen, setAddOpen] = React.useState(false)
  const [newSender, setNewSender] = React.useState<Omit<SenderNumber, 'id'>>({ code: '', kind: 'SHORTCODE', description: '', enabled: true })
  const onErr = (e: unknown) => toast.error(errorMessage(e))

  return (
    <>
      <PageHeader icon={<Send />} title={t('nav.adminSending')} description={t('sending.desc')} />
      <div className="grid gap-6 xl:grid-cols-2">
        <Card>
          <CardHeader>
            <div>
              <CardTitle>{t('sending.senders')}</CardTitle>
              <CardDescription>{t('sending.sendersDesc')}</CardDescription>
            </div>
            <Button size="sm" variant="soft" onClick={() => setAddOpen(true)}>
              <Plus />
              {t('sending.addSender')}
            </Button>
          </CardHeader>
          <CardContent className="px-0">
            {senders.isLoading ? (
              <Skeleton className="mx-5 h-48" />
            ) : (
              <ul>
                {senders.data?.map((s) => (
                  <li key={s.id} className="flex items-center gap-3 border-t px-5 py-3">
                    <span className="grid size-9 place-items-center rounded-lg bg-muted text-muted-foreground">{s.kind === 'SHORTCODE' ? <Hash className="size-4" /> : <Tag className="size-4" />}</span>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2 font-mono text-sm font-semibold">
                        {s.code}
                        <Badge variant="outline" className="font-sans">
                          {t(`sending.kind.${s.kind}`)}
                        </Badge>
                      </div>
                      <div className="truncate text-xs text-muted-foreground">{s.description}</div>
                    </div>
                    <Switch checked={s.enabled} onCheckedChange={(v) => saveSender.mutate({ ...s, enabled: v }, { onError: onErr })} aria-label={t('sending.enabled')} />
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <div>
              <CardTitle>{t('sending.policy')}</CardTitle>
              <CardDescription>{t('sending.policyDesc')}</CardDescription>
            </div>
          </CardHeader>
          <CardContent>
            {!form ? (
              <Skeleton className="h-64" />
            ) : (
              <form
                className="grid gap-6"
                onSubmit={(e) => {
                  e.preventDefault()
                  saveSettings.mutate(form, {
                    onSuccess: () => {
                      setForm(null)
                      toast.success(t('common.saved'))
                    },
                    onError: onErr,
                  })
                }}
              >
                <section className="grid gap-4">
                  <h4 className="flex items-center gap-2 text-sm font-semibold">
                    <RefreshCcw className="size-4 text-brand-600" />
                    {t('sending.retry')}
                  </h4>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <Field label={t('sending.retryMax')}>
                      <Input type="number" min={0} max={10} step="any" value={form.retryMaxAttempts} onChange={(e) => setForm({ ...form, retryMaxAttempts: Number(e.target.value) })} />
                    </Field>
                    <Field label={t('sending.retryInterval')}>
                      <Input type="number" min={1} step="any" value={form.retryIntervalMinutes} onChange={(e) => setForm({ ...form, retryIntervalMinutes: Number(e.target.value) })} />
                    </Field>
                  </div>
                  <label className="flex items-center gap-3 text-sm">
                    <Switch checked={form.refundOnFailure} onCheckedChange={(v) => setForm({ ...form, refundOnFailure: v })} />
                    {t('sending.refund')}
                  </label>
                </section>
                <section className="grid gap-4 border-t pt-5">
                  <h4 className="flex items-center gap-2 text-sm font-semibold">
                    <MoonStar className="size-4 text-brand-600" />
                    {t('campaign.wizard.silent')}
                  </h4>
                  <label className="flex items-center gap-3 text-sm">
                    <Switch checked={form.silentHoursEnabled} onCheckedChange={(v) => setForm({ ...form, silentHoursEnabled: v })} />
                    {t('sending.silentDefault')}
                  </label>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <Field label={t('common.from')}>
                      <Input type="time" value={form.silentFrom} disabled={!form.silentHoursEnabled} onChange={(e) => setForm({ ...form, silentFrom: e.target.value })} />
                    </Field>
                    <Field label={t('common.to')}>
                      <Input type="time" value={form.silentTo} disabled={!form.silentHoursEnabled} onChange={(e) => setForm({ ...form, silentTo: e.target.value })} />
                    </Field>
                  </div>
                </section>
                <section className="grid gap-4 border-t pt-5">
                  <Field label={t('sending.defaultLang')}>
                    <Select value={form.defaultLanguage} onValueChange={(v) => setForm({ ...form, defaultLanguage: v as NotificationSettings['defaultLanguage'] })} options={(['LO', 'EN', 'VI'] as const).map((l) => ({ value: l, label: t(`langName.${l}`) }))} />
                  </Field>
                </section>
                <div>
                  <Button type="submit" loading={saveSettings.isPending}>
                    {t('common.saveChanges')}
                  </Button>
                </div>
              </form>
            )}
          </CardContent>
        </Card>
      </div>

      <FormDialog
        open={addOpen}
        onOpenChange={setAddOpen}
        title={t('sending.addSender')}
        loading={saveSender.isPending}
        onSubmit={() =>
          saveSender.mutate(newSender, {
            onSuccess: () => {
              toast.success(t('common.saved'))
              setAddOpen(false)
              setNewSender({ code: '', kind: 'SHORTCODE', description: '', enabled: true })
            },
            onError: onErr,
          })
        }
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label={t('sending.code')} required>
            <Input value={newSender.code} onChange={(e) => setNewSender({ ...newSender, code: e.target.value.toUpperCase() })} className="font-mono" required />
          </Field>
          <Field label={t('sending.kindLabel')}>
            <Select value={newSender.kind} onValueChange={(v) => setNewSender({ ...newSender, kind: v as SenderNumber['kind'] })} options={(['SHORTCODE', 'BRANDNAME'] as const).map((k) => ({ value: k, label: t(`sending.kind.${k}`) }))} />
          </Field>
        </div>
        <Field label={t('segment.description')}>
          <Input value={newSender.description ?? ''} onChange={(e) => setNewSender({ ...newSender, description: e.target.value })} />
        </Field>
      </FormDialog>
    </>
  )
}
