import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vitest/config'

// The app's own import paths, so a component under test reaches lib as the app does.
export default defineConfig({
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
})
