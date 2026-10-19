import { useCallback, useState } from 'react'
import type { LoginResponse } from './api/types'
import AppHeader from './AppHeader'
import NoticePopup from './NoticePopup'
import './App.css'

function App() {
  const [session, setSession] = useState<LoginResponse | null>(null)
  const [notice, setNotice] = useState('')

  const clearNotice = useCallback(() => setNotice(''), [])

  return (
    <div className="app-shell app-shell--auth-only">
      <AppHeader
        session={session}
        onLogin={(result) => {
          setSession(result)
          setNotice('')
        }}
        onLogout={() => setSession(null)}
        onNotice={setNotice}
      />

      <NoticePopup message={notice} onClose={clearNotice} />

      <main id="center">
        <h1>Ephemeris</h1>
        {session ? (
          <p>
            Signed in as <strong>{session.username}</strong>. Auth and the
            stack scaffold are ready — orbital / SOI domain comes next.
          </p>
        ) : (
          <p>
            Use the account menu (top right) to register or log in.
          </p>
        )}
      </main>
    </div>
  )
}

export default App
