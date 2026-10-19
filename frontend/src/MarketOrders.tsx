import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import type { MouseEvent as ReactMouseEvent, ReactNode } from 'react'
import { getOrderBook } from './api/clients'
import type { Location, MarketOrder } from './api/types'
import type { BuyOrderDefaults } from './BuyOrderDialog'
import CancelBuyOrderDialog from './CancelBuyOrderDialog'
import { clampFixedMenu } from './clampFixedMenu'
import EditBuyOrderDialog from './EditBuyOrderDialog'
import { locationScopeIds } from './locationScope'

const sources: Record<number, string> = {
  1: 'eBay',
  2: 'Vinted',
  3: 'Gumtree',
  4: 'Facebook Marketplace',
  5: 'Amazon',
  6: 'Market Maker',
}

const currency = new Intl.NumberFormat('en-GB', {
  style: 'currency',
  currency: 'GBP',
})

const statusLabels: Record<MarketOrder['status'], string> = {
  open: 'Open',
  partial: 'Partial',
  filled: 'Filled',
  cancelled: 'Cancelled',
}

type SortKey =
  | 'price'
  | 'quantity'
  | 'username'
  | 'location'
  | 'source'
  | 'status'
  | 'updatedAt'

type SortDir = 'asc' | 'desc'

type ContextMenuState = {
  x: number
  y: number
  offer: MarketOrder
  kind: 'seller' | 'owned-buy'
}

function isOwnedEditableBuy(
  offer: MarketOrder,
  userId: number | null,
): boolean {
  return (
    userId != null &&
    offer.side === 'buy' &&
    offer.ownerKind === 'user' &&
    offer.userId === userId &&
    (offer.status === 'open' || offer.status === 'partial')
  )
}

const SELL_COLUMNS: { key: SortKey; label: string }[] = [
  { key: 'price', label: 'Price' },
  { key: 'quantity', label: 'Qty' },
  { key: 'location', label: 'Location' },
  { key: 'source', label: 'Source' },
  { key: 'status', label: 'Status' },
  { key: 'updatedAt', label: 'Updated' },
]

const BUY_COLUMNS: { key: SortKey; label: string }[] = [
  { key: 'price', label: 'Price' },
  { key: 'quantity', label: 'Qty' },
  { key: 'username', label: 'Buyer' },
  { key: 'location', label: 'Location' },
  { key: 'source', label: 'Source' },
  { key: 'status', label: 'Status' },
  { key: 'updatedAt', label: 'Updated' },
]

function mergeOrders(books: MarketOrder[][]): MarketOrder[] {
  const byId = new Map<number, MarketOrder>()
  for (const rows of books) {
    for (const row of rows) {
      byId.set(row.orderId, row)
    }
  }
  return [...byId.values()]
}

function compareOffers(
  a: MarketOrder,
  b: MarketOrder,
  key: SortKey,
  dir: SortDir,
  locationNames: Record<number, string>,
): number {
  const sign = dir === 'asc' ? 1 : -1
  let cmp = 0

  switch (key) {
    case 'price':
      cmp = a.price - b.price
      break
    case 'quantity':
      cmp = a.quantity - b.quantity
      break
    case 'username': {
      const ua = a.username ?? a.ownerRef ?? ''
      const ub = b.username ?? b.ownerRef ?? ''
      cmp = ua.localeCompare(ub, 'en-GB')
      break
    }
    case 'location': {
      const la =
        locationNames[a.locationId] ?? `Location ${a.locationId}`
      const lb =
        locationNames[b.locationId] ?? `Location ${b.locationId}`
      cmp = la.localeCompare(lb, 'en-GB')
      break
    }
    case 'source': {
      const sa = sources[a.sourceId] ?? 'Other'
      const sb = sources[b.sourceId] ?? 'Other'
      cmp = sa.localeCompare(sb, 'en-GB')
      break
    }
    case 'status':
      cmp = statusLabels[a.status].localeCompare(
        statusLabels[b.status],
        'en-GB',
      )
      break
    case 'updatedAt':
      cmp =
        new Date(a.updatedAt).getTime() - new Date(b.updatedAt).getTime()
      break
  }

  if (cmp !== 0) return cmp * sign
  return (a.orderId - b.orderId) * sign
}

