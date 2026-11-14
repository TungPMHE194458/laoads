import * as React from 'react'
import { cn } from '@/lib/utils'

export const inputClass =
  'flex h-9 w-full min-w-0 rounded-lg border border-input bg-card px-3 py-1 text-sm shadow-xs transition-[color,box-shadow,border] outline-none placeholder:text-muted-foreground/80 selection:bg-brand-200 file:inline-flex file:h-7 file:border-0 file:bg-transparent file:text-sm file:font-medium focus-visible:border-brand-500 focus-visible:ring-[3px] focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-destructive/20'

function Input({ className, type, ...props }: React.ComponentProps<'input'>) {
  return <input type={type} data-slot="input" className={cn(inputClass, className)} {...props} />
}

function Textarea({ className, ...props }: React.ComponentProps<'textarea'>) {
  return <textarea data-slot="textarea" className={cn(inputClass, 'min-h-20 py-2 h-auto', className)} {...props} />
}

export { Input, Textarea }
