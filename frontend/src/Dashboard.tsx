import { useState } from 'react'
import type { Location } from './api/types'
import { MyOrders } from './Orders'
import MyTransactions from './MyTransactions'

type DashboardTab = 'orders' | 'transactions'

export default function Dashboard({
  token,
  locations,
  onExpired,
  onNotice,
}: {
  token: string
  locations: Location[]
  onExpired: () => void
  onNotice: (message: string) => void
}) {
  const [tab, setTab] = useState<DashboardTab>('orders')

  const locationNames = Object.fromEntries(
    locations.map((loc) => [loc.locationId, loc.locationName]),
  )

  return (
    <div className="dashboard">
      <nav className="pane-tabs" aria-label="Dashboard views">
        <button
          type="button"
          className="pane-tab"
          aria-selected={tab === 'orders'}
          onClick={() => setTab('orders')}
        >
          Orders
        </button>
        <button
          type="button"
          className="pane-tab"
          aria-selected={tab === 'transactions'}
          onClick={() => setTab('transactions')}
        >
          Transaction history
        </button>
      </nav>

      <div className="dashboard-pane">
        {tab === 'orders' ? (
          <MyOrders
            token={token}
            onExpired={onExpired}
            onNotice={onNotice}
          />
        ) : (
          <MyTransactions
            token={token}
            locationNames={locationNames}
            onExpired={onExpired}
          />
        )}
      </div>
    </div>
  )
}
