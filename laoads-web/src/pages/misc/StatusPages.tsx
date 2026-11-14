import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { Compass, ShieldOff } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/common/EmptyState'

export function ForbiddenPage() {
  const { t } = useTranslation()
  return (
    <div className="mt-10 rounded-2xl border bg-card">
      <EmptyState
        icon={<ShieldOff />}
        title={t('status.forbiddenTitle')}
        description={t('status.forbiddenDesc')}
        action={
          <Button asChild variant="outline">
            <Link to="/dashboard">{t('status.backHome')}</Link>
          </Button>
        }
      />
    </div>
  )
}

export function NotFoundPage() {
  const { t } = useTranslation()
  return (
    <div className="mt-10 rounded-2xl border bg-card">
      <EmptyState
        icon={<Compass />}
        title={t('status.notFoundTitle')}
        description={t('status.notFoundDesc')}
        action={
          <Button asChild variant="outline">
            <Link to="/dashboard">{t('status.backHome')}</Link>
          </Button>
        }
      />
    </div>
  )
}
