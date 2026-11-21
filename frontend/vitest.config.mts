import path from "node:path"
import react from "@vitejs/plugin-react"
import { configDefaults, defineConfig } from "vitest/config"

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(import.meta.dirname, "."),
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./test/setup.ts"],
    // Playwright specs; run with `npm run test:e2e`.
    exclude: [...configDefaults.exclude, "e2e/**"],
    css: false,
    pool: "threads",
  },
})
