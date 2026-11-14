import logo from '@/assets/unitel-logo.png'
import { cn } from '@/lib/utils'

/** Logo Unitel + wordmark LaoAds. */
export function Brand({ collapsed, className, inverted }: { collapsed?: boolean; className?: string; inverted?: boolean }) {
  return (
    <div className={cn('flex items-center gap-2.5', className)}>
      <img src={logo} alt="Unitel" className="size-9 shrink-0 rounded-[10px] shadow-sm ring-1 ring-black/5" />
      {!collapsed && (
        <div className="leading-none">
          <div className={cn('text-[17px] font-bold tracking-tight', inverted ? 'text-white' : 'text-foreground')}>
            Lao<span className={inverted ? 'text-white/85' : 'text-brand-500'}>Ads</span>
          </div>
          <div className={cn('mt-1 text-[10.5px] font-medium tracking-[0.12em] uppercase', inverted ? 'text-white/70' : 'text-muted-foreground')}>
            by Unitel
          </div>
        </div>
      )}
    </div>
  )
}
