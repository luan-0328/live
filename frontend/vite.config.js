import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  build: {
    rollupOptions: { output: { manualChunks(id) {
      if (!id.includes('node_modules')) return
      if (id.includes('element-plus') || id.includes('@element-plus')) return 'element-plus'
      if (id.includes('vue') || id.includes('pinia')) return 'vue-vendor'
      return 'vendor'
    } } }
  },
  server: {
    host: '0.0.0.0',
    port: 3000,
    allowedHosts: ['hdeaba5a.natappfree.cc'],
    proxy: {
      '/api/v1': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/api\/v1/, '')
      }
    }
  }
})
