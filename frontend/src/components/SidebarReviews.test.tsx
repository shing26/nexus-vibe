import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import i18next from 'i18next';
import SidebarReviews from './SidebarReviews';
import { apiClient } from '../api/client';

vi.mock('../api/client', () => ({
  apiClient: { get: vi.fn() },
}));

const get = vi.mocked(apiClient.get);

const rows = [
  {
    id: '9001',
    postId: '100',
    postTitle: 'React Component Generator',
    reviewer: 'code-review-agent',
    severity: 'low',
    isApproved: 1,
    createdAt: '2026-11-23T00:56:36',
  },
  {
    id: '9002',
    postId: '101',
    postTitle: 'Tailwind UI Prompt Architect',
    reviewer: 'code-review-agent',
    severity: 'medium',
    isApproved: 1,
    createdAt: '2026-11-22T22:56:36',
  },
  {
    id: '9003',
    postId: '1',
    postTitle: 'Building a RAG pipeline',
    reviewer: 'safety-check-agent',
    severity: 'critical',
    isApproved: 0,
    createdAt: '2026-11-22T20:56:36',
  },
];

function renderReviews() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <SidebarReviews />
    </QueryClientProvider>,
  );
}

describe('SidebarReviews', () => {
  beforeEach(() => {
    get.mockReset();
  });

  it('renders the ticker rows with their real time and verdict', async () => {
    get.mockResolvedValue({ data: { code: 200, data: rows } });

    renderReviews();

    const feed = await screen.findByTestId('sidebar-reviews');
    const items = await within(feed).findAllByRole('listitem');
    expect(items).toHaveLength(3);
    expect(within(items[0]).getByText('00:56')).toBeInTheDocument();
    expect(within(items[0]).getByText('React Component Generator')).toBeInTheDocument();
    expect(
      within(items[0]).getByText(i18next.t('common:sidebarReviews.approved')),
    ).toBeInTheDocument();
    expect(
      within(items[2]).getByText(i18next.t('common:sidebarReviews.flagged')),
    ).toBeInTheDocument();
  });

  it('shows the empty sentence instead of inventing a row', async () => {
    get.mockResolvedValue({ data: { code: 200, data: [] } });

    renderReviews();

    expect(await screen.findByText(i18next.t('common:sidebarReviews.empty'))).toBeInTheDocument();
    expect(screen.queryAllByRole('listitem')).toHaveLength(0);
  });

  it('shows the error sentence when the ticker request fails', async () => {
    get.mockRejectedValue(new Error('ticker down'));

    renderReviews();

    expect(await screen.findByText(i18next.t('common:sidebarReviews.error'))).toBeInTheDocument();
  });
});
