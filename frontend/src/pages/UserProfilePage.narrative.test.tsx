import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import UserProfilePage from './UserProfilePage';
import { apiClient } from '../api/client';

vi.mock('../api/client', () => ({
  apiClient: { get: vi.fn() },
}));

const get = vi.mocked(apiClient.get);

function renderProfile() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/user/42']}>
        <Routes>
          <Route path="/user/:id" element={<UserProfilePage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('UserProfilePage narrative stats', () => {
  beforeEach(() => {
    get.mockReset();
    get.mockImplementation(async (url: string) => {
      if (url.includes('/summary')) {
        return {
          data: {
            code: 200,
            data: {
              id: 42,
              username: 'tester',
              nickname: 'Tester',
              stats: {
                posts: 2,
                comments: 5,
                likesReceived: 12,
                avgAiScore: 0.85,
                forks: 3,
                versions: 4,
              },
              recentActivity: [],
            },
          },
        };
      }
      if (url.includes('/agent-logs/user')) return { data: { code: 200, data: [] } };
      return {
        data: {
          code: 200,
          data: {
            list: [
              { aiReviewed: 1 },
              { aiReviewed: 1 },
            ],
          },
        },
      };
    });
  });

  it('weaves the same stats into one sentence with bold numbers', async () => {
    renderProfile();

    const narrative = await screen.findByTestId('narrative-stats');
    expect(narrative.querySelectorAll('strong').length).toBeGreaterThanOrEqual(5);
    expect(narrative).toHaveTextContent(/2/);
    expect(narrative).toHaveTextContent(/12/);
    expect(narrative).toHaveTextContent(/8\.5/);
  });
});
