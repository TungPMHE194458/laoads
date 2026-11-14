import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Loader2, ShieldAlert } from 'lucide-react'
import { errorMessage } from '@/api/client'
import { useVsaLogin } from '@/features/auth/api'
import { Button } from '@/components/ui/button'

/** VSA (CAS) redirect về đây kèm ?ticket=ST-... → đổi lấy JWT LaoAds. */
export default function VsaCallbackPage() {
  const { t } = useTranslation()
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const vsa = useVsaLogin()
  const ticket = params.get('ticket')
  const started = React.useRef(false)

  React.useEffect(() => {
    if (!ticket || started.current) return
    started.current = true
    const service = `${location.origin}${location.pathname}`
    vsa.mutate({ ticket, service }, { onSuccess: () => navigate('/dashboard', { replace: true }) })
  }, [ticket, vsa, navigate])

  return (
    <div className="grid min-h-screen place-items-center p-6">
      {vsa.isError || !ticket ? (
        <div className="max-w-sm text-center">
          <ShieldAlert className="mx-auto size-10 text-danger" />
          <h1 className="mt-4 text-lg font-semibold">{t('login.vsaFailed')}</h1>
          <p className="mt-2 text-sm text-muted-foreground">{ticket ? errorMessage(vsa.error) : t('login.vsaNoTicket')}</p>
          <Button asChild className="mt-6">
            <Link to="/login">{t('login.backToLogin')}</Link>
          </Button>
        </div>
      ) : (
        <div className="flex items-center gap-3 text-sm text-muted-foreground">
          <Loader2 className="size-5 animate-spin text-brand-500" />
          {t('login.vsaProcessing')}
        </div>
      )}
    </div>
  )
}
