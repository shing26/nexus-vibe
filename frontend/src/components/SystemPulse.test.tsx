import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import SystemPulse from './SystemPulse';
import { apiClient } from '../api/client';

vi.mock('../api/client', () => ({
  apiClient: { get: vi.fn() },
}));

const get = vi.mocked(apiClient.get);

function renderPulse() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <SystemPulse />
    </QueryClientProvider>,
  );
}

describe('SystemPulse', () => {
  beforeEach(() => {
    get.mockReset();
  });

  it('shows the real degraded capabilities, verbatim from the server', async () => {
    get.mockResolvedValue({
      data: {
        code: 200,
        data: [
          { key: 'search', impact: 'Search results may be incomplete.' },
          { key: 'ai-review', impact: 'New posts may take longer to appear.' },
        ],
      },
    });

    renderPulse();

    expect(await screen.findByText('Search results may be incomplete.')).toBeInTheDocument();
    expect(screen.getByText('New posts may take longer to appear.')).toBeInTheDocument();
    expect(screen.getByText('API serving')).toBeInTheDocument();
  });

  it('reports all-normal when the capability list is empty', async () => {
    get.mockResolvedValue({ data: { code: 200, data: [] } });

    renderPulse();

    expect(await screen.findByText('All capabilities normal')).toBeInTheDocument();
  });

  it('reports a broken API rather than inventing a healthy state', async () => {
    get.mockRejectedValue(new Error('network'));

    renderPulse();

    await waitFor(
      () => expect(screen.getByTestId('system-pulse')).toHaveTextContent('Cannot reach the server'),
      { timeout: 3000 },
    );
  });
});
