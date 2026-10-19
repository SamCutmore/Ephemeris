import type { LoginResponse } from './api/types'
import AccountMenu from './AccountMenu'
import brandIcon from './assets/negativeone-icon.png'

type Page = 'market' | 'dashboard'

type AppHeaderProps = {
  page: Page
  session: LoginResponse | null
  /** Bump to flash the Dashboard tab (e.g. after a successful buy). */
  ordersFlashKey?: number
  onPageChange: (page: Page) => void
  onLogin: (session: LoginResponse) => void
  onLogout: () => void
  onNotice: (message: string) => void
}

export default function AppHeader({
  page,
  session,
  ordersFlashKey = 0,
  onPageChange,
  onLogin,
  onLogout,
  onNotice,
}: AppHeaderProps) {
  return (
    <header className="app-header">
      <button
        type="button"
        className="app-header-brand"
        onClick={() => onPageChange('market')}
      >
        <img
          className="app-header-logo"
          src={brandIcon}
          alt=""
          width={40}
          height={40}
        />
        <span className="app-header-title">
          <strong>NegativeOne</strong>
          <span className="sidebar-muted">Second Hand Market Browser</span>
        </span>
      </button>

      <nav className="orders-toolbar app-header-dashboards" aria-label="Dashboards">
        <button
          type="button"
          aria-current={page === 'market' ? 'page' : undefined}
          onClick={() => onPageChange('market')}
        >
          Market
        </button>
        <button
          type="button"
          className={ordersFlashKey > 0 ? 'tab-flash' : undefined}
          key={`dashboard-${ordersFlashKey}`}
          aria-current={page === 'dashboard' ? 'page' : undefined}
          onClick={() => onPageChange('dashboard')}
        >
          Dashboard
        </button>
      </nav>

      <div className="app-header-account">
        <AccountMenu
          session={session}
          onLogin={onLogin}
          onLogout={onLogout}
          onExpiredNotice={onNotice}
        />
      </div>
    </header>
  )
}
