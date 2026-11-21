import { defineConfig, devices } from "@playwright/test"

// Browser regression suite for the public booking pages, run against a
// production build talking to e2e/mock-api.mjs. NEXT_PUBLIC_* values are baked
// in at build time, so the app is rebuilt against the mock API first.
const APP_PORT = 3100
const MOCK_API_PORT = 8099

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  reporter: [["list"]],
  use: {
    baseURL: `http://127.0.0.1:${APP_PORT}`,
    locale: "ro-RO",
    timezoneId: "Europe/Bucharest",
    trace: "retain-on-failure",
  },
  projects: [
    { name: "webkit-iphone-13", use: { ...devices["iPhone 13"] } },
    { name: "webkit-iphone-se", use: { ...devices["iPhone SE"] } },
    { name: "chromium-mobile", use: { ...devices["Pixel 7"] } },
    { name: "chromium-desktop", use: { ...devices["Desktop Chrome"] } },
  ],
  webServer: [
    {
      command: "node e2e/mock-api.mjs",
      url: `http://127.0.0.1:${MOCK_API_PORT}/api/v1/public/properties`,
      env: { MOCK_API_PORT: String(MOCK_API_PORT) },
      reuseExistingServer: !process.env.CI,
    },
    {
      command: `npm run build && npx next start -H 127.0.0.1 -p ${APP_PORT}`,
      url: `http://127.0.0.1:${APP_PORT}/book`,
      env: { NEXT_PUBLIC_API_BASE_URL: `http://127.0.0.1:${MOCK_API_PORT}/api/v1` },
      reuseExistingServer: !process.env.CI,
      timeout: 300_000,
    },
  ],
})
