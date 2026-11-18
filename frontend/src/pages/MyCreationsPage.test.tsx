import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import MyCreationsPage from './MyCreationsPage';
import { apiClient } from '../api/client';
import { useAuthStore } from '../stores/authStore';

vi.mock('../api/client', () => ({
  apiClient: { get: vi.fn() },
}));

const get = vi.mocked(apiClient.get);
const DRAFT_KEY = 'nexus_vibe_drafts';

const post = (id: string, overrides: Partial<Record<string, unknown>> = {}) => ({
  id,
  title: 'Post ' + id,
  summary: 'a short preview',
  content: 'body',
  postType: 'post',
  forkedFromId: null,
  aiReviewed: 1,
  aiReviewScore: 0.7,
  createTime: '2026-11-01T00:00:00',
  ...overrides,
});

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <MyCreationsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('MyCreationsPage', () => {
  beforeEach(() => {
    get.mockReset();
    localStorage.removeItem(DRAFT_KEY);
    useAuthStore.getState().setAuth('token', { id: '42', username: 'shing' }, 'refresh');
  });

  afterEach(() => {
    useAuthStore.getState().logout();
    localStorage.removeItem(DRAFT_KEY);
  });

  it('derives kinds from real post fields and shows counts that match the cards', async () => {
    get.mockResolvedValue({
      data: {
        code: 200,
        data: {
          list: [
            post('1'),
            post('2', { postType: 'prompt' }),
            post('3', { postType: 'prompt', forkedFromId: '100' }),
          ],
        },
      },
    });
    localStorage.setItem(DRAFT_KEY, JSON.stringify([
      { id: 7, title: 'Local draft', content: 'draft body', postType: 'post', updatedAt: '2026-11-02T00:00:00' },
    ]));

    renderPage();

    const tablist = await screen.findByRole('tablist');
    const tabs = within(tablist).getAllByRole('tab');
    // All=4 (3 server rows + 1 draft), post=1, prompt=1, fork=1, draft=1.
    expect(tabs.map((tab) => tab.textContent)).toEqual(['All4', 'Posts1', 'Prompts1', 'Forks1', 'Drafts1']);
    expect(screen.getByRole('link', { name: /Local draft/ })).toBeInTheDocument();
    expect(screen.getByText('on this device only')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Post 3/ })).toHaveAttribute('href', '/post/3');
  });

  it('filters by tab and links a draft to the existing edit route', async () => {
    const user = userEvent.setup();
    get.mockResolvedValue({ data: { code: 200, data: { list: [post('1'), post('2', { postType: 'prompt' })] } } });
    localStorage.setItem(DRAFT_KEY, JSON.stringify([
      { id: 9, title: 'Draft nine', content: 'b', postType: 'prompt', updatedAt: '2026-11-03T00:00:00' },
    ]));

    renderPage();

    await user.click(await screen.findByRole('tab', { name: /Drafts/ }));
    const draftLink = screen.getByRole('link', { name: /Draft nine/ });
    expect(draftLink).toHaveAttribute('href', '/post/new?draft=9');
    expect(screen.queryByRole('link', { name: /Post 1/ })).not.toBeInTheDocument();
  });

  it('shows a prose empty state for an empty category', async () => {
    const user = userEvent.setup();
    get.mockResolvedValue({ data: { code: 200, data: { list: [post('1')] } } });

    renderPage();

    await user.click(await screen.findByRole('tab', { name: /Forks/ }));
    expect(screen.getByText(/Nothing forked from the plaza yet/)).toBeInTheDocument();
  });

  it('shows an error panel with retry when the post list fails', async () => {
    get.mockRejectedValue(new Error('network'));

    renderPage();

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Retry/i })).toBeInTheDocument();
  });
});
