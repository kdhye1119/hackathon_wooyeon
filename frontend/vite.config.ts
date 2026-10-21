import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 백엔드(Spring Boot, :8080)로 API·WebSocket 프록시
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
})
