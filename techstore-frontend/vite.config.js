import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  test: {
    // Los procesos fork pueden agotar el tiempo de arranque en Windows/OneDrive.
    // Un worker de hilos mantiene la suite determinista y estable también en CI.
    pool: 'threads',
    maxWorkers: 1
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '/api')
      },
      '/uploads': {
        target: 'http://localhost:8090',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false
  }
})
