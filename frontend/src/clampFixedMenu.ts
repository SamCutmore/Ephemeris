/** Keep a fixed-position menu inside the viewport; flip upward when it would overflow. */
export function clampFixedMenu(
  el: HTMLElement,
  anchorX: number,
  anchorY: number,
): void {
  const rect = el.getBoundingClientRect()
  const pad = 8
  let x = anchorX
  let y = anchorY

  if (x + rect.width > window.innerWidth - pad) {
    x = Math.max(pad, window.innerWidth - rect.width - pad)
  }
  if (y + rect.height > window.innerHeight - pad) {
    y = Math.max(pad, anchorY - rect.height)
  }

  el.style.left = `${x}px`
  el.style.top = `${y}px`
}
