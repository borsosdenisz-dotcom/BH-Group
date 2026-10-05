/**
 * Full-page navigation to another origin (Stripe's hosted Checkout). Kept
 * in its own module so tests can assert the redirect without jsdom trying
 * to navigate.
 */
export function redirectToExternal(url: string) {
  window.location.assign(url)
}
