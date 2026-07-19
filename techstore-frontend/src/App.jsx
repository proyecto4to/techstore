import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import Navbar from './components/common/Navbar'
import ProtectedRoute from './components/common/ProtectedRoute'
import CatalogoPage from './pages/CatalogoPage'
import ProductoDetallePage from './pages/ProductoDetallePage'
import LoginPage from './pages/LoginPage'
import AdminPage from './pages/AdminPage'
import CartPage from './pages/CartPage'
import PedidosPage from './pages/PedidosPage'
import AdminProductosPage from './pages/AdminProductosPage'
import AdminReportesPage from './pages/AdminReportesPage'
import AdminFacturasPage from './pages/AdminFacturasPage'
import AdminCajaPage from './pages/AdminCajaPage'
import RegisterPage from './pages/RegisterPage'
import './App.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false },
  },
})

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <div className="min-h-screen">
          <Navbar />
          <main>
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
                path="/admin"
                element={
                  <ProtectedRoute rol="Admin">
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
                  <ProtectedRoute rol="Admin">
                    <AdminFacturasPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/caja"
                element={
                  <ProtectedRoute rol="Admin">
                    <AdminCajaPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/admin/reportes"
                element={
                  <ProtectedRoute rol="Admin">
                    <AdminReportesPage />
                  </ProtectedRoute>
                }
              />
            </Routes>
          </main>
        </div>
      </BrowserRouter>
    </QueryClientProvider>
  )
}

export default App
