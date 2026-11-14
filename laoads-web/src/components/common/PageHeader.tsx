import * as React from 'react'
import { Link } from 'react-router-dom'
import { ChevronRight } from 'lucide-react'
import { cn } from '@/lib/utils'

export interface Crumb {
  label: string
  to?: string
}

export function PageHeader({
  title,
  description,
  actions,
  crumbs,
  className,
  icon,
}: {
  title: React.ReactNode
  description?: React.ReactNode
  actions?: React.ReactNode
  crumbs?: Crumb[]
  className?: string
  icon?: React.ReactNode
}) {
  return (
    <div className={cn('mb-6 flex flex-col gap-4 md:flex-row md:items-end md:justify-between', className)}>
      <div className="min-w-0">
        {crumbs && crumbs.length > 0 && (
          <nav aria-label="breadcrumb" className="mb-2 flex flex-wrap items-center gap-1 text-[13px] text-muted-foreground">
            {crumbs.map((c, i) => (
              <React.Fragment key={i}>
                {i > 0 && <ChevronRight className="size-3.5 opacity-60" />}
                {c.to ? (
                  <Link to={c.to} className="transition hover:text-foreground">
                    {c.label}
                  </Link>
                ) : (
                  <span className="text-foreground/80">{c.label}</span>
                )}
              </React.Fragment>
            ))}
          </nav>
        )}
        <div className="flex items-center gap-3">
          {icon && <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-accent text-brand-600 [&_svg]:size-5">{icon}</span>}
          <div className="min-w-0">
            <h1 className="truncate text-[22px] font-semibold tracking-tight md:text-2xl">{title}</h1>
            {description && <p className="mt-1 text-sm text-muted-foreground">{description}</p>}
          </div>
        </div>
      </div>
      {actions && <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>}
    </div>
  )
}
