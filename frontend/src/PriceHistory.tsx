import { useEffect, useMemo, useRef, useState } from 'react'
import type { PointerEvent as ReactPointerEvent } from 'react'
import { getHistory } from './api/clients'
import type { Location, MarketHistory } from './api/types'
import {
  isWorldLocation,
  locationScopeIds,
  WORLD_LOCATION_ID,
} from './locationScope'

const W = 880
const H = 340
const NAV_H = 84
const PAD = { top: 16, right: 56, bottom: 36, left: 64 }

const currency = new Intl.NumberFormat('en-GB', {
  style: 'currency',
  currency: 'GBP',
})

const volumeFmt = new Intl.NumberFormat('en-GB')

type Range = { start: number; end: number }
type DragMode = 'move' | 'left' | 'right'

function clamp(n: number, min: number, max: number) {
  return Math.min(max, Math.max(min, n))
}

function defaultRange(length: number): Range {
  if (length <= 0) return { start: 0, end: 0 }
  const end = length - 1
  const window = Math.min(30, length)
  return { start: end - window + 1, end }
}

function mergeHistoryByDate(
  typeId: number,
  books: MarketHistory[],
): MarketHistory[] {
  const byDate = new Map<string, MarketHistory[]>()
  for (const row of books) {
    const list = byDate.get(row.date) ?? []
    list.push(row)
    byDate.set(row.date, list)
  }

  return [...byDate.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, rows]) => {
      const volume = rows.reduce((sum, row) => sum + row.volume, 0)
      const orders = rows.reduce((sum, row) => sum + row.orders, 0)
      const lowPrice = Math.min(...rows.map((row) => row.lowPrice))
      const highPrice = Math.max(...rows.map((row) => row.highPrice))
      const avgPrice =
        volume > 0
          ? rows.reduce((sum, row) => sum + row.avgPrice * row.volume, 0) /
            volume
          : rows.reduce((sum, row) => sum + row.avgPrice, 0) / rows.length
      return {
        locationId: WORLD_LOCATION_ID,
        typeId,
        date,
        lowPrice,
        highPrice,
        avgPrice,
        volume,
        orders,
      }
    })
}

