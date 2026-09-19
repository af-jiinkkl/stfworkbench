import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // 把 /api 转发到后端，让浏览器眼里所有请求都是同源的：
    //   - 开发期不需要后端开 CORS
    //   - 前端代码里不用写死 http://localhost:8080，生产环境同样走 /api 前缀，
    //     由 Nginx 转发（见 docs/需求说明.md §8 的部署约束：不硬编码地址）
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
