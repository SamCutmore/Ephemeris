export type LoginResponse = {
  token: string
  userId: number
  username: string
}

export type HealthResponse = {
  status: string
}

export type MarketGroup = {
  marketGroupId: number
  parentGroupId: number | null
  marketGroupName: string
  description: string | null
  hasTypes: boolean
}

export type InvType = {
  typeId: number
  typeName: string
  description: string | null
  basePrice: number | null
  published: boolean
  marketGroupId: number | null
}

export type Location = {
  locationId: number
  parentLocationId: number | null
  locationName: string
  description: string | null
}

export type MarketOrder = {
  orderId: number
  typeId: number
  locationId: number
  sourceId: number
  side: 'buy' | 'sell'
  price: number
  quantity: number
  quantityInitial: number
  status: 'open' | 'partial' | 'filled' | 'cancelled'
  externalUrl: string | null
  externalListingId: string | null
  ownerKind: 'user' | 'market_maker' | 'external'
  ownerRef: string | null
  userId: number | null
  username: string | null
  expiresAt: string | null
  createdAt: string
  updatedAt: string
}

export type MarketFill = {
  fillId: number
  typeId: number
  locationId: number
  buyOrderId: number
  sellOrderId: number
  price: number
  quantity: number
  filledAt: string
}

export type MyTransaction = {
  order: MarketOrder
  fills: MarketFill[]
}

export type MarketHistory = {
  locationId: number
  typeId: number
  date: string
  lowPrice: number
  highPrice: number
  avgPrice: number
  volume: number
  orders: number
}

export type OrderBook = {
  typeId: number
  buys: MarketOrder[]
  sells: MarketOrder[]
  locationId: number
}

export type MyOrder = {
  orderId: number
  typeId: number
  typeName: string
  price: number
  quantity: number
  quantityInitial: number
  status: MarketOrder['status']
  createdAt: string
}

export type MarketPrice = {
  typeId: number
  locationId: number
  medianPrice: number | null
  lowestPrice: number | null
  sampleSize: number
}

export type PlaceBuyOrderResponse = {
  orderId: number
  userId: number
  expiresAt: string
  fills: number
}

export type CancelOrderResponse = {
  orderId: number
  status: 'cancelled'
}

export type EditOrderResponse = {
  orderId: number
  price: number
  quantity: number
  quantityInitial: number
  status: string
  fills: number
}

export type PlaceSellOrderResponse = {
  orderId: number
  typeId: number
  locationId: number
  sourceId: number
  externalListingId: string
  price: number
  quantity: number
  quantityInitial: number
  status: string
  fills: number
  lastUpdated: string
  createdAt: string
}

export type EditSellOrderResponse = {
  orderId: number
  sourceId: number
  externalListingId: string
  price: number
  quantity: number
  quantityInitial: number
  status: string
  fills: number
  lastUpdated: string
}

export type CancelSellOrderResponse = {
  orderId: number
  sourceId: number
  externalListingId: string
  status: string
  lastUpdated: string
}

export type CancelStaleSellOrdersResponse = {
  sourceId: number
  updatedBefore: string
  cancelled: number
}
