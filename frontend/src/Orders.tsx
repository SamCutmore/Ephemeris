import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import type { MouseEvent as ReactMouseEvent } from 'react'
import { getMyOrders } from './api/clients'
import { handleFailure } from './api/errors'
import type { MyOrder } from './api/types'
import CancelBuyOrderDialog from './CancelBuyOrderDialog'
import { clampFixedMenu } from './clampFixedMenu'
import EditBuyOrderDialog from './EditBuyOrderDialog'
import { TypeIcon } from './typeIcons'

const currency = new Intl.NumberFormat('en-GB', {
  style: 'currency',
  currency: 'GBP',
})

const statusLabels: Record<MyOrder['status'], string> = {
  open: 'Pending',
  partial: 'Partially filled',
  filled: 'Completed',
  cancelled: 'Cancelled',
}

type MenuState = {
  x: number
  y: number
  order: MyOrder
}

function isEditable(order: MyOrder): boolean {
  return order.status === 'open' || order.status === 'partial'
}

export function MyOrders({
  token,
  onExpired,
  onNotice,
}: {
  token: string
  onExpired: () => void
  onNotice: (message: string) => void
}) {
  const [orders, setOrders] = useState<MyOrder[]>([])
  const [filter, setFilter] = useState<'all' | MyOrder['status']>('all')
  const [refresh, setRefresh] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [menu, setMenu] = useState<MenuState | null>(null)
  const menuRef = useRef<HTMLUListElement | null>(null)
  const [editing, setEditing] = useState<MyOrder | null>(null)
  const [cancelling, setCancelling] = useState<MyOrder | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError('')
    setOrders([])

    getMyOrders(token)
      .then((rows) => {
        if (!cancelled) setOrders(rows)
      })
      .catch((failure: unknown) => {
        if (!cancelled) {
          setError(handleFailure(failure, onExpired))
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [token, refresh, onExpired])

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
    return () => {
      document.removeEventListener('mousedown', close)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [menu])

  const visibleOrders = orders.filter(
    (order) => filter === 'all' || order.status === filter,
  )

  function openMenu(event: ReactMouseEvent, order: MyOrder) {
    if (!isEditable(order)) return
    event.preventDefault()
    setMenu({ x: event.clientX, y: event.clientY, order })
  }

  return (
    <section className="orders-panel" aria-label="My orders">
      <div className="orders-toolbar">
        <label>
          Status{' '}
          <select
            value={filter}
            onChange={(event) =>
              setFilter(event.target.value as 'all' | MyOrder['status'])
            }
          >
            <option value="all">All orders</option>
            <option value="open">Pending</option>
            <option value="partial">Partially filled</option>
            <option value="filled">Completed</option>
            <option value="cancelled">Cancelled</option>
          </select>
        </label>

        <button
          type="button"
          disabled={loading}
          onClick={() => setRefresh((current) => current + 1)}
        >
          Refresh
        </button>
      </div>

      {loading ? (
        <p role="status">Loading orders…</p>
      ) : error ? (
        <p className="error" role="alert">{error}</p>
      ) : visibleOrders.length === 0 ? (
        <p>No orders in this view.</p>
      ) : (
        <div className="orders-table-scroll">
          <table className="orders-table">
            <thead>
              <tr>
                <th scope="col">Order</th>
                <th scope="col">Product</th>
                <th scope="col">Limit price</th>
                <th scope="col">Ordered</th>
                <th scope="col">Filled</th>
                <th scope="col">Remaining</th>
                <th scope="col">Status</th>
                <th scope="col">Created</th>
              </tr>
            </thead>
            <tbody>
              {visibleOrders.map((order) => (
                <tr
                  key={order.orderId}
                  className={
                    isEditable(order) ? 'market-row-interactive' : undefined
                  }
                  onContextMenu={(event) => openMenu(event, order)}
                >
                  <td data-label="Order">#{order.orderId}</td>
                  <td data-label="Product">
                    <span className="order-product">
                      <TypeIcon
                        typeId={order.typeId}
                        typeName={order.typeName}
                        size={28}
                      />
                      <span>{order.typeName}</span>
                    </span>
                  </td>
                  <td data-label="Limit price">{currency.format(order.price)}</td>
                  <td data-label="Ordered">{order.quantityInitial}</td>
                  <td data-label="Filled">{order.quantityInitial - order.quantity}</td>
                  <td data-label="Remaining">{order.quantity}</td>
                  <td data-label="Status">{statusLabels[order.status]}</td>
                  <td data-label="Created">{new Date(order.createdAt).toLocaleString('en-GB')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {menu ? (
        <ul
          ref={menuRef}
          className="context-menu"
          style={{ top: menu.y, left: menu.x }}
          role="menu"
          onMouseDown={(event) => event.stopPropagation()}
        >
          <li role="none">
            <button
              type="button"
              role="menuitem"
              onClick={() => {
                const order = menu.order
                setMenu(null)
                setEditing(order)
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
                const order = menu.order
                setMenu(null)
                setCancelling(order)
              }}
            >
              Cancel order
            </button>
          </li>
        </ul>
      ) : null}

      {editing ? (
        <EditBuyOrderDialog
          order={editing}
          typeName={editing.typeName}
          token={token}
          onClose={() => setEditing(null)}
          onExpired={onExpired}
          onSaved={(result) => {
            setEditing(null)
            onNotice(`Order #${result.orderId} updated.`)
            setRefresh((current) => current + 1)
          }}
        />
      ) : null}

      {cancelling ? (
        <CancelBuyOrderDialog
          orderId={cancelling.orderId}
          typeName={cancelling.typeName}
          price={cancelling.price}
          quantity={cancelling.quantity}
          token={token}
          onClose={() => setCancelling(null)}
          onExpired={onExpired}
          onCancelled={() => {
            onNotice(`Order #${cancelling.orderId} cancelled.`)
            setCancelling(null)
            setRefresh((current) => current + 1)
          }}
        />
      ) : null}
    </section>
  )
}
