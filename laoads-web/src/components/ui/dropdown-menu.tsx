import * as React from 'react'
import { DropdownMenu as DM } from 'radix-ui'
import { CheckIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

const DropdownMenu = DM.Root
const DropdownMenuTrigger = DM.Trigger
const DropdownMenuGroup = DM.Group

function DropdownMenuContent({ className, sideOffset = 6, ...props }: React.ComponentProps<typeof DM.Content>) {
  return (
    <DM.Portal>
      <DM.Content
        sideOffset={sideOffset}
        className={cn(
          'z-50 min-w-44 overflow-hidden rounded-xl border bg-popover p-1 text-popover-foreground shadow-pop',
          'data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95',
          className,
        )}
        {...props}
      />
    </DM.Portal>
  )
}

function DropdownMenuItem({
  className,
  variant = 'default',
  ...props
}: React.ComponentProps<typeof DM.Item> & { variant?: 'default' | 'destructive' }) {
  return (
    <DM.Item
      data-variant={variant}
      className={cn(
        "relative flex cursor-pointer items-center gap-2 rounded-lg px-2.5 py-2 text-sm outline-none select-none focus:bg-muted data-[disabled]:pointer-events-none data-[disabled]:opacity-50 data-[variant=destructive]:text-destructive data-[variant=destructive]:focus:bg-danger-soft [&_svg:not([class*='size-'])]:size-4 [&_svg]:text-muted-foreground data-[variant=destructive]:[&_svg]:text-destructive",
        className,
      )}
      {...props}
    />
  )
}

function DropdownMenuCheckItem({ className, children, checked, ...props }: React.ComponentProps<typeof DM.Item> & { checked?: boolean }) {
  return (
    <DropdownMenuItem className={cn('pr-8', className)} {...props}>
      {children}
      {checked && <CheckIcon className="absolute right-2 size-4 text-brand-600!" />}
    </DropdownMenuItem>
  )
}

function DropdownMenuLabel({ className, ...props }: React.ComponentProps<typeof DM.Label>) {
  return <DM.Label className={cn('px-2.5 py-1.5 text-xs font-medium text-muted-foreground', className)} {...props} />
}

function DropdownMenuSeparator({ className, ...props }: React.ComponentProps<typeof DM.Separator>) {
  return <DM.Separator className={cn('-mx-1 my-1 h-px bg-border', className)} {...props} />
}

export {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuGroup,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuCheckItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
}
