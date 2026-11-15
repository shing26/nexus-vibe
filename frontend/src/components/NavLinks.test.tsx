import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import Footer from './Footer';
import { apiClient } from '../api/client';

/**
 * The navigation links exist for one reader: someone deciding whether to believe the
 * project's claims. Each one has to open the real evidence in a new tab, with the
 * attributes that keep the opener away from the opened page.
 *
 * <p>The navbar half of this file was retired with the v2 shell: the top bar now
 * carries no navigation beyond search and the user menu, so the evidence links have
 * their only home in the footer — which is what the remaining assertions cover.</p>
 */

vi.mock('../api/client', () => ({
  apiClient: { get: vi.fn() },
}));

const get = vi.mocked(apiClient.get);

function renderWithProviders(ui: ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('nav evidence links', () => {
  beforeEach(() => {
    get.mockReset();
    get.mockResolvedValue({ data: { code: 200, data: [] } });
  });

  it('carries the ADR index and load-test links in the footer', () => {
    renderWithProviders(<Footer />);

    const adr = screen.getByTitle('Architecture decision records');
    expect(adr).toHaveAttribute('href', 'https://github.com/shing26/nexus-vibe/tree/master/docs/adr');
    expect(adr).toHaveAttribute('target', '_blank');
    expect(adr).toHaveAttribute('rel', 'noopener noreferrer');

    const research = screen.getByTitle('Async pool load test report');
    expect(research).toHaveAttribute(
      'href',
      'https://github.com/shing26/nexus-vibe/blob/master/docs/research/async-pool-loadtest.md',
    );
  });

  it('carries the same links in the footer, plus the failure drill', () => {
    renderWithProviders(<Footer />);

    expect(screen.getByTitle('Architecture decision records')).toBeInTheDocument();
    expect(screen.getByTitle('Async pool load test report')).toBeInTheDocument();

    const drill = screen.getByTitle('Failure drill, including what it cannot prove');
    expect(drill).toHaveAttribute(
      'href',
      'https://github.com/shing26/nexus-vibe/blob/master/docs/research/observability-drill-2026-09.md',
    );
  });
});
