import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import Footer from './Footer';
import { apiClient } from '../api/client';

/**
 * A review log outlives the post it names: deleting the post leaves ticker rows
 * whose title lookup returns null. The first version printed the empty template
 * ("reviewed 「」"), which reads as a broken feed; the row must fall back to the
 * post id and keep the timestamp, reviewer and status.
 */

vi.mock('../api/client', () => ({
  apiClient: { get: vi.fn() },
}));

const get = vi.mocked(apiClient.get);

function renderFooter() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <Footer />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('Footer agent ticker', () => {
  beforeEach(() => {
    get.mockReset();
  });

  it('names the post id instead of empty quotes when the post was deleted', async () => {
    get.mockResolvedValue({
      data: {
        code: 200,
        data: [
          {
            id: '1',
            postId: '2121565362310975490',
            postTitle: null,
            reviewer: 'safety-check-agent',
            severity: 'none',
            isApproved: 1,
            status: 'completed',
            createdAt: '2026-11-14T19:26:42',
          },
        ],
      },
    });

    renderFooter();

    await waitFor(() =>
      expect(screen.getByText(/post #2121565362310975490/)).toBeInTheDocument(),
    );
    expect(screen.queryByText(/「」/)).not.toBeInTheDocument();
  });

  it('keeps the title when the post still exists', async () => {
    get.mockResolvedValue({
      data: {
        code: 200,
        data: [
          {
            id: '2',
            postId: '42',
            postTitle: 'Release acceptance',
            reviewer: 'code-review-agent',
            severity: 'low',
            isApproved: 1,
            status: 'completed',
            createdAt: '2026-11-14T19:26:42',
          },
        ],
      },
    });

    renderFooter();

    await waitFor(() =>
      expect(screen.getByText(/Release acceptance/)).toBeInTheDocument(),
    );
  });
});
