import * as React from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/lib/utils'

const badgeVariants = cva(
  'inline-flex items-center gap-1 whitespace-nowrap rounded-full border px-2 py-0.5 text-xs font-medium [&>svg]:size-3 [&>svg]:shrink-0',
  {
    variants: {
      variant: {
        neutral: 'border-transparent bg-muted text-muted-foreground',
        brand: 'border-transparent bg-accent text-accent-foreground',
        success: 'border-transparent bg-success-soft text-success',
        warning: 'border-transparent bg-warning-soft text-warning',
        info: 'border-transparent bg-info-soft text-info',
        danger: 'border-transparent bg-danger-soft text-danger',
        outline: 'border-border text-foreground',
      },
    },
    defaultVariants: { variant: 'neutral' },
  },
)

export type BadgeVariant = NonNullable<VariantProps<typeof badgeVariants>['variant']>

function Badge({ className, variant, ...props }: React.ComponentProps<'span'> & VariantProps<typeof badgeVariants>) {
  return <span data-slot="badge" className={cn(badgeVariants({ variant }), className)} {...props} />
}

/** Badge có chấm tròn trạng thái phía trước — trạng thái không chỉ dựa vào màu (luôn kèm nhãn). */
function StatusDot({ variant = 'neutral', children, className }: { variant?: BadgeVariant; children: React.ReactNode; className?: string }) {
  const dot: Record<BadgeVariant, string> = {
    neutral: 'bg-muted-foreground/60',
    brand: 'bg-brand-500',
    success: 'bg-success',
    warning: 'bg-warning',
    info: 'bg-info',
    danger: 'bg-danger',
    outline: 'bg-foreground/50',
  }
  return (
    <Badge variant={variant} className={className}>
      <span className={cn('size-1.5 rounded-full', dot[variant])} aria-hidden />
      {children}
    </Badge>
  )
}

export { Badge, StatusDot, badgeVariants }
