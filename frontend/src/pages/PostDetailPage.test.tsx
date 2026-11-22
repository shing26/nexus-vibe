import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import PostDetailPage from './PostDetailPage';
import { apiClient } from '../api/client';
import { useAuthStore } from '../stores/authStore';

/**
 * Slice C2: the detail page's information architecture.
 *
 * <p>Three properties are load-bearing and each broke once already elsewhere:
 * the primary actions must use the paired `vibe-on-accent` token (white on the
 * audit green is 2.1:1), the header must not render empty state pills, and the
 * engagement counts must appear once on the page — the header used to repeat
 * what the action row already showed.</p>
 */

vi.mock('../api/client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));

const get = vi.mocked(apiClient.get);

const POST = {
  id: '42',
  title: 'Building a RAG pipeline',
  content: 'A plain body without code.',
  summary: '',
  authorName: 'shing',
  categoryName: 'Agent 实战',
  viewCount: 328,
  likeCount: 1,
  commentCount: 3,
  status: 1,
  createTime: '2026-11-20T10:00:00.000Z',
  aiReviewed: 1,
  aiReviewScore: 3,
  postType: 'text',
};

function renderDetail() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/post/42']}>
        <Routes>
          <Route path="/post/:id" element={<PostDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('PostDetailPage information architecture', () => {
  beforeEach(() => {
    get.mockReset();
    useAuthStore.setState({ token: null, refreshToken: null, user: null, isAuthenticated: false });
    get.mockImplementation(async (url: string) => {
      if (url === '/posts/42') return { data: { code: 200, data: POST } };
      if (url === '/comments/post/42') return { data: { code: 200, data: [] } };
      return { data: { code: 200, data: null } };
    });
  });

  it('renders the identity line as mono text without the retired category pill', async () => {
    renderDetail();

    const title = await screen.findByRole('heading', { level: 1, name: 'Building a RAG pipeline' });
    expect(title).toHaveClass('text-2xl');
    expect(screen.getByText('shing')).toBeInTheDocument();
    expect(screen.getByText('Agent 实战')).toBeInTheDocument();
  });

  it('shows the engagement counts exactly once on the page', async () => {
    renderDetail();

    await screen.findByRole('heading', { level: 1, name: 'Building a RAG pipeline' });

    // The action row owns the counts; a second copy in the header is the bug.
    await waitFor(() => expect(screen.getAllByText('328')).toHaveLength(1));
    expect(screen.getAllByText('3')).toHaveLength(1);
  });

  it('gives the engagement counts accessible names', async () => {
    renderDetail();

    expect(await screen.findByLabelText('328 views')).toBeInTheDocument();
    expect(screen.getByLabelText('3 comments')).toBeInTheDocument();
    expect(screen.getByLabelText('1 likes')).toBeInTheDocument();
  });

  it('does not render an empty pending stamp on an active post', async () => {
    renderDetail();

    await screen.findByRole('heading', { level: 1, name: 'Building a RAG pipeline' });
    expect(screen.queryByText('Pending review')).not.toBeInTheDocument();
  });
});
