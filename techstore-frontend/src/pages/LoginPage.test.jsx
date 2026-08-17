import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import LoginPage from './LoginPage'

describe('LoginPage', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('oculta las credenciales de prueba en una compilación de producción', () => {
    vi.stubEnv('DEV', false)
    vi.stubEnv('VITE_SHOW_DEMO_CREDENTIALS', 'false')

    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    )

    expect(screen.queryByText(/admin@techstore\.py/i)).not.toBeInTheDocument()
  })

  it('permite mostrarlas de forma explícita para una demo controlada', () => {
    vi.stubEnv('DEV', false)
    vi.stubEnv('VITE_SHOW_DEMO_CREDENTIALS', 'true')

    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    )

    expect(screen.getByText(/admin@techstore\.py/i)).toBeInTheDocument()
  })
})
