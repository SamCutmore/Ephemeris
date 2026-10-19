import { useEffect } from 'react'

type NoticePopupProps = {
  message: string
  onClose: () => void
  /** Auto-dismiss after this many ms. Set 0 to keep until dismissed. */
  durationMs?: number
}

export default function NoticePopup({
  message,
  onClose,
  durationMs = 4500,
}: NoticePopupProps) {
  useEffect(() => {
    if (!message || durationMs <= 0) return

    const timer = window.setTimeout(onClose, durationMs)
    return () => window.clearTimeout(timer)
  }, [message, durationMs, onClose])

  useEffect(() => {
    if (!message) return

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }

    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [message, onClose])

  if (!message) return null

  return (
    <div className="notice-popup-host" aria-live="polite">
      <div className="notice-popup" role="alert">
        <p>{message}</p>
        <button
          type="button"
          className="modal-close"
          aria-label="Dismiss"
          onClick={onClose}
        >
          ×
        </button>
      </div>
    </div>
  )
}
