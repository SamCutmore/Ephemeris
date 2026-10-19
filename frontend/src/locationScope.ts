import type { Location } from './api/types'

/** Sentinel for the flat "World" option — aggregates every known location. */
export const WORLD_LOCATION_ID = 0

export function isWorldLocation(locationId: number): boolean {
  return locationId === WORLD_LOCATION_ID
}

/** Selected location plus every descendant under it (any depth). World → all. */
export function locationScopeIds(
  locations: Pick<Location, 'locationId' | 'parentLocationId'>[],
  selectedId: number,
): number[] {
  if (isWorldLocation(selectedId)) {
    return locations.map((loc) => loc.locationId)
  }

  const children = new Map<number, number[]>()
  for (const loc of locations) {
    if (loc.parentLocationId == null) continue
    const list = children.get(loc.parentLocationId) ?? []
    list.push(loc.locationId)
    children.set(loc.parentLocationId, list)
  }

  const ids: number[] = []
  const stack = [selectedId]
  const seen = new Set<number>()

  while (stack.length) {
    const id = stack.pop()!
    if (seen.has(id)) continue
    seen.add(id)
    ids.push(id)
    for (const child of children.get(id) ?? []) {
      stack.push(child)
    }
  }

  return ids
}
