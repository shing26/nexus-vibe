import { describe, expect, it } from 'vitest';
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios';
import { serverErrorMessage } from './serverErrorMessage';

/**
 * Which sentence a user sees when a query fails. The rule this pins is the one round
 * six bought: the server's own words win, and axios's transport summary
 * ({@code Request failed with status code 403}) never reaches the screen. That
 * string was unreachable before the status became the truth, because every failure
 * arrived as a 200 and the error branch never ran.
 */

function axiosError(status: number, data: unknown) {
  const config = { headers: new AxiosHeaders() } as unknown as InternalAxiosRequestConfig;
  return new AxiosError(`Request failed with status code ${status}`, 'ERR_BAD_REQUEST', config, {}, {
    status,
    statusText: '',
    data,
    headers: new AxiosHeaders(),
    config,
  });
}

const FALLBACK = 'An unexpected error occurred';

describe('serverErrorMessage', () => {
  it('prefers the envelope message', () => {
    const error = axiosError(403, { code: 403, message: 'Access denied. Admin privileges required.' });

    expect(serverErrorMessage(error, FALLBACK)).toBe('Access denied. Admin privileges required.');
  });

  it('refuses to show axios its own status line', () => {
    const error = axiosError(403, 'Forbidden');

    expect(serverErrorMessage(error, FALLBACK)).toBe(FALLBACK);
  });

  it('treats an empty or whitespace message as absent', () => {
    expect(serverErrorMessage(axiosError(400, { message: '   ' }), FALLBACK)).toBe(FALLBACK);
  });

  it('keeps the transport sentence when there is no response at all', () => {
    // A timeout or a dead socket has no envelope, and "Network Error" is the true
    // description of what happened; the fallback would be a worse answer.
    expect(serverErrorMessage(new AxiosError('Network Error'), FALLBACK)).toBe('Network Error');
  });

  it('passes through a non-axios error and falls back for anything else', () => {
    expect(serverErrorMessage(new Error('boom'), FALLBACK)).toBe('boom');
    expect(serverErrorMessage(undefined, FALLBACK)).toBe(FALLBACK);
    expect(serverErrorMessage('a string', FALLBACK)).toBe(FALLBACK);
  });
});
