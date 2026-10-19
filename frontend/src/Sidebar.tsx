import { useEffect, useState } from 'react'
import {
  listMarketGroups,
  listTypes,
  searchTypes,
} from './api/clients'
import type { InvType, MarketGroup } from './api/types'
import { TypeIcon } from './typeIcons'
import './Sidebar.css'

type NavTab = 'browse' | 'quickbar'

type SidebarProps = {
  search: string
  selectedTypeId: number | null
  navTab: NavTab
  quickbar: InvType[]
  onNavTabChange: (tab: NavTab) => void
  onSearchChange: (value: string) => void
  onSelectType: (type: InvType) => void
  onRemoveQuickbar: (typeId: number) => void
}

type GroupNode = {
  group: MarketGroup
  children: GroupNode[] | null
  types: InvType[] | null
  expanded: boolean
  loading: boolean
}

function Sidebar({
  search,
  selectedTypeId,
  navTab,
  quickbar,
  onNavTabChange,
  onSearchChange,
  onSelectType: onSelectTypeProp,
  onRemoveQuickbar,
}: SidebarProps) {
  const [roots, setRoots] = useState<GroupNode[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loadingRoots, setLoadingRoots] = useState(true)
  const [searchResults, setSearchResults] = useState<InvType[]>([])
  const [searchLoading, setSearchLoading] = useState(false)
  const [searchError, setSearchError] = useState<string | null>(null)
  const [mobileOpen, setMobileOpen] = useState(false)

  function onSelectType(type: InvType) {
    onSelectTypeProp(type)
    setMobileOpen(false)
  }

  const q = search.trim()
  const searching = q.length >= 2

  useEffect(() => {
    let cancelled = false
    setLoadingRoots(true)
    listMarketGroups('root')
      .then((groups) => {
        if (cancelled) return
        setRoots(
          groups.map((group) => ({
            group,
            children: null,
            types: null,
            expanded: false,
            loading: false,
          })),
        )
        setError(null)
      })
      .catch((e) => {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : 'Failed to load market')
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingRoots(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    if (!searching) {
      setSearchResults([])
      setSearchError(null)
      setSearchLoading(false)
      return
    }

    let cancelled = false
    const handle = window.setTimeout(() => {
      setSearchLoading(true)
      setSearchError(null)
      searchTypes(q)
        .then((rows) => {
          if (!cancelled) setSearchResults(rows)
        })
        .catch((e) => {
          if (!cancelled) {
            setSearchError(
              e instanceof Error ? e.message : 'Search failed',
            )
            setSearchResults([])
          }
        })
        .finally(() => {
          if (!cancelled) setSearchLoading(false)
        })
    }, 200)

    return () => {
      cancelled = true
      window.clearTimeout(handle)
    }
  }, [q, searching])

  async function toggleGroup(path: number[]) {
    const node = getNode(roots, path)
    if (!node) return

    if (node.expanded) {
      setRoots((prev) =>
        updateNode(prev, path, (n) => ({ ...n, expanded: false })),
      )
      return
    }

    if (node.children !== null && node.types !== null) {
      setRoots((prev) =>
        updateNode(prev, path, (n) => ({ ...n, expanded: true })),
      )
      return
    }

    const groupId = node.group.marketGroupId
    const loadTypes = node.group.hasTypes

    setRoots((prev) =>
      updateNode(prev, path, (n) => ({ ...n, loading: true, expanded: true })),
    )

    try {
      const [childGroups, types] = await Promise.all([
        listMarketGroups(groupId),
        loadTypes ? listTypes(groupId) : Promise.resolve([] as InvType[]),
      ])
      setRoots((prev) =>
        updateNode(prev, path, (n) => ({
          ...n,
          loading: false,
          expanded: true,
          children: childGroups.map((group) => ({
            group,
            children: null,
            types: null,
            expanded: false,
            loading: false,
          })),
          types,
        })),
      )
      setError(null)
    } catch (e) {
      setRoots((prev) =>
        updateNode(prev, path, (n) => ({
          ...n,
          loading: false,
          expanded: false,
        })),
      )
      setError(e instanceof Error ? e.message : 'Failed to open group')
    }
  }

  return (
    <aside className="sidebar">
      <button
        type="button"
        className="sidebar-mobile-toggle"
        aria-expanded={mobileOpen}
        onClick={() => setMobileOpen((current) => !current)}
      >
        <span>Browse items</span>
        <span aria-hidden="true">{mobileOpen ? '▴' : '▾'}</span>
      </button>

      <div
        className={
          mobileOpen ? 'sidebar-body is-open' : 'sidebar-body'
        }
      >
      <nav className="sidebar-tabs" role="tablist" aria-label="Navigation">
        <button
          type="button"
          role="tab"
          className="sidebar-tab"
          aria-selected={navTab === 'browse'}
          onClick={() => onNavTabChange('browse')}
        >
          Browse
        </button>
        <button
          type="button"
          role="tab"
          className="sidebar-tab"
          aria-selected={navTab === 'quickbar'}
          onClick={() => onNavTabChange('quickbar')}
        >
          Quickbar
        </button>
      </nav>

      {navTab === 'browse' ? (
        <>
          <label className="sidebar-search">
            <span className="visually-hidden">Search</span>
            <input
              type="search"
              value={search}
              onChange={(event) => onSearchChange(event.target.value)}
              placeholder="Search types…"
            />
          </label>

          {searching ? (
            <section className="sidebar-tree" aria-label="Search results">
              {searchLoading ? <p>Searching…</p> : null}
              {searchError ? (
                <p className="sidebar-error">{searchError}</p>
              ) : null}
              {!searchLoading && !searchResults.length ? (
                <p>No matches</p>
              ) : null}
              <ul className="sidebar-scroll">
                {searchResults.map((type) => (
                  <li key={type.typeId}>
                    <button
                      type="button"
                      className={
                        selectedTypeId === type.typeId
                          ? 'sidebar-type selected'
                          : 'sidebar-type'
                      }
                      onClick={() => onSelectType(type)}
                    >
                      <TypeIcon
                        typeId={type.typeId}
                        marketGroupId={type.marketGroupId}
                        typeName={type.typeName}
                        size={22}
                      />
                      <span>{type.typeName}</span>
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          ) : (
            <section className="sidebar-tree" aria-label="Browse categories">
              {loadingRoots ? <p>Loading…</p> : null}
              {error ? <p className="sidebar-error">{error}</p> : null}
              {!loadingRoots && !roots.length ? (
                <p>No market groups</p>
              ) : null}
              <ul className="sidebar-scroll sidebar-categories">
                {roots.map((node, index) => (
                  <GroupBranch
                    key={node.group.marketGroupId}
                    node={node}
                    path={[index]}
                    depth={0}
                    selectedTypeId={selectedTypeId}
                    onToggle={toggleGroup}
                    onSelectType={onSelectType}
                  />
                ))}
              </ul>
            </section>
          )}
        </>
      ) : (
        <section className="sidebar-tree" aria-label="Quickbar">
          {!quickbar.length ? (
            <p className="sidebar-muted">
              Pin types from the item view to save them here.
            </p>
          ) : (
            <ul className="sidebar-scroll">
              {quickbar.map((type) => (
                <li key={type.typeId} className="sidebar-quickbar-row">
                  <button
                    type="button"
                    className={
                      selectedTypeId === type.typeId
                        ? 'sidebar-type selected'
                        : 'sidebar-type'
                    }
                    onClick={() => onSelectType(type)}
                  >
                    <TypeIcon
                      typeId={type.typeId}
                      marketGroupId={type.marketGroupId}
                      typeName={type.typeName}
                      size={22}
                    />
                    <span>{type.typeName}</span>
                  </button>
                  <button
                    type="button"
                    className="sidebar-unpin"
                    title="Remove from quickbar"
                    onClick={() => onRemoveQuickbar(type.typeId)}
                  >
                    ×
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}
      </div>
    </aside>
  )
}

type BranchProps = {
  node: GroupNode
  path: number[]
  depth: number
  selectedTypeId: number | null
  onToggle: (path: number[]) => void
  onSelectType: (type: InvType) => void
}

function GroupBranch({
  node,
  path,
  depth,
  selectedTypeId,
  onToggle,
  onSelectType,
}: BranchProps) {
  const nested = depth > 0
  const open = node.expanded

  return (
    <li
      className={
        nested ? 'sidebar-group sidebar-group--nested' : 'sidebar-group sidebar-group--root'
      }
    >
      <button
        type="button"
        className={[
          'sidebar-group-toggle',
          nested ? 'sidebar-group-toggle--nested' : 'sidebar-group-toggle--root',
          open ? 'is-open' : '',
        ]
          .filter(Boolean)
          .join(' ')}
        aria-expanded={open}
        onClick={() => onToggle(path)}
      >
        <span className="sidebar-caret" aria-hidden="true">
          {open ? '▾' : '▸'}
        </span>
        {nested ? (
          <span className="sidebar-nest-mark" aria-hidden="true">
            └
          </span>
        ) : null}
        <span className="sidebar-group-name">{node.group.marketGroupName}</span>
        {node.loading ? <span className="sidebar-muted">…</span> : null}
      </button>

      {open ? (
        <div className="sidebar-nest">
          <ul className="sidebar-nest-list">
            {(node.children ?? []).map((child, index) => (
              <GroupBranch
                key={child.group.marketGroupId}
                node={child}
                path={[...path, index]}
                depth={depth + 1}
                selectedTypeId={selectedTypeId}
                onToggle={onToggle}
                onSelectType={onSelectType}
              />
            ))}
            {(node.types ?? []).map((type) => (
              <li key={type.typeId}>
                <button
                  type="button"
                  className={
                    selectedTypeId === type.typeId
                      ? 'sidebar-type selected'
                      : 'sidebar-type'
                  }
                  onClick={() => onSelectType(type)}
                >
                  <TypeIcon
                    typeId={type.typeId}
                    marketGroupId={type.marketGroupId}
                    typeName={type.typeName}
                    size={20}
                  />
                  <span>{type.typeName}</span>
                </button>
              </li>
            ))}
            {!node.loading &&
            !(node.children ?? []).length &&
            !(node.types ?? []).length ? (
              <li className="sidebar-muted">Empty</li>
            ) : null}
          </ul>
        </div>
      ) : null}
    </li>
  )
}

function getNode(roots: GroupNode[], path: number[]): GroupNode | null {
  let current: GroupNode | undefined = roots[path[0]]
  for (let i = 1; i < path.length; i++) {
    if (!current?.children) return null
    current = current.children[path[i]]
  }
  return current ?? null
}

function updateNode(
  roots: GroupNode[],
  path: number[],
  updater: (node: GroupNode) => GroupNode,
): GroupNode[] {
  if (path.length === 0) return roots
  return roots.map((node, index) => {
    if (index !== path[0]) return node
    if (path.length === 1) return updater(node)
    if (!node.children) return node
    return {
      ...node,
      children: updateNode(node.children, path.slice(1), updater),
    }
  })
}

export default Sidebar
