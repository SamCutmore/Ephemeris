import { useEffect, useId, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { cancelOrder } from './api/clients'
import { handleFailure } from './api/errors'

const currency = new Intl.NumberFormat('en-GB', {
  style: 'currency',
  currency: 'GBP',
})

type CancelBuyOrderDialogProps = {
  orderId: number
  typeName: string
  locationName?: string | null
  price: number
  quantity: number
  token: string
  onClose: () => void
  onCancelled: () => void
  onExpired: () => void
}

export default function CancelBuyOrderDialog({
  orderId,
  typeName,
  locationName = null,
  price,
  quantity,
  token,
  onClose,
  onCancelled,
  onExpired,
}: CancelBuyOrderDialogProps) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const titleId = useId()
  const dialogRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const firstField = dialogRef.current?.querySelector<HTMLElement>(
      'button:not(.modal-close)',
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
    setBusy(true)
    try {
      await cancelOrder(orderId, token)
      onCancelled()
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
          <h2 id={titleId}>Cancel buy order #{orderId}</h2>
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
              <dt>Price</dt>
              <dd>{currency.format(price)}</dd>
            </div>
            <div className="buy-order-meta-row">
              <dt>Quantity</dt>
              <dd>{quantity}</dd>
            </div>
          </dl>

          <p className="buy-order-confirm-copy">
            Cancel this order? Remaining quantity will no longer be filled.
          </p>

          <div className="modal-dialog-actions">
            <button type="button" disabled={busy} onClick={onClose}>
              Keep order
            </button>
            <button type="submit" disabled={busy}>
              {busy ? 'Cancelling…' : 'Cancel order'}
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
