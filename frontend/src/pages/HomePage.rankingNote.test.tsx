import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import HomePage from './HomePage';
import { apiClient } from '../api/client';

vi.mock('../api/client', () => ({
  apiClient: { get: vi.fn() },
}));

const get = vi.mocked(apiClient.get);

function renderHome() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('HomePage ranking note', () => {
  beforeEach(() => {
    get.mockReset();
    get.mockResolvedValue({ data: { code: 200, data: { list: [], total: '0', page: 1, size: 10, pages: 0 } } });
  });

  it('explains the hot order without a hover, and follows tab changes', async () => {
    const user = userEvent.setup();
    renderHome();

    const note = await screen.findByTestId('ranking-note');
    expect(note).toHaveTextContent(/heat/i);

    await user.click(screen.getByRole('button', { name: /Latest/i }));
    expect(screen.getByTestId('ranking-note')).toHaveTextContent(/newest first/i);
  });
});
