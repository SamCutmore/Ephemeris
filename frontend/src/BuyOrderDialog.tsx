import { useEffect, useId, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { placeBuyOrder } from './api/clients'
import { handleFailure } from './api/errors'
import type { PlaceBuyOrderResponse } from './api/types'

export type BuyOrderDefaults = {
  price?: string
  quantity?: string
}

type BuyOrderDialogProps = {
  typeId: number
  typeName: string
  locationId: number
  locationName: string
  token: string
  defaults?: BuyOrderDefaults
  onClose: () => void
  onPlaced: (result: PlaceBuyOrderResponse) => void
  onExpired: () => void
}

export default function BuyOrderDialog({
  typeId,
  typeName,
  locationId,
  locationName,
  token,
  defaults,
  onClose,
  onPlaced,
  onExpired,
}: BuyOrderDialogProps) {
  const [price, setPrice] = useState(defaults?.price ?? '')
  const [quantity, setQuantity] = useState(defaults?.quantity ?? '')
  const [expiry, setExpiry] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const titleId = useId()
  const dialogRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const firstField = dialogRef.current?.querySelector<HTMLElement>(
      'input:not([readonly]), button',
    )
    firstField?.focus()

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && !busy) onClose()
    }

    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      previous?.focus()
    }
  }, [busy, onClose])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return

    setError('')
    const unitPrice = Number(price)
    const units = Number(quantity)

    if (
      !Number.isFinite(unitPrice) ||
      unitPrice <= 0 ||
      !Number.isSafeInteger(units) ||
      units <= 0 ||
      units > 2147483647
    ) {
      setError('Enter a positive price and a whole quantity.')
      return
    }

    if (expiry) {
      const when = new Date(expiry)
      if (Number.isNaN(when.getTime()) || when.getTime() <= Date.now()) {
        setError('Expiry must be a future date and time.')
        return
      }
    }

    setBusy(true)
    try {
      const result = await placeBuyOrder(
        typeId,
        locationId,
        unitPrice,
        units,
        token,
      )
      onPlaced(result)
    } catch (failure) {
      setError(handleFailure(failure, onExpired))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div
      className="modal-backdrop"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !busy) onClose()
      }}
    >
      <div
        ref={dialogRef}
        className="modal-dialog buy-order-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
      >
        <header className="modal-dialog-header">
          <h2 id={titleId}>Place a buy order</h2>
          <button
            type="button"
            className="modal-close"
            disabled={busy}
            aria-label="Close"
            onClick={onClose}
          >
            ×
          </button>
        </header>

        <form className="message-form buy-order-form" onSubmit={submit}>
          <dl className="buy-order-meta">
            <div className="buy-order-meta-row">
              <dt>Item</dt>
              <dd>{typeName}</dd>
            </div>
            <div className="buy-order-meta-row">
              <dt>Location</dt>
              <dd>{locationName}</dd>
            </div>
          </dl>

          <div className="buy-order-fields">
            <label>
              Maximum unit price (£)
              <input
                required
                type="number"
                min="0.01"
                step="0.01"
                value={price}
                onChange={(event) => setPrice(event.target.value)}
              />
            </label>

            <label>
              Quantity
              <input
                required
                type="number"
                min="1"
                max="2147483647"
                step="1"
                value={quantity}
                onChange={(event) => setQuantity(event.target.value)}
              />
            </label>
          </div>

          <label className="buy-order-expiry">
            Expiry
            <input
              type="datetime-local"
              value={expiry}
              onChange={(event) => setExpiry(event.target.value)}
            />
          </label>

          <div className="modal-dialog-actions">
            <button type="button" disabled={busy} onClick={onClose}>
              Cancel
            </button>
            <button type="submit" disabled={busy}>
              {busy ? 'Placing order…' : 'Place buy order'}
            </button>
          </div>
        </form>

        {error ? (
          <p className="error" role="alert">
            {error}
          </p>
        ) : null}
      </div>
    </div>
  )
}
