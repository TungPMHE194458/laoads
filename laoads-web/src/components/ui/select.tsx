import * as React from 'react'
import { Select as SelectPrimitive } from 'radix-ui'
import { CheckIcon, ChevronDownIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

export interface Option {
  value: string
  label: React.ReactNode
  disabled?: boolean
}

/**
 * Select gọn: truyền `options`. Giá trị rỗng ('') được map sang sentinel '__all__'
 * vì Radix Select không cho phép item value rỗng.
 */
function Select({
  value,
  onValueChange,
  options,
  placeholder,
  className,
  disabled,
  allLabel,
  size = 'default',
  'aria-label': ariaLabel,
}: {
  value: string
  onValueChange: (v: string) => void
  options: Option[]
  placeholder?: string
  className?: string
  disabled?: boolean
  /** Thêm option "Tất cả" (value '') ở đầu danh sách. */
  allLabel?: string
  size?: 'default' | 'sm'
  'aria-label'?: string
}) {
  const ALL = '__all__'
  const opts = allLabel ? [{ value: ALL, label: allLabel }, ...options] : options
  return (
    <SelectPrimitive.Root
      value={value === '' && allLabel ? ALL : value || undefined}
      onValueChange={(v) => onValueChange(v === ALL ? '' : v)}
      disabled={disabled}
    >
      <SelectPrimitive.Trigger
        aria-label={ariaLabel}
        className={cn(
          'flex w-full items-center justify-between gap-2 rounded-lg border border-input bg-card px-3 text-sm whitespace-nowrap shadow-xs outline-none transition focus-visible:border-brand-500 focus-visible:ring-[3px] focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 data-[placeholder]:text-muted-foreground cursor-pointer [&>span]:truncate',
          size === 'sm' ? 'h-8 text-[13px]' : 'h-9',
          className,
        )}
      >
        <SelectPrimitive.Value placeholder={placeholder} />
        <SelectPrimitive.Icon asChild>
          <ChevronDownIcon className="size-4 opacity-60" />
        </SelectPrimitive.Icon>
      </SelectPrimitive.Trigger>
      <SelectPrimitive.Portal>
        <SelectPrimitive.Content
          position="popper"
          sideOffset={6}
          className="z-50 max-h-72 min-w-[var(--radix-select-trigger-width)] overflow-hidden rounded-xl border bg-popover text-popover-foreground shadow-pop data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-95"
        >
          <SelectPrimitive.Viewport className="p-1">
            {opts.map((o) => (
              <SelectPrimitive.Item
                key={o.value}
                value={o.value}
                disabled={o.disabled}
                className="relative flex cursor-pointer items-center rounded-lg py-2 pr-8 pl-2.5 text-sm outline-none select-none focus:bg-muted data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
              >
                <SelectPrimitive.ItemText>{o.label}</SelectPrimitive.ItemText>
                <SelectPrimitive.ItemIndicator className="absolute right-2.5">
                  <CheckIcon className="size-4 text-brand-600" />
                </SelectPrimitive.ItemIndicator>
              </SelectPrimitive.Item>
            ))}
          </SelectPrimitive.Viewport>
        </SelectPrimitive.Content>
      </SelectPrimitive.Portal>
    </SelectPrimitive.Root>
  )
}

export { Select }