function PriceHistory({
  typeId,
  locationId,
  locations = [],
}: {
  typeId: number
  locationId: number
  locations?: Pick<Location, 'locationId' | 'parentLocationId'>[]
}) {
  const [rows, setRows] = useState<MarketHistory[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [hover, setHover] = useState<number | null>(null)
  const [range, setRange] = useState<Range>({ start: 0, end: 0 })
  const dragRef = useRef<{
    mode: DragMode
    originX: number
    origin: Range
  } | null>(null)
  const navSvgRef = useRef<SVGSVGElement | null>(null)

  const scopeIds = useMemo(
    () => locationScopeIds(locations, locationId),
    [locations, locationId],
  )
  const scopeKey = scopeIds.join(',')

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)

    const ids = isWorldLocation(locationId)
      ? scopeIds
      : [locationId]

    Promise.all(ids.map((id) => getHistory(typeId, id)))
      .then((books) => {
        if (cancelled) return
        const merged = isWorldLocation(locationId)
          ? mergeHistoryByDate(typeId, books.flat())
          : [...(books[0] ?? [])].sort((a, b) =>
              a.date.localeCompare(b.date),
            )
        setRows(merged)
        setRange(defaultRange(merged.length))
      })
      .catch(() => {
        if (!cancelled) setError('Could not load price history.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [typeId, locationId, scopeKey, scopeIds])

  const visible = useMemo(() => {
    if (!rows.length) return []
    const start = clamp(range.start, 0, rows.length - 1)
    const end = clamp(range.end, start, rows.length - 1)
    return rows.slice(start, end + 1)
  }, [rows, range])

  if (loading) return <p>Loading price history…</p>
  if (error) return <p role="alert">{error}</p>
  if (!rows.length) return <p>No price history available.</p>

  const innerW = W - PAD.left - PAD.right
  const innerH = H - PAD.top - PAD.bottom

  const min = Math.min(...visible.map((r) => r.lowPrice))
  const max = Math.max(...visible.map((r) => r.highPrice))
  const span = max - min || 1
  const yMin = min - span * 0.08
  const yMax = max + span * 0.08
  const maxVol = Math.max(...visible.map((r) => r.volume), 1)

  const xAt = (i: number, count: number) =>
    PAD.left + (count <= 1 ? innerW / 2 : (i / (count - 1)) * innerW)
  const yPrice = (v: number) =>
    PAD.top + (1 - (v - yMin) / (yMax - yMin)) * innerH
  // Bars: max volume reaches 1/4 plot height. Axis keeps that scale to the top
  // (so if max is 3 at 1/4, labels continue 6, 9, 12 at 1/2, 3/4, full).
  const volBand = innerH * 0.25
  const volAxisMax = maxVol * (innerH / volBand)
  const yVolBar = (v: number) =>
    PAD.top + innerH - (v / maxVol) * volBand
  const yVolAxis = (v: number) =>
    PAD.top + innerH - (v / volAxisMax) * innerH
  const volTicks = Array.from(
    { length: 5 },
    (_, i) => (volAxisMax * i) / 4,
  )

  const x = (i: number) => xAt(i, visible.length)
  const barW = Math.max(
    2,
    Math.min(12, (innerW / Math.max(visible.length, 1)) * 0.5),
  )

  const avgPath = visible
    .map((r, i) => `${i ? 'L' : 'M'}${x(i)},${yPrice(r.avgPrice)}`)
    .join(' ')
  const bandPath =
    visible
      .map((r, i) => `${i ? 'L' : 'M'}${x(i)},${yPrice(r.highPrice)}`)
      .join(' ') +
    ' ' +
    [...visible]
      .map((r, i) => `L${x(i)},${yPrice(r.lowPrice)}`)
      .reverse()
      .join(' ') +
    ' Z'

  const priceTicks = Array.from(
    { length: 5 },
    (_, i) => yMin + ((yMax - yMin) * i) / 4,
  )
  const active = hover === null ? null : visible[hover]
  const labelStep = Math.max(1, Math.ceil(visible.length / 7))
  const daySpan = visible.length

  // Navigator geometry
  const navPad = { top: 8, right: PAD.right, bottom: 18, left: PAD.left }
  const navInnerW = W - navPad.left - navPad.right
  const navInnerH = NAV_H - navPad.top - navPad.bottom
  const navMin = Math.min(...rows.map((r) => r.lowPrice))
  const navMax = Math.max(...rows.map((r) => r.highPrice))
  const navSpan = navMax - navMin || 1
  const navX = (i: number) =>
    navPad.left +
    (rows.length <= 1 ? navInnerW / 2 : (i / (rows.length - 1)) * navInnerW)
  const navY = (v: number) =>
    navPad.top + (1 - (v - navMin) / navSpan) * navInnerH
  const navAvg = rows
    .map((r, i) => `${i ? 'L' : 'M'}${navX(i)},${navY(r.avgPrice)}`)
    .join(' ')
  const selLeft = navX(range.start)
  const selRight = navX(range.end)
  const selWidth = Math.max(selRight - selLeft, 4)

  function indexFromClientX(clientX: number): number {
    const svg = navSvgRef.current
    if (!svg || rows.length <= 1) return 0
    const rect = svg.getBoundingClientRect()
    const scale = W / rect.width
    const localX = (clientX - rect.left) * scale
    const t = (localX - navPad.left) / navInnerW
    return clamp(Math.round(t * (rows.length - 1)), 0, rows.length - 1)
  }

  function beginDrag(mode: DragMode, clientX: number) {
    dragRef.current = {
      mode,
      originX: clientX,
      origin: { ...range },
    }
  }

  function onNavPointerDown(event: ReactPointerEvent<SVGSVGElement>) {
    const target = event.target as Element
    const handle = target.getAttribute('data-handle') as DragMode | null
    if (handle === 'left' || handle === 'right' || handle === 'move') {
      event.currentTarget.setPointerCapture(event.pointerId)
      beginDrag(handle, event.clientX)
      return
    }

    // Click on overview: centre window on click, keep width
    const idx = indexFromClientX(event.clientX)
    const width = Math.max(1, range.end - range.start + 1)
    let start = idx - Math.floor(width / 2)
    let end = start + width - 1
    if (start < 0) {
      start = 0
      end = width - 1
    }
    if (end > rows.length - 1) {
      end = rows.length - 1
      start = Math.max(0, end - width + 1)
    }
    setRange({ start, end })
    event.currentTarget.setPointerCapture(event.pointerId)
    beginDrag('move', event.clientX)
  }

  function onNavPointerMove(event: ReactPointerEvent<SVGSVGElement>) {
    const drag = dragRef.current
    if (!drag) return

    const originIdx = indexFromClientX(drag.originX)
    const nowIdx = indexFromClientX(event.clientX)
    const delta = nowIdx - originIdx
    const width = drag.origin.end - drag.origin.start

    if (drag.mode === 'move') {
      let start = drag.origin.start + delta
      let end = drag.origin.end + delta
      if (start < 0) {
        start = 0
        end = width
      }
      if (end > rows.length - 1) {
        end = rows.length - 1
        start = end - width
      }
      setRange({ start, end })
      return
    }

    if (drag.mode === 'left') {
      const start = clamp(drag.origin.start + delta, 0, drag.origin.end)
      setRange({ start, end: drag.origin.end })
      return
    }

    const end = clamp(drag.origin.end + delta, drag.origin.start, rows.length - 1)
    setRange({ start: drag.origin.start, end })
  }

  function onNavPointerUp(event: ReactPointerEvent<SVGSVGElement>) {
    dragRef.current = null
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId)
    }
  }

  return (
    <figure className="price-chart">
      <div className="price-chart-toolbar">
        <ul className="chart-legend" aria-label="Chart legend">
          <li>
            <span className="chart-legend-swatch chart-legend-donchian" />
            Donchian channel
          </li>
          <li>
            <span className="chart-legend-swatch chart-legend-avg" />
            Average
          </li>
          <li>
            <span className="chart-legend-swatch chart-legend-vol" />
            Volume
          </li>
        </ul>
        <p className="chart-window-label">
          {daySpan} day{daySpan === 1 ? '' : 's'} shown
        </p>
      </div>

      <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Price history chart">
        {priceTicks.map((t) => (
          <g key={`p-${t}`}>
            <line
              x1={PAD.left}
              x2={W - PAD.right}
              y1={yPrice(t)}
              y2={yPrice(t)}
              className="chart-grid"
            />
            <text
              x={PAD.left - 8}
              y={yPrice(t) + 4}
              textAnchor="end"
              className="chart-label"
            >
              {currency.format(t)}
            </text>
          </g>
        ))}

        {volTicks.map((t) => (
          <text
            key={`v-${t}`}
            x={W - PAD.right + 8}
            y={yVolAxis(t) + 4}
            textAnchor="start"
            className="chart-label chart-label-volume"
          >
            {volumeFmt.format(t)}
          </text>
        ))}

        {visible.map((r, i) => {
          const top = yVolBar(r.volume)
          const base = PAD.top + innerH
          return (
            <rect
              key={`vol-${r.date}`}
              x={x(i) - barW / 2}
              y={top}
              width={barW}
              height={Math.max(0, base - top)}
              className={
                hover === i ? 'chart-volume is-active' : 'chart-volume'
              }
              onMouseEnter={() => setHover(i)}
              onMouseLeave={() => setHover(null)}
            >
              <title>
                {r.date}: {volumeFmt.format(r.volume)} sold
              </title>
            </rect>
          )
        })}

        <path d={bandPath} className="chart-band" />
        <path d={avgPath} className="chart-line" />

        {visible.map((r, i) => (
          <circle
            key={`dot-${r.date}`}
            cx={x(i)}
            cy={yPrice(r.avgPrice)}
            r={hover === i ? 6 : 4}
            className="chart-dot"
            onMouseEnter={() => setHover(i)}
            onMouseLeave={() => setHover(null)}
          >
            <title>
              {r.date}: {volumeFmt.format(r.volume)} sold
            </title>
          </circle>
        ))}

        {visible.map((r, i) =>
          i % labelStep === 0 || i === visible.length - 1 ? (
            <text
              key={`d-${r.date}`}
              x={x(i)}
              y={H - 12}
              textAnchor="middle"
              className="chart-label"
            >
              {new Date(r.date).toLocaleDateString('en-GB', {
                day: 'numeric',
                month: 'short',
              })}
            </text>
          ) : null,
        )}
      </svg>

      <svg
        ref={navSvgRef}
        className="chart-navigator"
        viewBox={`0 0 ${W} ${NAV_H}`}
        role="slider"
        aria-label="Select visible history range"
        aria-valuemin={0}
        aria-valuemax={rows.length - 1}
        aria-valuetext={`${rows[range.start]?.date ?? ''} to ${rows[range.end]?.date ?? ''}`}
        onPointerDown={onNavPointerDown}
        onPointerMove={onNavPointerMove}
        onPointerUp={onNavPointerUp}
        onPointerCancel={onNavPointerUp}
      >
        <rect
          x={navPad.left}
          y={navPad.top}
          width={navInnerW}
          height={navInnerH}
          className="chart-nav-track"
        />
        <path d={navAvg} className="chart-nav-line" />

        {/* Dim outside selection */}
        <rect
          x={navPad.left}
          y={navPad.top}
          width={Math.max(0, selLeft - navPad.left)}
          height={navInnerH}
          className="chart-nav-mask"
        />
        <rect
          x={selRight}
          y={navPad.top}
          width={Math.max(0, navPad.left + navInnerW - selRight)}
          height={navInnerH}
          className="chart-nav-mask"
        />

        <rect
          x={selLeft}
          y={navPad.top}
          width={selWidth}
          height={navInnerH}
          className="chart-nav-window"
          data-handle="move"
        />
        <rect
          x={selLeft - 3}
          y={navPad.top}
          width={6}
          height={navInnerH}
          className="chart-nav-handle"
          data-handle="left"
        />
        <rect
          x={selRight - 3}
          y={navPad.top}
          width={6}
          height={navInnerH}
          className="chart-nav-handle"
          data-handle="right"
        />

        <text
          x={navPad.left}
          y={NAV_H - 2}
          textAnchor="start"
          className="chart-label chart-nav-date"
        >
          {new Date(rows[0].date).toLocaleDateString('en-GB', {
            day: 'numeric',
            month: 'short',
            year: '2-digit',
          })}
        </text>
        <text
          x={navPad.left + navInnerW}
          y={NAV_H - 2}
          textAnchor="end"
          className="chart-label chart-nav-date"
        >
          {new Date(rows[rows.length - 1].date).toLocaleDateString('en-GB', {
            day: 'numeric',
            month: 'short',
            year: '2-digit',
          })}
        </text>
        <text
          x={(selLeft + selRight) / 2}
          y={navPad.top + 11}
          textAnchor="middle"
          className="chart-label chart-nav-range"
          style={{ pointerEvents: 'none' }}
        >
          {new Date(rows[range.start].date).toLocaleDateString('en-GB', {
            day: 'numeric',
            month: 'short',
          })}
          {' – '}
          {new Date(rows[range.end].date).toLocaleDateString('en-GB', {
            day: 'numeric',
            month: 'short',
          })}
        </text>
      </svg>

      {active ? (
        <p className="chart-tooltip">
          {active.date}: avg {currency.format(active.avgPrice)} (low{' '}
          {currency.format(active.lowPrice)}, high{' '}
          {currency.format(active.highPrice)}) ·{' '}
          <strong>{volumeFmt.format(active.volume)} sold</strong>
        </p>
      ) : null}
    </figure>
  )
}

export default PriceHistory
