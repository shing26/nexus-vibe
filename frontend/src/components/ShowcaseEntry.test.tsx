import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import ShowcaseEntry from './ShowcaseEntry';
import { apiClient } from '../api/client';

/**
 * The landing-page entry exists for exactly one reader: someone who has not signed in.
 *
 * <p>Two properties matter and both are easy to lose. It must render nothing at all
 * when the deployment has no showcase post, because a broken or empty card is worse
 * than no card. And when it does render, it must link to the post id the backend
 * handed it rather than to a hard-coded one — the seed id is configuration, not a
 * constant this component may assume.</p>
 */

vi.mock('../api/client', () => ({
  apiClient: { get: vi.fn() },
}));

const get = vi.mocked(apiClient.get);

function renderEntry() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <ShowcaseEntry />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('ShowcaseEntry', () => {
  beforeEach(() => {
    get.mockReset();
  });

  it('renders nothing when the deployment has no showcase post', async () => {
    get.mockResolvedValue({ data: { code: 200, data: null } });

    const { container } = renderEntry();

    await waitFor(() => expect(get).toHaveBeenCalledWith('/showcase'));
    expect(container).toBeEmptyDOMElement();
  });

  it('links to the post the backend named, with its real score', async () => {
    get.mockResolvedValue({
      data: {
        code: 200,
        // A string, as the API sends it: Jackson serialises Long ids as strings, and a
        // JavaScript number literal would lose the last digits before the test ever ran.
        data: { postId: '900000000000000001', title: 'Showcase: unbounded cache', aiReviewScore: 3 },
      },
    });

    renderEntry();

    const link = await screen.findByRole('link');
    expect(link).toHaveAttribute('href', '/post/900000000000000001');
    expect(link).toHaveTextContent('Showcase: unbounded cache');
    expect(link).toHaveTextContent('3/10');
  });

  it('describes the review as a recording rather than claiming it is live', async () => {
    get.mockResolvedValue({
      data: {
        code: 200,
        data: { postId: '900000000000000001', title: 'Showcase: unbounded cache', aiReviewScore: 3 },
      },
    });

    renderEntry();

    // The seeded review is a stored row copied out of a real run, so the wording may say
    // "recording" and may not say the pipeline produced it just now. This exists because an
    // earlier version of this card read "every field came out of the live pipeline".
    const link = await screen.findByRole('link');
    expect(link).toHaveTextContent(/recorded run/i);
    expect(link).not.toHaveTextContent(/live pipeline/i);
  });

  it('stays absent when the request fails, rather than showing a broken card', async () => {
    get.mockRejectedValue(new Error('network'));

    const { container } = renderEntry();

    await waitFor(() => expect(get).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });
});
