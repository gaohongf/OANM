import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import { App as AntdApp, ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'

import App from './App.tsx'
import './index.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {/*
      ConfigProvider 统一中文文案（表格分页、日期、空状态等都有默认英文文案）。
      AntdApp 提供 message/modal/notification 的上下文版本 —— 用静态的
      `message.error(...)` 在 antd 5+ 里拿不到主题与国际化，会有告警。
    */}
    <ConfigProvider locale={zhCN} theme={{ token: { borderRadius: 6 } }}>
      <AntdApp>
        {/*
          BrowserRouter 放在最外层：登录状态与菜单是"渲染路由数组"的输入，
          所以路由本身必须是静态的壳，动态的部分由 useRoutes 的数组决定。
        */}
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </AntdApp>
    </ConfigProvider>
  </StrictMode>,
)
