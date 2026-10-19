import axios from 'axios'
import type {
  CancelOrderResponse,
  CancelSellOrderResponse,
  CancelStaleSellOrdersResponse,
  EditOrderResponse,
  EditSellOrderResponse,
  HealthResponse,
  InvType,
  Location,
  LoginResponse,
  MarketGroup,
  MarketHistory,
  MarketOrder,
  MarketPrice,
  MyOrder,
  MyTransaction,
  OrderBook,
  PlaceBuyOrderResponse,
  PlaceSellOrderResponse,
} from './types'

const api = axios.create({ baseURL: '/' })

export async function health(): Promise<HealthResponse> {
  const { data } = await api.get<HealthResponse>('/api/health')
  return data
}

export async function register(
  username: string,
  password: string,
  passwordAgain: string,
): Promise<void> {
  const { data } = await api.post<string>('/api', null, {
    params: { q: `register: ${username};${password};${passwordAgain}` },
  })
  if (data !== 'OK') {
    throw new Error('Registration failed')
  }
}

export async function login(username: string, password: string): Promise<LoginResponse> {
  const { data } = await api.post<LoginResponse | string>('/api', null, {
    params: { q: `login: ${username};${password}` },
  })
  if (typeof data === 'string' || !data?.token) {
    throw new Error('Login failed')
  }
  return data
}

export async function logout(token: string): Promise<void> {
  const { data } = await api.post<string>('/api', null, {
    params: { q: `logout: ${token}` },
  })
  if (data !== 'OK') {
    throw new Error('Logout failed')
  }
}

export async function listMarketGroups(
  parent: 'root' | number = 'root',
): Promise<MarketGroup[]> {
  const { data } = await api.get<MarketGroup[]>('/api', {
    params: { q: `listMarketGroups: ${parent}` },
  })
  return data
}

export async function listTypes(groupId: number): Promise<InvType[]> {
  const { data } = await api.get<InvType[]>('/api', {
    params: { q: `listTypes: ${groupId}` },
  })
  return data
}

export async function searchTypes(query: string): Promise<InvType[]> {
  const { data } = await api.get<InvType[]>('/api', {
    params: { q: `searchTypes: ${query}` },
  })
  return data
}

export async function listLocations(
  parent: 'root' | number = 'root',
): Promise<Location[]> {
  const { data } = await api.get<Location[]>('/api', {
    params: { q: `listLocations: ${parent}` },
  })
  return data
}

export async function getOrderBook(
  typeId: number,
  locationId = 1,
): Promise<OrderBook> {
  const { data } = await api.get<OrderBook>('/api', {
    params: { q: `getOrderBook: ${typeId};${locationId}` },
  })
  return data
}

export async function getHistory(
  typeId: number,
  locationId = 1,
): Promise<MarketHistory[]> {
  const { data } = await api.get<MarketHistory[]>('/api', {
    params: { q: `getHistory: ${typeId};${locationId}` },
  })
  return data
}

export async function getMarketPrice(
  typeId: number,
  locationId = 1,
): Promise<MarketPrice> {
  const { data } = await api.get<MarketPrice>('/api', {
    params: { q: `getMarketPrice: ${typeId};${locationId}` },
  })
  return data
}

export async function refreshMarketPrice(
  typeId: number,
  locationId = 1,
): Promise<MarketPrice> {
  const { data } = await api.post<MarketPrice>('/api', null, {
    params: { q: `refreshMarketPrice: ${typeId};${locationId}` },
  })
  return data
}

export async function placeBuyOrder(
  typeId: number,
  locationId: number,
  price: number,
  quantity: number,
  token: string,
): Promise<PlaceBuyOrderResponse> {
  const { data } = await api.post<PlaceBuyOrderResponse>('/api', null, {
    params: {
      q: `placeBuyOrder: ${typeId};${locationId};${price};${quantity};${token}`,
    },
  })
  return data
}

export async function cancelOrder(
  orderId: number,
  token: string,
): Promise<CancelOrderResponse> {
  const { data } = await api.post<CancelOrderResponse>('/api', null, {
    params: { q: `cancelOrder: ${orderId};${token}` },
  })
  return data
}

/** quantity is the new remaining size. */
export async function editOrder(
  orderId: number,
  price: number,
  quantity: number,
  token: string,
): Promise<EditOrderResponse> {
  const { data } = await api.post<EditOrderResponse>('/api', null, {
    params: { q: `editOrder: ${orderId};${price};${quantity};${token}` },
  })
  return data
}

export async function listMyOrders(token: string): Promise<MarketOrder[]> {
  const { data } = await api.get<MarketOrder[]>('/api', {
    params: { q: `listMyOrders: ${token}` },
  })
  return data
}

export async function listMyTransactions(token: string): Promise<MyTransaction[]> {
  const { data } = await api.get<MyTransaction[]>('/api', {
    params: { q: `listMyTransactions: ${token}` },
  })
  return data
}

/** All of the user's buy orders (with typeName). */
export async function getMyOrders(token: string): Promise<MyOrder[]> {
  const { data } = await api.get<MyOrder[]>('/api', {
    params: { q: `getMyOrders: ${token}` },
  })
  return data
}

export async function placeSellOrder(
  typeId: number,
  locationId: number,
  sourceId: number,
  price: number,
  quantity: number,
  externalListingId: string,
  ingestionToken: string,
): Promise<PlaceSellOrderResponse> {
  const { data } = await api.post<PlaceSellOrderResponse>('/api', null, {
    params: {
      q: `placeSellOrder: ${typeId};${locationId};${sourceId};${price};${quantity};${externalListingId};${ingestionToken}`,
    },
  })
  return data
}

export async function editSellOrder(
  sourceId: number,
  externalListingId: string,
  price: number,
  quantity: number,
  ingestionToken: string,
): Promise<EditSellOrderResponse> {
  const { data } = await api.post<EditSellOrderResponse>('/api', null, {
    params: {
      q: `editSellOrder: ${sourceId};${externalListingId};${price};${quantity};${ingestionToken}`,
    },
  })
  return data
}

export async function cancelSellOrder(
  sourceId: number,
  externalListingId: string,
  ingestionToken: string,
): Promise<CancelSellOrderResponse> {
  const { data } = await api.post<CancelSellOrderResponse>('/api', null, {
    params: {
      q: `cancelSellOrder: ${sourceId};${externalListingId};${ingestionToken}`,
    },
  })
  return data
}

export async function cancelStaleSellOrders(
  sourceId: number,
  updatedBeforeEpochMs: number,
  ingestionToken: string,
): Promise<CancelStaleSellOrdersResponse> {
  const { data } = await api.post<CancelStaleSellOrdersResponse>('/api', null, {
    params: {
      q: `cancelStaleSellOrders: ${sourceId};${updatedBeforeEpochMs};${ingestionToken}`,
    },
  })
  return data
}
