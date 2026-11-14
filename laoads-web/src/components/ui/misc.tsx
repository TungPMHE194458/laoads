import * as React from 'react'
import {
  Checkbox as CheckboxPrimitive,
  Switch as SwitchPrimitive,
  Tabs as TabsPrimitive,
  Tooltip as TooltipPrimitive,
  Separator as SeparatorPrimitive,
  Popover as PopoverPrimitive,
  RadioGroup as RadioPrimitive,
  Progress as ProgressPrimitive,
} from 'radix-ui'
import { CheckIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

/* ───────── Checkbox ───────── */
function Checkbox({ className, ...props }: React.ComponentProps<typeof CheckboxPrimitive.Root>) {
  return (
    <CheckboxPrimitive.Root
      className={cn(
        'peer size-4 shrink-0 cursor-pointer rounded-[5px] border border-input bg-card shadow-xs outline-none transition focus-visible:ring-[3px] focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 data-[state=checked]:border-primary data-[state=checked]:bg-primary data-[state=checked]:text-primary-foreground data-[state=indeterminate]:border-primary data-[state=indeterminate]:bg-primary data-[state=indeterminate]:text-primary-foreground',
        className,
      )}
      {...props}
    >
      <CheckboxPrimitive.Indicator className="flex items-center justify-center">
        {props.checked === 'indeterminate' ? <span className="h-0.5 w-2 rounded bg-current" /> : <CheckIcon className="size-3.5" strokeWidth={3} />}
      </CheckboxPrimitive.Indicator>
    </CheckboxPrimitive.Root>
  )
}

/* ───────── Switch ───────── */
function Switch({ className, ...props }: React.ComponentProps<typeof SwitchPrimitive.Root>) {
  return (
    <SwitchPrimitive.Root
      className={cn(
        'peer inline-flex h-5 w-9 shrink-0 cursor-pointer items-center rounded-full border border-transparent shadow-xs outline-none transition focus-visible:ring-[3px] focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 data-[state=checked]:bg-primary data-[state=unchecked]:bg-input',
        className,
      )}
      {...props}
    >
      <SwitchPrimitive.Thumb className="pointer-events-none block size-4 rounded-full bg-white shadow ring-0 transition-transform data-[state=checked]:translate-x-[17px] data-[state=unchecked]:translate-x-0.5" />
    </SwitchPrimitive.Root>
  )
}

/* ───────── Tabs (kiểu segmented + kiểu underline) ───────── */
const Tabs = TabsPrimitive.Root
function TabsList({ className, variant = 'segmented', ...props }: React.ComponentProps<typeof TabsPrimitive.List> & { variant?: 'segmented' | 'underline' }) {
  return (
    <TabsPrimitive.List
      data-variant={variant}
      className={cn(
        'group/tabs inline-flex items-center',
        variant === 'segmented' ? 'gap-1 rounded-xl bg-muted p-1' : 'gap-5 border-b w-full',
        className,
      )}
      {...props}
    />
  )
}
function TabsTrigger({ className, ...props }: React.ComponentProps<typeof TabsPrimitive.Trigger>) {
  return (
    <TabsPrimitive.Trigger
      className={cn(
        'inline-flex cursor-pointer items-center gap-1.5 text-sm font-medium whitespace-nowrap text-muted-foreground outline-none transition focus-visible:ring-[3px] focus-visible:ring-ring disabled:opacity-50 [&_svg]:size-4',
        'group-data-[variant=segmented]/tabs:h-8 group-data-[variant=segmented]/tabs:rounded-lg group-data-[variant=segmented]/tabs:px-3 group-data-[variant=segmented]/tabs:data-[state=active]:bg-card group-data-[variant=segmented]/tabs:data-[state=active]:text-foreground group-data-[variant=segmented]/tabs:data-[state=active]:shadow-sm',
        'group-data-[variant=underline]/tabs:-mb-px group-data-[variant=underline]/tabs:border-b-2 group-data-[variant=underline]/tabs:border-transparent group-data-[variant=underline]/tabs:pb-2.5 group-data-[variant=underline]/tabs:pt-1 group-data-[variant=underline]/tabs:data-[state=active]:border-brand-500 group-data-[variant=underline]/tabs:data-[state=active]:text-foreground hover:text-foreground',
        className,
      )}
      {...props}
    />
  )
}
const TabsContent = TabsPrimitive.Content

/* ───────── Tooltip ───────── */
function Tooltip({ content, children, side = 'top' }: { content: React.ReactNode; children: React.ReactNode; side?: 'top' | 'right' | 'bottom' | 'left' }) {
  if (!content) return <>{children}</>
  return (
    <TooltipPrimitive.Root delayDuration={200}>
      <TooltipPrimitive.Trigger asChild>{children}</TooltipPrimitive.Trigger>
      <TooltipPrimitive.Portal>
        <TooltipPrimitive.Content
          side={side}
          sideOffset={6}
          className="z-50 max-w-xs rounded-lg bg-foreground px-2.5 py-1.5 text-xs text-background shadow-pop animate-in fade-in-0 zoom-in-95"
        >
          {content}
        </TooltipPrimitive.Content>
      </TooltipPrimitive.Portal>
    </TooltipPrimitive.Root>
  )
}
const TooltipProvider = TooltipPrimitive.Provider

/* ───────── Separator ───────── */
function Separator({ className, orientation = 'horizontal', ...props }: React.ComponentProps<typeof SeparatorPrimitive.Root>) {
  return (
    <SeparatorPrimitive.Root
      orientation={orientation}
      className={cn('shrink-0 bg-border', orientation === 'horizontal' ? 'h-px w-full' : 'h-full w-px', className)}
      {...props}
    />
  )
}

/* ───────── Popover ───────── */
const Popover = PopoverPrimitive.Root
const PopoverTrigger = PopoverPrimitive.Trigger
function PopoverContent({ className, align = 'end', sideOffset = 8, ...props }: React.ComponentProps<typeof PopoverPrimitive.Content>) {
  return (
    <PopoverPrimitive.Portal>
      <PopoverPrimitive.Content
        align={align}
        sideOffset={sideOffset}
        className={cn(
          'z-50 w-80 rounded-xl border bg-popover p-4 text-popover-foreground shadow-pop outline-none data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-95',
          className,
        )}
        {...props}
      />
    </PopoverPrimitive.Portal>
  )
}

/* ───────── RadioGroup (dạng thẻ lựa chọn) ───────── */
const RadioGroup = RadioPrimitive.Root
function RadioCard({
  value,
  title,
  description,
  icon,
  className,
}: {
  value: string
  title: React.ReactNode
  description?: React.ReactNode
  icon?: React.ReactNode
  className?: string
}) {
  return (
    <RadioPrimitive.Item
      value={value}
      className={cn(
        'group relative flex w-full cursor-pointer items-start gap-3 rounded-xl border bg-card p-4 text-left outline-none transition hover:border-brand-300 focus-visible:ring-[3px] focus-visible:ring-ring data-[state=checked]:border-brand-500 data-[state=checked]:bg-accent/60 data-[state=checked]:shadow-[0_0_0_1px_var(--brand-500)]',
        className,
      )}
    >
      {icon && (
        <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-muted text-muted-foreground transition group-data-[state=checked]:bg-brand-500 group-data-[state=checked]:text-white">
          {icon}
        </span>
      )}
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-semibold">{title}</span>
        {description && <span className="mt-1 block text-[13px] leading-snug text-muted-foreground">{description}</span>}
      </span>
      <span className="mt-0.5 grid size-4 shrink-0 place-items-center rounded-full border border-input group-data-[state=checked]:border-brand-500">
        <RadioPrimitive.Indicator className="size-2 rounded-full bg-brand-500" />
      </span>
    </RadioPrimitive.Item>
  )
}

/* ───────── Progress ───────── */
function Progress({ value, className, indicatorClassName }: { value: number; className?: string; indicatorClassName?: string }) {
  return (
    <ProgressPrimitive.Root value={value} className={cn('relative h-1.5 w-full overflow-hidden rounded-full bg-muted', className)}>
      <ProgressPrimitive.Indicator
        className={cn('h-full rounded-full bg-brand-500 transition-all', indicatorClassName)}
        style={{ width: `${Math.max(0, Math.min(100, value))}%` }}
      />
    </ProgressPrimitive.Root>
  )
}

/* ───────── Skeleton ───────── */
function Skeleton({ className, ...props }: React.ComponentProps<'div'>) {
  return <div className={cn('animate-pulse rounded-md bg-muted', className)} {...props} />
}

export {
  Checkbox,
  Switch,
  Tabs,
  TabsList,
  TabsTrigger,
  TabsContent,
  Tooltip,
  TooltipProvider,
  Separator,
  Popover,
  PopoverTrigger,
  PopoverContent,
  RadioGroup,
  RadioCard,
  Progress,
  Skeleton,
}
