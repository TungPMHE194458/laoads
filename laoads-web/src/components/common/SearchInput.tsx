import * as React from 'react'
import { Search, X } from 'lucide-react'
import { cn } from '@/lib/utils'
import { inputClass } from '@/components/ui/input'

/** Ô tìm kiếm có debounce (mặc định 350ms). */
export function SearchInput({
  value,
  onChange,
  placeholder,
  className,
  delay = 350,
}: {
  value: string
  onChange: (v: string) => void
  placeholder?: string
  className?: string
  delay?: number
}) {
  const [local, setLocal] = React.useState(value)
  // Đồng bộ khi value từ ngoài đổi (vd reset filter) — điều chỉnh state trong render, không dùng effect
  const [prev, setPrev] = React.useState(value)
  if (value !== prev) {
    setPrev(value)
    setLocal(value)
  }
  React.useEffect(() => {
    if (local === value) return
    const id = setTimeout(() => onChange(local), delay)
    return () => clearTimeout(id)
  }, [local, value, delay, onChange])
  return (
    <div className={cn('relative w-full sm:w-72', className)}>
      <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
      <input
        type="search"
        value={local}
        onChange={(e) => setLocal(e.target.value)}
        placeholder={placeholder}
        className={cn(inputClass, 'pl-9 pr-8 [&::-webkit-search-cancel-button]:hidden')}
      />
      {local && (
        <button
          type="button"
          aria-label="clear"
          onClick={() => {
            setLocal('')
            onChange('')
          }}
          className="absolute top-1/2 right-2 grid size-5 -translate-y-1/2 cursor-pointer place-items-center rounded text-muted-foreground hover:bg-muted"
        >
          <X className="size-3.5" />
        </button>
      )}
    </div>
  )
}
