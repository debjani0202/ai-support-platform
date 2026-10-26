import { defineConfig as defineViteConfig } from 'vite'
import { defineConfig as defineVitestConfig, mergeConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import path from 'path'

export default mergeConfig(
  defineViteConfig({
    plugins: [react()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  }),
  defineVitestConfig({
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      clearMocks: true,
    },
  })
)