export default function MarketOrders({
  typeId,
  typeName,
  locationId,
  locations,
  locationNames,
  canBuy,
  pinned,
  userId = null,
  userName = null,
  token = null,
  refreshToken = 0,
  highlightBuyOrderId = null,
  onBuyFromAsk,
  onNeedLogin,
  onHighlightBuyConsumed,
  onPlaceBuy,
  onTogglePin,
  onExpired,
  onOrdersChanged,
  onNotice,
}: {
  typeId: number
  typeName: string
  locationId: number
  locations: Pick<Location, 'locationId' | 'parentLocationId'>[]
  locationNames: Record<number, string>
  canBuy: boolean
  pinned: boolean
  userId?: number | null
  userName?: string | null
  token?: string | null
  /** Bump to reload the book without remounting the whole page. */
  refreshToken?: number
  /** After placeBuyOrder — flash this row if it appears in the buy book. */
  highlightBuyOrderId?: number | null
  onBuyFromAsk: (
    defaults: BuyOrderDefaults,
    offerLocationId: number,
  ) => void
  onNeedLogin: () => void
  onHighlightBuyConsumed?: () => void
  onPlaceBuy: () => void
  onTogglePin: () => void
  onExpired: () => void
  onOrdersChanged: () => void
  onNotice: (message: string) => void
}) {
  const [sells, setSells] = useState<MarketOrder[]>([])
  const [buys, setBuys] = useState<MarketOrder[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [loadedKey, setLoadedKey] = useState<string | null>(null)
  const [menu, setMenu] = useState<ContextMenuState | null>(null)
  const menuRef = useRef<HTMLUListElement | null>(null)
  const [editing, setEditing] = useState<MarketOrder | null>(null)
  const [cancelling, setCancelling] = useState<MarketOrder | null>(null)

  const scopeIds = useMemo(
    () => locationScopeIds(locations, locationId),
    [locations, locationId],
  )
  const scopeKey = scopeIds.join(',')
  const requestKey = `${typeId}:${scopeKey}:${refreshToken}`

  useEffect(() => {
    let cancelled = false
    const key = requestKey
    const ids = scopeIds

    setLoading(true)
    setError(null)
    setSells([])
    setBuys([])
    setLoadedKey(null)
    setMenu(null)

    Promise.all(ids.map((id) => getOrderBook(typeId, id)))
      .then((books) => {
        if (cancelled) return
        setSells(mergeOrders(books.map((b) => b.sells)))
        setBuys(mergeOrders(books.map((b) => b.buys)))
        setLoadedKey(key)
      })
      .catch(() => {
        if (!cancelled) setError('Could not load market data.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [typeId, locationId, refreshToken, requestKey, scopeIds])

  useLayoutEffect(() => {
    if (!menu || !menuRef.current) return
    clampFixedMenu(menuRef.current, menu.x, menu.y)
  }, [menu])

  useEffect(() => {
    if (!menu) return

    function close() {
      setMenu(null)
    }

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') close()
    }

    document.addEventListener('mousedown', close)
    document.addEventListener('keydown', onKeyDown)
    document.addEventListener('scroll', close, true)
    return () => {
      document.removeEventListener('mousedown', close)
      document.removeEventListener('keydown', onKeyDown)
      document.removeEventListener('scroll', close, true)
    }
  }, [menu])

  const ready = !loading && !error && loadedKey === requestKey

  useEffect(() => {
    if (!ready || highlightBuyOrderId == null) return
    const present = buys.some((offer) => offer.orderId === highlightBuyOrderId)
    if (!present) {
      onHighlightBuyConsumed?.()
    }
  }, [ready, buys, highlightBuyOrderId, onHighlightBuyConsumed])

  function openSellerMenu(
    event: ReactMouseEvent,
    offer: MarketOrder,
  ) {
    event.preventDefault()
    setMenu({ x: event.clientX, y: event.clientY, offer, kind: 'seller' })
  }

  function openBuyMenu(event: ReactMouseEvent, offer: MarketOrder) {
    if (!isOwnedEditableBuy(offer, userId ?? null)) return
    event.preventDefault()
    setMenu({ x: event.clientX, y: event.clientY, offer, kind: 'owned-buy' })
  }

  function listingUrl(offer: MarketOrder): string | null {
    if (offer.externalUrl && /^https?:\/\//i.test(offer.externalUrl)) {
      return offer.externalUrl
    }
    return null
  }

  return (
    <div className="market-data">
      {loading ? <p>Loading market data…</p> : null}
      {error ? <p role="alert">{error}</p> : null}

      {ready ? (
        <div className="market-books">
          <OrderTable
            heading="Sellers"
            empty="No sell orders."
            offers={sells}
            locationNames={locationNames}
            side="sell"
            defaultSort={{ key: 'price', dir: 'asc' }}
            onRowContextMenu={openSellerMenu}
          />

          <OrderTable
            heading="Buyers"
            empty="No buy orders."
            offers={buys}
            locationNames={locationNames}
            side="buy"
            defaultSort={{ key: 'price', dir: 'desc' }}
            highlightOrderId={highlightBuyOrderId}
            onHighlightEnd={onHighlightBuyConsumed}
            ownedUserId={userId}
            ownedUserName={userName}
            onRowContextMenu={openBuyMenu}
            headingActions={
              <div className="market-heading-actions">
                <button type="button" onClick={onPlaceBuy}>
                  Place buy order
                </button>
                <button type="button" onClick={onTogglePin}>
                  {pinned ? 'Unpin quickbar' : 'Add to quickbar'}
                </button>
              </div>
            }
          />
        </div>
      ) : null}

      {menu ? (
        <ul
          ref={menuRef}
          className="context-menu"
          style={{ top: menu.y, left: menu.x }}
          role="menu"
          onMouseDown={(event) => event.stopPropagation()}
        >
          {menu.kind === 'seller' ? (
            <>
              <li role="none">
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => {
                    const offer = menu.offer
                    setMenu(null)
                    if (!canBuy) {
                      onNeedLogin()
                      return
                    }
                    onBuyFromAsk(
                      {
                        price: String(offer.price),
                        quantity: String(offer.quantity),
                      },
                      offer.locationId,
                    )
                  }}
                >
                  Create buy order…
                </button>
              </li>
              <li role="none">
                <button
                  type="button"
                  role="menuitem"
                  disabled={!listingUrl(menu.offer)}
                  onClick={() => {
                    const url = listingUrl(menu.offer)
                    setMenu(null)
                    if (url) {
                      window.open(url, '_blank', 'noopener,noreferrer')
                    }
                  }}
                >
                  Open listing
                </button>
              </li>
            </>
          ) : (
            <>
              <li role="none">
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => {
                    const offer = menu.offer
                    setMenu(null)
                    setEditing(offer)
                  }}
                >
                  Edit order…
                </button>
              </li>
              <li role="none">
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => {
                    const offer = menu.offer
                    setMenu(null)
                    setCancelling(offer)
                  }}
                >
                  Cancel order
                </button>
              </li>
            </>
          )}
        </ul>
      ) : null}

      {editing && token ? (
        <EditBuyOrderDialog
          order={editing}
          typeName={typeName}
          locationName={
            locationNames[editing.locationId] ??
            `Location ${editing.locationId}`
          }
          token={token}
          onClose={() => setEditing(null)}
          onExpired={onExpired}
          onSaved={(result) => {
            setEditing(null)
            onNotice(`Order #${result.orderId} updated.`)
            onOrdersChanged()
          }}
        />
      ) : null}

      {cancelling && token ? (
        <CancelBuyOrderDialog
          orderId={cancelling.orderId}
          typeName={typeName}
          locationName={
            locationNames[cancelling.locationId] ??
            `Location ${cancelling.locationId}`
          }
          price={cancelling.price}
          quantity={cancelling.quantity}
          token={token}
          onClose={() => setCancelling(null)}
          onExpired={onExpired}
          onCancelled={() => {
            onNotice(`Order #${cancelling.orderId} cancelled.`)
            setCancelling(null)
            onOrdersChanged()
          }}
        />
      ) : null}
    </div>
  )
}

function OrderTable({
  heading,
  empty,
  offers,
  locationNames,
  side,
  defaultSort,
  highlightOrderId = null,
  ownedUserId = null,
  ownedUserName = null,
  onRowContextMenu,
  onHighlightEnd,
  headingActions,
}: {
  heading: string
  empty: string
  offers: MarketOrder[]
  locationNames: Record<number, string>
  side: 'buy' | 'sell'
  defaultSort: { key: SortKey; dir: SortDir }
  highlightOrderId?: number | null
  ownedUserId?: number | null
  ownedUserName?: string | null
  onRowContextMenu?: (
    event: ReactMouseEvent,
    offer: MarketOrder,
  ) => void
  onHighlightEnd?: () => void
  headingActions?: ReactNode
}) {
  const [sortKey, setSortKey] = useState<SortKey>(defaultSort.key)
  const [sortDir, setSortDir] = useState<SortDir>(defaultSort.dir)
  const highlightRef = useRef<HTMLTableRowElement | null>(null)

  const sorted = useMemo(
    () =>
      [...offers].sort((a, b) =>
        compareOffers(a, b, sortKey, sortDir, locationNames),
      ),
    [offers, sortKey, sortDir, locationNames],
  )

  useEffect(() => {
    if (highlightOrderId == null) return
    highlightRef.current?.scrollIntoView({
      block: 'nearest',
      behavior: 'smooth',
    })
  }, [highlightOrderId, sorted])

  const columns = side === 'buy' ? BUY_COLUMNS : SELL_COLUMNS

  function toggleSort(key: SortKey) {
    if (key === sortKey) {
      setSortDir((current) => (current === 'asc' ? 'desc' : 'asc'))
      return
    }
    setSortKey(key)
    setSortDir(key === 'price' && side === 'buy' ? 'desc' : 'asc')
  }

  return (
    <section className="market-data-section">
      <div className="market-section-heading">
        <h3>{heading}</h3>
        {headingActions}
      </div>
      {!offers.length ? (
        <p>{empty}</p>
      ) : (
        <div className="market-table-scroll">
          <table className="market-table">
            <thead>
              <tr>
                {columns.map((col) => {
                  const active = sortKey === col.key
                  return (
                    <th key={col.key} scope="col">
                      <button
                        type="button"
                        className={
                          active
                            ? 'market-sort-btn is-active'
                            : 'market-sort-btn'
                        }
                        onClick={() => toggleSort(col.key)}
                        aria-sort={
                          active
                            ? sortDir === 'asc'
                              ? 'ascending'
                              : 'descending'
                            : 'none'
                        }
                      >
                        <span>{col.label}</span>
                        <span className="market-sort-ind" aria-hidden="true">
                          {active ? (sortDir === 'asc' ? '▲' : '▼') : '◇'}
                        </span>
                      </button>
                    </th>
                  )
                })}
              </tr>
            </thead>
            <tbody>
              {sorted.map((offer) => {
                const flashing =
                  highlightOrderId != null &&
                  offer.orderId === highlightOrderId
                const owned =
                  side === 'buy' &&
                  isOwnedEditableBuy(offer, ownedUserId)
                const interactive =
                  side === 'sell' || owned
                const buyerName =
                  offer.username ??
                  offer.ownerRef ??
                  (owned ? ownedUserName : null)
                const classes = [
                  interactive ? 'market-row-interactive' : '',
                  owned ? 'market-row-owned' : '',
                  flashing ? 'row-flash' : '',
                ]
                  .filter(Boolean)
                  .join(' ')

                return (
                  <tr
                    key={offer.orderId}
                    ref={flashing ? highlightRef : undefined}
                    className={classes || undefined}
                    onAnimationEnd={
                      flashing
                        ? (event) => {
                            if (event.target === event.currentTarget) {
                              onHighlightEnd?.()
                            }
                          }
                        : undefined
                    }
                    onContextMenu={
                      onRowContextMenu && interactive
                        ? (event) => onRowContextMenu(event, offer)
                        : undefined
                    }
                  >
                    <td className="market-cell-num" data-label="Price">
                      <span className="market-price-cell">
                        {owned ? (
                          <span
                            className="market-owned-mark"
                            title="Your order"
                            aria-label="Your order"
                          />
                        ) : null}
                        {currency.format(offer.price)}
                      </span>
                    </td>
                    <td className="market-cell-num" data-label="Qty">
                      {offer.quantity}
                    </td>
                    {side === 'buy' ? (
                      <td data-label="Buyer">{buyerName ?? '—'}</td>
                    ) : null}
                    <td data-label="Location">
                      {locationNames[offer.locationId] ??
                        `Location ${offer.locationId}`}
                    </td>
                    <td data-label="Source">
                      {sources[offer.sourceId] ?? 'Other'}
                    </td>
                    <td data-label="Status">{statusLabels[offer.status]}</td>
                    <td className="market-cell-num" data-label="Updated">
                      {new Date(offer.updatedAt).toLocaleString('en-GB', {
                        dateStyle: 'short',
                        timeStyle: 'short',
                      })}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
