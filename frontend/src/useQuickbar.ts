import { useCallback, useEffect, useState } from 'react'
import type { InvType } from './api/types'

const STORAGE_KEY = 'negativeone.quickbar'

function readStored(): InvType[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as InvType[]
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

/** Persisted favourite types (EMB-style quickbar). */
export function useQuickbar() {
  const [items, setItems] = useState<InvType[]>(() => readStored())

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(items))
  }, [items])

  const has = useCallback(
    (typeId: number) => items.some((t) => t.typeId === typeId),
    [items],
  )

  const add = useCallback((type: InvType) => {
    setItems((prev) =>
      prev.some((t) => t.typeId === type.typeId) ? prev : [...prev, type],
    )
  }, [])

  const remove = useCallback((typeId: number) => {
    setItems((prev) => prev.filter((t) => t.typeId !== typeId))
  }, [])

  return { items, has, add, remove }
}
