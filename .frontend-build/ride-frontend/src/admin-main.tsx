import React from 'react'
import ReactDOM from 'react-dom/client'

import { AppProviders } from './bootstrap/AppProviders'
import { createDependencies } from './bootstrap/createDependencies'
import './i18n'
import './index.css'
import { initTelegramWebAppUI } from './lib/telegram'
import AdminDashboard from './pages/admin/AdminDashboard'

initTelegramWebAppUI()
const deps = createDependencies()

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AppProviders deps={deps}>
      <AdminDashboard />
    </AppProviders>
  </React.StrictMode>,
)
