import { useEffect, useId, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { login, logout, register } from './api/clients'
import { errorMessage } from './api/errors'
import type { LoginResponse } from './api/types'

type Mode = 'menu' | 'login' | 'register'

type AccountMenuProps = {
  session: LoginResponse | null
  onLogin: (session: LoginResponse) => void
  onLogout: () => void
  onExpiredNotice?: (message: string) => void
}

function UserIcon() {
  return (
    <svg
      className="account-menu-icon"
      viewBox="0 0 24 24"
      width="22"
      height="22"
      aria-hidden="true"
      focusable="false"
    >
      <circle
        cx="12"
        cy="8"
        r="3.5"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.75"
      />
      <path
        d="M5 19.5c.8-3.2 3.2-5 7-5s6.2 1.8 7 5"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.75"
        strokeLinecap="round"
      />
    </svg>
  )
}

export default function AccountMenu({
  session,
  onLogin,
  onLogout,
  onExpiredNotice,
}: AccountMenuProps) {
  const [open, setOpen] = useState(false)
  const [mode, setMode] = useState<Mode>('menu')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [passwordAgain, setPasswordAgain] = useState('')
  const [busy, setBusy] = useState(false)
  const [loggingOut, setLoggingOut] = useState(false)
  const [error, setError] = useState('')
  const rootRef = useRef<HTMLDivElement>(null)
  const panelId = useId()

  useEffect(() => {
    if (!open) return

    function onPointerDown(event: MouseEvent) {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false)
        setMode('menu')
        setError('')
      }
    }

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false)
        setMode('menu')
        setError('')
      }
    }

    document.addEventListener('mousedown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('mousedown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  useEffect(() => {
    if (!session) {
      setMode('menu')
    }
  }, [session])

  async function submitAuth(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return

    const name = username.trim()
    setError('')

    if (!name || name.includes(';') || password.includes(';')) {
      setError('Enter a username and password without semicolons.')
      return
    }

    if (mode === 'register' && password !== passwordAgain) {
      setError('Passwords do not match.')
      return
    }

    setBusy(true)
    try {
      if (mode === 'register') {
        await register(name, password, passwordAgain)
      }
      const next = await login(name, password)
      onLogin(next)
      setOpen(false)
      setMode('menu')
      setUsername('')
      setPassword('')
      setPasswordAgain('')
    } catch (failure) {
      setError(errorMessage(failure))
    } finally {
      setBusy(false)
    }
  }

  async function handleLogout() {
    if (!session || loggingOut) return
    setLoggingOut(true)
    try {
      await logout(session.token)
      onExpiredNotice?.('Logged out.')
    } catch {
      onExpiredNotice?.(
        'Logged out on this device. The server could not confirm logout.',
      )
    } finally {
      onLogout()
      setLoggingOut(false)
      setOpen(false)
      setMode('menu')
    }
  }

  const label = session
    ? `Account menu for ${session.username}`
    : 'Account menu'

  return (
    <div className="account-menu" ref={rootRef}>
      <button
        type="button"
        className="account-menu-trigger"
        aria-label={label}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => {
          setOpen((current) => !current)
          setMode('menu')
          setError('')
        }}
      >
        <UserIcon />
      </button>

      {open ? (
        <div
          id={panelId}
          className="account-menu-panel"
          role="dialog"
          aria-label="Account"
        >
          {session && mode === 'menu' ? (
            <>
              <p className="account-menu-user">
                Signed in as <strong>{session.username}</strong>
              </p>
              <button
                type="button"
                disabled={loggingOut}
                onClick={handleLogout}
              >
                {loggingOut ? 'Logging out…' : 'Log out'}
              </button>
            </>
          ) : null}

          {!session && mode === 'menu' ? (
            <div className="account-menu-actions">
              <button type="button" onClick={() => setMode('login')}>
                Log in
              </button>
              <button type="button" onClick={() => setMode('register')}>
                Create account
              </button>
            </div>
          ) : null}

          {!session && (mode === 'login' || mode === 'register') ? (
            <>
              <h2>{mode === 'register' ? 'Create account' : 'Log in'}</h2>
              <form className="message-form" onSubmit={submitAuth}>
                <label>
                  Username
                  <input
                    required
                    autoComplete="username"
                    value={username}
                    onChange={(event) => setUsername(event.target.value)}
                  />
                </label>
                <label>
                  Password
                  <input
                    required
                    type="password"
                    autoComplete={
                      mode === 'register' ? 'new-password' : 'current-password'
                    }
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                  />
                </label>
                {mode === 'register' ? (
                  <label>
                    Confirm password
                    <input
                      required
                      type="password"
                      autoComplete="new-password"
                      value={passwordAgain}
                      onChange={(event) =>
                        setPasswordAgain(event.target.value)
                      }
                    />
                  </label>
                ) : null}
                <button
                  type="submit"
                  className="account-menu-submit"
                  disabled={busy}
                >
                  {busy
                    ? 'Please wait…'
                    : mode === 'register'
                      ? 'Create account'
                      : 'Log in'}
                </button>
              </form>
              {error ? (
                <p className="error" role="alert">
                  {error}
                </p>
              ) : null}
              <button
                type="button"
                disabled={busy}
                onClick={() => {
                  setMode(mode === 'register' ? 'login' : 'register')
                  setError('')
                }}
              >
                {mode === 'register'
                  ? 'Use existing account'
                  : 'Create an account'}
              </button>
            </>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
