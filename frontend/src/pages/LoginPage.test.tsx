import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios';
import LoginPage from './LoginPage';
import { apiClient } from '../api/client';
import { useAuthStore } from '../stores/authStore';

vi.mock('../api/client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

const mockedPost = vi.mocked(apiClient.post);

function http401(message: string) {
  const config = { headers: new AxiosHeaders() } as unknown as InternalAxiosRequestConfig;
  return new AxiosError('Request failed with status code 401', AxiosError.ERR_BAD_REQUEST, config, {}, {
    status: 401,
    statusText: 'Unauthorized',
    data: { code: 401, message, data: null },
    headers: new AxiosHeaders(),
    config,
  });
}

function renderLogin() {
  return render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>,
  );
}

async function submitCredentials(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByPlaceholderText('username'), 'shing');
  await user.type(screen.getByPlaceholderText('password'), 'wrongpassword');
  await user.click(screen.getByRole('button', { name: 'Login' }));
}

describe('LoginPage', () => {
  it('shows the server sentence for a refused login', async () => {
    const user = userEvent.setup();
    mockedPost.mockRejectedValue(http401('Invalid username or password.'));

    renderLogin();
    await submitCredentials(user);

    // The string is only reachable now that /auth/login answers 401. Until round
    // six it answered 200, axios resolved, `res.data.data` was null, and the
    // catch fired on a TypeError - which is why this page always showed its own
    // fallback and never once showed what the server had said.
    await waitFor(() => expect(screen.getByText('! Invalid username or password.')).toBeInTheDocument());
    expect(screen.queryByText('! Login failed')).not.toBeInTheDocument();
  });

  it('keeps a fallback for a failure with no server message', async () => {
    const user = userEvent.setup();
    mockedPost.mockRejectedValue(new AxiosError('Network Error'));

    renderLogin();
    await submitCredentials(user);

    await waitFor(() => expect(screen.getByText('! Login failed')).toBeInTheDocument());
  });

  it('stores the session and leaves the login page on success', async () => {
    const user = userEvent.setup();
    mockedPost.mockResolvedValue({
      data: {
        code: 200,
        message: 'Login successful.',
        data: { token: 'jwt-token', refreshToken: 'jwt-refresh', userId: '1', username: 'shing', nickname: 'Shing', role: 'USER', avatar: null },
      },
      status: 200,
      statusText: 'OK',
      headers: new AxiosHeaders(),
      config: {},
    } as never);

    renderLogin();
    await submitCredentials(user);

    await waitFor(() => expect(useAuthStore.getState().token).toBe('jwt-token'));
    expect(useAuthStore.getState().isAuthenticated).toBe(true);
    expect(useAuthStore.getState().refreshToken).toBe('jwt-refresh');
    expect(screen.queryByText(/^!/)).not.toBeInTheDocument();
  });
});
