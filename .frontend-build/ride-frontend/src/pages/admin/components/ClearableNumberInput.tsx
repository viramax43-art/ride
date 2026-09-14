import { useEffect, useState } from 'react'

type ClearableNumberInputProps = Omit<React.InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange'> & {
  value: number
  onValueChange: (value: number) => void
}

export default function ClearableNumberInput({
  value,
  onValueChange,
  min,
  ...props
}: ClearableNumberInputProps) {
  const [draft, setDraft] = useState(() => String(value))

  useEffect(() => {
    setDraft(String(value))
  }, [value])

  return (
    <input
      {...props}
      type="number"
      min={min}
      value={draft}
      onChange={(event) => {
        const next = event.target.value
        setDraft(next)
        if (!next.trim()) return

        const parsed = Number(next)
        const numericMin = typeof min === 'number' ? min : min === undefined ? undefined : Number(min)
        if (Number.isFinite(parsed) && (numericMin === undefined || parsed >= numericMin)) {
          onValueChange(parsed)
        }
      }}
    />
  )
}
