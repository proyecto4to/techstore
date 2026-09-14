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
const AdminVehiculosPage = lazy(() => import('./pages/AdminVehiculosPage'))
const AdminSolicitudesPage = lazy(() => import('./pages/AdminSolicitudesPage'))
const AdminSeguridadPage = lazy(() => import('./pages/AdminSeguridadPage'))
const CuentaPage = lazy(() => import('./pages/CuentaPage'))
const NotificacionesPage = lazy(() => import('./pages/NotificacionesPage'))
const PasswordRecoveryPage = lazy(() => import('./pages/PasswordRecoveryPage'))
const PasswordResetPage = lazy(() =>
  import('./pages/PasswordRecoveryPage').then((module) => ({ default: module.PasswordResetPage })),
)

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
              <Route path="/password/forgot" element={<PasswordRecoveryPage />} />
              <Route path="/password/reset" element={<PasswordResetPage />} />
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
                  <ProtectedRoute roles={['Admin', 'Cajero']} modulo="PEDIDOS">
                    <AdminPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/productos"
                element={
                  <ProtectedRoute rol="Admin" modulo="PRODUCTOS">
                    <AdminProductosPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/facturas"
                element={
                  <ProtectedRoute roles={['Admin', 'Cajero']} modulo="FACTURAS">
                    <AdminFacturasPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/caja"
                element={
                  <ProtectedRoute roles={['Admin', 'Cajero']} modulo="CAJA">
                    <AdminCajaPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/usuarios"
                element={
                  <ProtectedRoute rol="Admin" modulo="USUARIOS">
                    <AdminUsuariosPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/compras"
                element={
                  <ProtectedRoute roles={['Admin', 'Cajero']} modulo="COMPRAS">
                    <AdminComprasPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/venta"
                element={
                  <ProtectedRoute roles={['Admin', 'Cajero']} modulo="FACTURAS">
                    <AdminVentaDirectaPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/empresa"
                element={
                  <ProtectedRoute rol="Admin" modulo="EMPRESA">
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
                path="/notificaciones"
                element={
                  <ProtectedRoute>
                    <NotificacionesPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/reportes"
                element={
                  <ProtectedRoute roles={['Admin', 'Cajero']} modulo="REPORTES">
                    <AdminReportesPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/contabilidad"
                element={
                  <ProtectedRoute rol="Admin" modulo="CONTABILIDAD">
                    <AdminContabilidadPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/correos"
                element={
                  <ProtectedRoute roles={['Admin', 'Cajero']} modulo="CONFIGURACION">
                    <AdminCorreosPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/mensajes"
                element={
                  <ProtectedRoute roles={['Admin', 'Cajero']} modulo="CONVERSACIONES">
                    <AdminMensajesPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/auditoria"
                element={
                  <ProtectedRoute rol="Admin" modulo="AUDITORIA">
                    <AdminAuditoriaPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/vehiculos"
                element={
                  <ProtectedRoute rol="Admin" modulo="VEHICULOS">
                    <AdminVehiculosPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/solicitudes"
                element={
                  <ProtectedRoute rol="Cajero" modulo="REPARTOS">
                    <AdminSolicitudesPage />
                  </ProtectedRoute>
                }
              />
              {/*
                Seguridad tiene doble cerrojo a propósito: el rol Administrador
                y el permiso del módulo. Y ninguno de los dos es la protección
                real: quien escriba la URL a mano llega igual a una pantalla que
                no va a poder usar, porque cada endpoint que hay detrás exige lo
                mismo en el servidor.
              */}
              <Route
                path="/admin/seguridad"
                element={
                  <ProtectedRoute rol="Admin" modulo="SEGURIDAD">
                    <AdminSeguridadPage />
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
