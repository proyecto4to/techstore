import { lazy, Suspense } from 'react'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import Navbar from './components/common/Navbar'
import RealtimeSync from './components/common/RealtimeSync'
import ProtectedRoute from './components/common/ProtectedRoute'
import './App.css'

const CatalogoPage = lazy(() => import('./pages/CatalogoPage'))
const ProductoDetallePage = lazy(() => import('./pages/ProductoDetallePage'))
const LoginPage = lazy(() => import('./pages/LoginPage'))
const RegisterPage = lazy(() => import('./pages/RegisterPage'))
const CartPage = lazy(() => import('./pages/CartPage'))
const PedidosPage = lazy(() => import('./pages/PedidosPage'))
const DashboardPage = lazy(() => import('./pages/DashboardPage'))
const AdminPage = lazy(() => import('./pages/AdminPage'))
const AdminProductosPage = lazy(() => import('./pages/AdminProductosPage'))
const AdminReportesPage = lazy(() => import('./pages/AdminReportesPage'))
const AdminContabilidadPage = lazy(() => import('./pages/AdminContabilidadPage'))
const AdminFacturasPage = lazy(() => import('./pages/AdminFacturasPage'))
const AdminCajaPage = lazy(() => import('./pages/AdminCajaPage'))
const AdminUsuariosPage = lazy(() => import('./pages/AdminUsuariosPage'))
const AdminComprasPage = lazy(() => import('./pages/AdminComprasPage'))
const AdminVentaDirectaPage = lazy(() => import('./pages/AdminVentaDirectaPage'))
const AdminEmpresaPage = lazy(() => import('./pages/AdminEmpresaPage'))
const AdminCorreosPage = lazy(() => import('./pages/AdminCorreosPage'))
const AdminAuditoriaPage = lazy(() => import('./pages/AdminAuditoriaPage'))
const AdminMensajesPage = lazy(() => import('./pages/AdminMensajesPage'))
const CuentaPage = lazy(() => import('./pages/CuentaPage'))

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false },
  },
})

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <RealtimeSync />
        <div className="min-h-screen">
          <Navbar />
          <main>
            <Suspense fallback={<div className="loading" role="status">Cargando…</div>}>
              <Routes>
              <Route path="/" element={<CatalogoPage />} />
              <Route path="/productos/:id" element={<ProductoDetallePage />} />
              <Route path="/login" element={<LoginPage />} />
              <Route path="/registro" element={<RegisterPage />} />
              <Route path="/carrito" element={<CartPage />} />
              <Route
                path="/pedidos"
                element={
                  <ProtectedRoute>
                    <PedidosPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/inicio"
                element={
                  <ProtectedRoute roles={['Admin', 'Cajero']}>
                    <DashboardPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin"
                element={
                  <ProtectedRoute roles={['Admin', 'Cajero']}>
                    <AdminPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/productos"
                element={
                  <ProtectedRoute rol="Admin">
                    <AdminProductosPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/facturas"
                element={
                  <ProtectedRoute roles={['Admin', 'Cajero']}>
                    <AdminFacturasPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/caja"
                element={
                  <ProtectedRoute roles={['Admin', 'Cajero']}>
                    <AdminCajaPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/usuarios"
                element={
                  <ProtectedRoute rol="Admin">
                    <AdminUsuariosPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/compras"
                element={
                  <ProtectedRoute roles={['Admin', 'Cajero']}>
                    <AdminComprasPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/venta"
                element={
                  <ProtectedRoute roles={['Admin', 'Cajero']}>
                    <AdminVentaDirectaPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/empresa"
                element={
                  <ProtectedRoute rol="Admin">
                    <AdminEmpresaPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/cuenta"
                element={
                  <ProtectedRoute>
                    <CuentaPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/reportes"
                element={
                  <ProtectedRoute roles={['Admin', 'Cajero']}>
                    <AdminReportesPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/contabilidad"
                element={
                  <ProtectedRoute rol="Admin">
                    <AdminContabilidadPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/correos"
                element={
                  <ProtectedRoute roles={['Admin', 'Cajero']}>
                    <AdminCorreosPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/mensajes"
                element={
                  <ProtectedRoute roles={['Admin', 'Cajero']}>
                    <AdminMensajesPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/auditoria"
                element={
                  <ProtectedRoute rol="Admin">
                    <AdminAuditoriaPage />
                  </ProtectedRoute>
                }
              />
              </Routes>
            </Suspense>
          </main>
        </div>
      </BrowserRouter>
    </QueryClientProvider>
  )
}

export default App
