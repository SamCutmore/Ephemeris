import { useCallback, useEffect, useMemo, useState } from 'react'
import type { InvType, Location, LoginResponse } from './api/types'
import { listLocations } from './api/clients'
import AppHeader from './AppHeader'
import Sidebar from './Sidebar'
import ItemWorkspace from './ItemWorkspace'
import NoticePopup from './NoticePopup'
import Dashboard from './Dashboard'
import { WORLD_LOCATION_ID } from './locationScope'
import { useQuickbar } from './useQuickbar'
import './App.css'

type Page = 'market' | 'dashboard'
type ItemTab = 'data' | 'history'
type NavTab = 'browse' | 'quickbar'

function App() {
  const [search, setSearch] = useState('')
  const [selectedType, setSelectedType] = useState<InvType | null>(null)
  const [session, setSession] = useState<LoginResponse | null>(null)
  const [page, setPage] = useState<Page>('market')
  const [itemTab, setItemTab] = useState<ItemTab>('data')
  const [navTab, setNavTab] = useState<NavTab>('browse')
  const [notice, setNotice] = useState('')
  const [marketVersion, setMarketVersion] = useState(0)
  const [ordersFlashKey, setOrdersFlashKey] = useState(0)
  const [highlightBuyOrderId, setHighlightBuyOrderId] = useState<number | null>(
    null,
  )
  const [locations, setLocations] = useState<Location[]>([])
  const [locationId, setLocationId] = useState(WORLD_LOCATION_ID)
  const quickbar = useQuickbar()

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const roots = await listLocations('root')
        const nested = await Promise.all(
          roots.map(async (root) => {
            const children = await listLocations(root.locationId)
            return [root, ...children]
          }),
        )
        if (!cancelled) {
          const flat = nested.flat()
          setLocations(flat)
          setLocationId((current) =>
            current === WORLD_LOCATION_ID ||
            flat.some((l) => l.locationId === current)
              ? current
              : WORLD_LOCATION_ID,
          )
        }
      } catch {
        if (!cancelled) {
          setLocations([
            {
              locationId: 1,
              parentLocationId: null,
              locationName: 'UK',
              description: null,
            },
          ])
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  const locationName = useMemo(
    () =>
      locationId === WORLD_LOCATION_ID
        ? 'World'
        : (locations.find((l) => l.locationId === locationId)?.locationName ??
          `Location ${locationId}`),
    [locations, locationId],
  )

  const handleExpired = useCallback(() => {
    setSession(null)
    setNotice('Your session expired. Please log in again.')
  }, [])

  function selectType(type: InvType) {
    setSelectedType(type)
    setPage('market')
    setItemTab('data')
  }

  const clearNotice = useCallback(() => setNotice(''), [])
  const clearHighlightBuy = useCallback(
    () => setHighlightBuyOrderId(null),
    [],
  )

  return (
    <div className="app-shell">
      <AppHeader
        page={page}
        session={session}
        ordersFlashKey={ordersFlashKey}
        onPageChange={setPage}
        onLogin={(result) => {
          setSession(result)
          setNotice('')
        }}
        onLogout={() => setSession(null)}
        onNotice={setNotice}
      />

      <Sidebar
        search={search}
        selectedTypeId={selectedType?.typeId ?? null}
        navTab={navTab}
        quickbar={quickbar.items}
        onNavTabChange={setNavTab}
        onSearchChange={setSearch}
        onSelectType={selectType}
        onRemoveQuickbar={quickbar.remove}
      />

      <NoticePopup message={notice} onClose={clearNotice} />

      <main id="center">
        {page === 'dashboard' ? (
          <>
            <h1>Dashboard</h1>
            {session ? (
              <Dashboard
                key={session.userId}
                token={session.token}
                locations={locations}
                onExpired={handleExpired}
                onNotice={setNotice}
              />
            ) : (
              <p>
                Use the account menu (top right) to log in and view your
                dashboard.
              </p>
            )}
          </>
        ) : selectedType ? (
          <ItemWorkspace
            key={selectedType.typeId}
            type={selectedType}
            locationId={locationId}
            locationName={locationName}
            locations={locations}
            itemTab={itemTab}
            session={session}
            marketVersion={marketVersion}
            highlightBuyOrderId={highlightBuyOrderId}
            pinned={quickbar.has(selectedType.typeId)}
            onItemTabChange={setItemTab}
            onLocationChange={setLocationId}
            onTogglePin={() => {
              if (quickbar.has(selectedType.typeId)) {
                quickbar.remove(selectedType.typeId)
              } else {
                quickbar.add(selectedType)
                setNavTab('quickbar')
              }
            }}
            onExpired={handleExpired}
            onNeedLogin={() => setNotice('Log in to place a buy order.')}
            onHighlightBuyConsumed={clearHighlightBuy}
            onNotice={setNotice}
            onOrdersChanged={() =>
              setMarketVersion((current) => current + 1)
            }
            onBuyPlaced={(result) => {
              setMarketVersion((current) => current + 1)
              setOrdersFlashKey((current) => current + 1)
              setHighlightBuyOrderId(result.orderId)
              setNotice(
                result.fills > 0
                  ? `Your buy order #${result.orderId} was filled.`
                  : `Your buy order #${result.orderId} was placed.`,
              )
            }}
          />
        ) : (
          <>
            <h1>Market</h1>
            <p>
              Select a product from Browse or Quickbar, or search for a type.
              After selecting an item, use Location next to Market Data / Price
              History to switch regional books (e.g. UK vs Germany).
            </p>
          </>
        )}
      </main>
    </div>
  )
}

export default App
