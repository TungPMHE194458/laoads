import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { AlertTriangle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'

export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel,
  destructive,
  loading,
  onConfirm,
  children,
}: {
  open: boolean
  onOpenChange: (o: boolean) => void
  title: React.ReactNode
  description?: React.ReactNode
  confirmLabel?: string
  destructive?: boolean
  loading?: boolean
  onConfirm: () => void
  children?: React.ReactNode
}) {
  const { t } = useTranslation()
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md" hideClose>
        <div className="flex gap-4">
          {destructive && (
            <span className="grid size-10 shrink-0 place-items-center rounded-full bg-danger-soft text-danger">
              <AlertTriangle className="size-5" />
            </span>
          )}
          <DialogHeader className="pr-0">
            <DialogTitle>{title}</DialogTitle>
            {description && <DialogDescription>{description}</DialogDescription>}
          </DialogHeader>
        </div>
        {children}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={loading}>
            {t('common.cancel')}
          </Button>
          <Button variant={destructive ? 'destructive' : 'default'} loading={loading} onClick={onConfirm}>
            {confirmLabel ?? t('common.confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export function FormDialog({
  open,
  onOpenChange,
  title,
  description,
  submitLabel,
  loading,
  onSubmit,
  children,
  className,
}: {
  open: boolean
  onOpenChange: (o: boolean) => void
  title: React.ReactNode
  description?: React.ReactNode
  submitLabel?: string
  loading?: boolean
  onSubmit: () => void
  children: React.ReactNode
  className?: string
}) {
  const { t } = useTranslation()
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className={cn('max-w-lg', className)}>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          {description && <DialogDescription>{description}</DialogDescription>}
        </DialogHeader>
        <form
          className="grid gap-4"
          onSubmit={(e) => {
            e.preventDefault()
            onSubmit()
          }}
        >
          {children}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={loading}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" loading={loading}>
              {submitLabel ?? t('common.save')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

/** Trường form: label + control + lỗi/ghi chú. */
export function Field({
  label,
  htmlFor,
  required,
  error,
  hint,
  children,
  className,
}: {
  label: React.ReactNode
  htmlFor?: string
  required?: boolean
  error?: string | null
  hint?: React.ReactNode
  children: React.ReactNode
  className?: string
}) {
  return (
    <div className={cn('grid gap-1.5', className)}>
      <Label htmlFor={htmlFor}>
        {label}
        {required && <span className="text-brand-600">*</span>}
      </Label>
      {children}
      {error ? <p className="text-xs text-danger">{error}</p> : hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
    </div>
  )
}
