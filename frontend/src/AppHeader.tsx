import type { LoginResponse } from './api/types'
import AccountMenu from './AccountMenu'
import brandIcon from './assets/negativeone-icon.png'

type AppHeaderProps = {
  session: LoginResponse | null
  onLogin: (session: LoginResponse) => void
  onLogout: () => void
  onNotice: (message: string) => void
}

export default function AppHeader({
  session,
  onLogin,
  onLogout,
  onNotice,
}: AppHeaderProps) {
  return (
    <header className="app-header">
      <div className="app-header-brand">
        <img
          className="app-header-logo"
          src={brandIcon}
          alt=""
          width={40}
          height={40}
        />
        <span className="app-header-title">
          <strong>Ephemeris</strong>
          <span className="sidebar-muted">Orbital simulation scaffold</span>
        </span>
      </div>

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
