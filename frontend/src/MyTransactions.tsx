import { useEffect, useState } from 'react'
import { listMyTransactions } from './api/clients'
import { handleFailure } from './api/errors'
import type { MyTransaction } from './api/types'
import { TypeIcon } from './typeIcons'

const currency = new Intl.NumberFormat('en-GB', {
  style: 'currency',
  currency: 'GBP',
})

const statusLabels: Record<MyTransaction['order']['status'], string> = {
  open: 'Pending',
  partial: 'Partially filled',
  filled: 'Completed',
  cancelled: 'Cancelled',
}

export default function MyTransactions({
  token,
  locationNames,
  onExpired,
}: {
  token: string
  locationNames: Record<number, string>
  onExpired: () => void
}) {
  const [rows, setRows] = useState<MyTransaction[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [refresh, setRefresh] = useState(0)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError('')
    setRows([])

    listMyTransactions(token)
      .then((data) => {
        if (!cancelled) setRows(data)
      })
      .catch((failure: unknown) => {
        if (!cancelled) setError(handleFailure(failure, onExpired))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [token, refresh, onExpired])

  return (
    <section className="orders-panel" aria-label="Transaction history">
      <div className="orders-toolbar">
        <button
          type="button"
          disabled={loading}
          onClick={() => setRefresh((current) => current + 1)}
        >
          Refresh
        </button>
      </div>

      {loading ? (
        <p role="status">Loading transactions…</p>
      ) : error ? (
        <p className="error" role="alert">
          {error}
        </p>
      ) : rows.length === 0 ? (
        <p>No completed or cancelled orders yet.</p>
      ) : (
        <div className="orders-table-scroll">
          <table className="orders-table">
            <thead>
              <tr>
                <th scope="col">Order</th>
                <th scope="col">Product</th>
                <th scope="col">Location</th>
                <th scope="col">Limit price</th>
                <th scope="col">Filled qty</th>
                <th scope="col">Fill total</th>
                <th scope="col">Fills</th>
                <th scope="col">Status</th>
                <th scope="col">Updated</th>
              </tr>
            </thead>
            <tbody>
              {rows.map(({ order, fills }) => {
                const filledQty = fills.reduce(
                  (sum, fill) => sum + fill.quantity,
                  0,
                )
                const fillTotal = fills.reduce(
                  (sum, fill) => sum + fill.price * fill.quantity,
                  0,
                )
                return (
                  <tr key={order.orderId}>
                    <td data-label="Order">#{order.orderId}</td>
                    <td data-label="Product">
                      <span className="order-product">
                        <TypeIcon typeId={order.typeId} size={28} />
                        <span>Type {order.typeId}</span>
                      </span>
                    </td>
                    <td data-label="Location">
                      {locationNames[order.locationId] ??
                        `Location ${order.locationId}`}
                    </td>
                    <td data-label="Limit price">{currency.format(order.price)}</td>
                    <td data-label="Filled qty">{filledQty}</td>
                    <td data-label="Fill total">
                      {filledQty > 0 ? currency.format(fillTotal) : '—'}
                    </td>
                    <td data-label="Fills">{fills.length}</td>
                    <td data-label="Status">{statusLabels[order.status]}</td>
                    <td data-label="Updated">
                      {new Date(order.updatedAt).toLocaleString('en-GB')}
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
