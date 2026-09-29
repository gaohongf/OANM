import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  build: {
    /*
      提高单 chunk 体积告警阈值。

      这是有意的取舍而不是把问题藏起来: 业务页面已经全部按路由懒加载了
      （见 router/registry.ts 的 import.meta.glob），剩下这个 500 kB 出头的
      chunk 是 antd + react 本身 —— 后台管理界面用到 Table/Form/Modal 时就必然
      有这么大，拆它没有收益（它们本来就该被长期缓存）。
      真正该警惕的是"业务代码进了主包"，那种情况体积会继续涨、这个阈值拦不住，
      所以调高它不会让真正的问题溜过去。
    */
    chunkSizeWarningLimit: 800,
  },
  server: {
    port: 5173,
    proxy: {
      // 开发期把 /api 代理到网关, 而不是直接代理到某个服务。
      // 这样可以顺带验证网关的路由与转发 —— 直连服务的话, 网关配错了也发现不了。
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
