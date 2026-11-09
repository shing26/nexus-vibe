import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import DegradationBanner from './DegradationBanner';
import { apiClient } from '../api/client';

/**
 * What the banner owes the reader: only the consequence, only when there is one, and a
 * dismissal that covers what was on screen rather than the whole feature forever.
 *
 * <p>The two failure modes worth pinning are both silent in production. A banner that renders
 * with nothing to say trains people to ignore it; a dismissal stored as one boolean hides a
 * second, different degradation for the rest of the session, which is exactly the moment the
 * banner exists for.</p>
 */

vi.mock('../api/client', () => ({
  apiClient: { get: vi.fn() },
}));

const get = vi.mocked(apiClient.get);

const ADR_0007 =
  'https://github.com/shing26/nexus-vibe/blob/master/docs/adr/0007-degraded-status-is-not-unhealthy.md';

function respond(capabilities: Array<{ key: string; impact: string }>) {
  get.mockResolvedValue({ data: { code: 200, message: 'ok', data: capabilities } });
}

function renderBanner() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <DegradationBanner />
    </QueryClientProvider>,
  );
}

describe('DegradationBanner', () => {
  beforeEach(() => {
    get.mockReset();
    sessionStorage.clear();
  });

  it('renders nothing when the endpoint has nothing to report', async () => {
    respond([]);

    const { container } = renderBanner();

    await waitFor(() => expect(get).toHaveBeenCalledWith('/status/capabilities'));
    expect(container).toBeEmptyDOMElement();
  });

  it("names the consequence in the user's words and links ADR-0007", async () => {
    respond([{ key: 'search', impact: 'Search results may be incomplete.' }]);

    renderBanner();

    expect(await screen.findByText(/search results may be incomplete/i)).toBeInTheDocument();
    const link = screen.getByRole('link', { name: /why this happens/i });
    expect(link).toHaveAttribute('href', ADR_0007);
    expect(link.getAttribute('rel')).toContain('noopener');
  });

  it('stays absent when the request fails, rather than showing a broken banner', async () => {
    get.mockRejectedValue(new Error('network'));

    const { container } = renderBanner();

    await waitFor(() => expect(get).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it('dismisses for the session and records only the capability that was shown', async () => {
    const user = userEvent.setup();
    respond([{ key: 'search', impact: 'Search results may be incomplete.' }]);

    renderBanner();

    await user.click(await screen.findByRole('button', { name: /dismiss status notice/i }));

    expect(screen.queryByText(/search results may be incomplete/i)).not.toBeInTheDocument();
    expect(JSON.parse(sessionStorage.getItem('nexus.degraded-capabilities.dismissed') ?? '[]')).toEqual([
      'search',
    ]);
  });

  it('shows a capability that was not in the dismissed set', async () => {
    sessionStorage.setItem('nexus.degraded-capabilities.dismissed', JSON.stringify(['search']));
    respond([
      { key: 'search', impact: 'Search results may be incomplete.' },
      {
        key: 'ai-review',
        impact: 'New posts may take longer to appear while automated review is unavailable.',
      },
    ]);

    renderBanner();

    expect(await screen.findByText(/new posts may take longer/i)).toBeInTheDocument();
    expect(screen.queryByText(/search results may be incomplete/i)).not.toBeInTheDocument();
  });

  it('keeps a dismissed capability hidden across a remount in the same session', async () => {
    sessionStorage.setItem('nexus.degraded-capabilities.dismissed', JSON.stringify(['search']));
    respond([{ key: 'search', impact: 'Search results may be incomplete.' }]);

    const { container } = renderBanner();

    await waitFor(() => expect(get).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });
});
