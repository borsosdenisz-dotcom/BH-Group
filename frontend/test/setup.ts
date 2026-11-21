import "@testing-library/jest-dom/vitest"
import { cleanup } from "@testing-library/react"
import { afterEach, vi } from "vitest"
import "./mocks/next-navigation"

afterEach(() => {
  cleanup()
})

// jsdom doesn't implement matchMedia — framer-motion's useReducedMotion()
// (used by property-booking-card) calls it unconditionally.
if (!window.matchMedia) {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }))
}

// jsdom doesn't implement ResizeObserver — needed by the base-ui
// Select/Dialog popups used in pricing-admin-section and the owner
// thread dialog.
if (!window.ResizeObserver) {
  window.ResizeObserver = class ResizeObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}

// Framer Motion's whileInView features need this browser API. Tests using
// marketing sections only need observation to be available, not viewport
// geometry simulation.
if (!globalThis.IntersectionObserver) {
  globalThis.IntersectionObserver = class IntersectionObserverMock {
    readonly root = null
    readonly rootMargin = "0px"
    readonly thresholds = [0]
    disconnect() {}
    observe() {}
    takeRecords(): IntersectionObserverEntry[] { return [] }
    unobserve() {}
  } as unknown as typeof IntersectionObserver
}

// jsdom throws "not implemented" for these — base-ui's popup positioning
// and pointer-capture logic call them during open/close.
if (!Element.prototype.hasPointerCapture) {
  Element.prototype.hasPointerCapture = () => false
}
if (!Element.prototype.setPointerCapture) {
  Element.prototype.setPointerCapture = () => {}
}
if (!Element.prototype.releasePointerCapture) {
  Element.prototype.releasePointerCapture = () => {}
}
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {}
}
