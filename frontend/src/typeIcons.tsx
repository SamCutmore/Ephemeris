/** Local type pictograms — stand-in until inv_types carries icon refs. */

const TYPE_KIND: Record<number, string> = {
  1001: 'phone',
  1008: 'phone',
  2001: 'exhaust',
  2002: 'filter',
  2003: 'brake',
  3001: 'book',
  3002: 'book',
  4001: 'vinyl',
  4002: 'card',
  5001: 'wrench',
  5002: 'nut',
}

function kindFor(typeId: number, marketGroupId?: number | null): string {
  if (TYPE_KIND[typeId]) return TYPE_KIND[typeId]
  if (marketGroupId == null) return 'generic'
  if (marketGroupId >= 10 && marketGroupId < 20) return 'phone'
  if (marketGroupId >= 20 && marketGroupId < 30) return 'brake'
  if (marketGroupId >= 30 && marketGroupId < 40) return 'book'
  if (marketGroupId === 41) return 'vinyl'
  if (marketGroupId === 42) return 'card'
  if (marketGroupId >= 50 && marketGroupId < 60) return 'wrench'
  return 'generic'
}

function Glyph({ kind }: { kind: string }) {
  switch (kind) {
    case 'phone':
      return (
        <g fill="none" stroke="currentColor" strokeWidth="1.6">
          <rect x="8" y="3" width="16" height="26" rx="3" />
          <circle cx="16" cy="24.5" r="1.2" fill="currentColor" stroke="none" />
        </g>
      )
    case 'book':
      return (
        <g fill="none" stroke="currentColor" strokeWidth="1.6">
          <path d="M6 5h9a4 4 0 0 1 4 4v16H10a4 4 0 0 0-4 4V5z" />
          <path d="M19 5h7v20h-7" />
          <path d="M10 11h6M10 15h6" />
        </g>
      )
    case 'vinyl':
      return (
        <g fill="none" stroke="currentColor" strokeWidth="1.6">
          <circle cx="16" cy="16" r="12" />
          <circle cx="16" cy="16" r="3.5" />
          <circle cx="16" cy="16" r="1" fill="currentColor" stroke="none" />
        </g>
      )
    case 'card':
      return (
        <g fill="none" stroke="currentColor" strokeWidth="1.6">
          <rect x="5" y="7" width="22" height="18" rx="2" />
          <rect x="8" y="10" width="10" height="8" rx="1" />
          <path d="M20 12h4M20 16h4" />
        </g>
      )
    case 'wrench':
      return (
        <g fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round">
          <path d="M20 6a5 5 0 0 0-7 7L5 21l3 3 8-8a5 5 0 0 0 4-10z" />
          <circle cx="21" cy="7" r="1.2" fill="currentColor" stroke="none" />
        </g>
      )
    case 'nut':
      return (
        <g fill="none" stroke="currentColor" strokeWidth="1.6">
          <path d="M16 4l9 5v10l-9 5-9-5V9z" />
          <circle cx="16" cy="16" r="3.5" />
        </g>
      )
    case 'exhaust':
      return (
        <g fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round">
          <path d="M4 18c4-8 8-10 12-10s8 2 12 10" />
          <path d="M8 18h16" />
          <circle cx="10" cy="20" r="1.5" />
          <circle cx="22" cy="20" r="1.5" />
        </g>
      )
    case 'filter':
      return (
        <g fill="none" stroke="currentColor" strokeWidth="1.6">
          <path d="M10 4h12v6H10z" />
          <path d="M12 10v14M20 10v14" />
          <path d="M10 24h12" />
        </g>
      )
    case 'brake':
      return (
        <g fill="none" stroke="currentColor" strokeWidth="1.6">
          <circle cx="16" cy="16" r="11" />
          <circle cx="16" cy="16" r="4" />
          <path d="M16 5v4M16 23v4M5 16h4M23 16h4" />
        </g>
      )
    default:
      return (
        <g fill="none" stroke="currentColor" strokeWidth="1.6">
          <rect x="6" y="6" width="20" height="20" rx="3" />
          <path d="M11 16h10M16 11v10" />
        </g>
      )
  }
}

export function TypeIcon({
  typeId,
  marketGroupId,
  typeName,
  size = 40,
  className = 'type-icon',
}: {
  typeId: number
  marketGroupId?: number | null
  typeName?: string
  size?: number
  className?: string
}) {
  const kind = kindFor(typeId, marketGroupId)
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 32 32"
      role="img"
      aria-label={typeName ? `Icon for ${typeName}` : 'Item icon'}
    >
      <rect
        x="1"
        y="1"
        width="30"
        height="30"
        rx="6"
        className="type-icon-plate"
      />
      <Glyph kind={kind} />
    </svg>
  )
}
