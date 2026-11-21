import { describe, expect, it, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import Navbar from './Navbar';
import { useAuthStore } from '../stores/authStore';

/**
 * The signed-out top bar is the widest it gets: evidence, theme, language, login
 * and register. At 390px the register label pushed the row to 410px, so the
 * button now collapses to its icon below `sm`. It must keep its accessible name
 * while doing so — an icon-only control with no name is worse than the overflow.
 */

function renderNavbar() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <Navbar onOpenDrawer={() => {}} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('Navbar signed-out cluster', () => {
  beforeEach(() => {
    useAuthStore.setState({ token: null, refreshToken: null, user: null, isAuthenticated: false });
  });

  it('keeps the register control named when its label collapses on narrow bars', () => {
    renderNavbar();

    const register = screen.getByRole('link', { name: 'Register' });
    expect(register).toHaveAttribute('href', '/register');
    expect(screen.getByText('Register')).toHaveClass('sr-only', 'sm:not-sr-only');
  });

  it('keeps the login control named', () => {
    renderNavbar();

    expect(screen.getByRole('link', { name: 'Login' })).toHaveAttribute('href', '/login');
  });
});
