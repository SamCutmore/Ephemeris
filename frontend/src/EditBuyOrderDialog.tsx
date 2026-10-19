import { useEffect, useId, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { editOrder } from './api/clients'
import { handleFailure } from './api/errors'
import type { EditOrderResponse, MarketOrder } from './api/types'

type EditBuyOrderDialogProps = {
  order: Pick<
    MarketOrder,
    'orderId' | 'price' | 'quantity' | 'quantityInitial' | 'status'
  >
  typeName: string
  locationName?: string | null
  token: string
  onClose: () => void
  onSaved: (result: EditOrderResponse) => void
  onExpired: () => void
}

export default function EditBuyOrderDialog({
  order,
  typeName,
  locationName = null,
  token,
  onClose,
  onSaved,
  onExpired,
}: EditBuyOrderDialogProps) {
  const [price, setPrice] = useState(String(order.price))
  const [quantity, setQuantity] = useState(String(order.quantity))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const titleId = useId()
  const dialogRef = useRef<HTMLDivElement>(null)
  const filled = order.quantityInitial - order.quantity

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
      setError('Enter a positive price and a whole remaining quantity.')
      return
    }

    setBusy(true)
    try {
      const result = await editOrder(order.orderId, unitPrice, units, token)
      onSaved(result)
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
          <h2 id={titleId}>Edit buy order #{order.orderId}</h2>
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
            {locationName ? (
              <div className="buy-order-meta-row">
                <dt>Location</dt>
                <dd>{locationName}</dd>
              </div>
            ) : null}
            <div className="buy-order-meta-row">
              <dt>Already filled</dt>
              <dd>{filled}</dd>
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
              Remaining quantity
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

          <div className="modal-dialog-actions">
            <button type="button" disabled={busy} onClick={onClose}>
              Cancel
            </button>
            <button type="submit" disabled={busy}>
              {busy ? 'Saving…' : 'Save changes'}
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
