import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ErrorBoundary from './ErrorBoundary';
import { resetCrashReporterForTest } from '../api/crashReporter';

const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 200 }));

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock);
  resetCrashReporterForTest();
  // React logs the caught error itself; keep the test output readable.
  vi.spyOn(console, 'error').mockImplementation(() => {});
});

function Bomb(): never {
  throw new Error('boundary boom');
}

describe('ErrorBoundary', () => {
  it('reports a caught render error with the component stack', async () => {
    render(
      <MemoryRouter>
        <ErrorBoundary>
          <Bomb />
        </ErrorBoundary>
      </MemoryRouter>,
    );
    await new Promise((r) => setTimeout(r, 0));
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const body = JSON.parse(fetchMock.mock.calls[0][1].body);
    expect(body.kind).toBe('boundary');
    expect(body.message).toBe('boundary boom');
    expect(body.componentStack).toContain('Bomb');
  });
});
