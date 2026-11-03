import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Outlet, Route, Routes } from 'react-router-dom';
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

// Rendered through a lazy boundary, a component that throws during its first
// render crashes the boundary; one that throws while already mounted only
// unmounts itself. The route-level case needs the first kind.
function RouteCrasher(): never {
  throw new Error('route boom');
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
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    const body = JSON.parse(fetchMock.mock.calls[0][1].body);
    expect(body.kind).toBe('boundary');
    expect(body.message).toBe('boundary boom');
    expect(body.componentStack).toContain('Bomb');
  });

  it('a section boundary renders the section note', () => {
    render(
      <MemoryRouter>
        <ErrorBoundary section="the channel page">
          <Bomb />
        </ErrorBoundary>
      </MemoryRouter>,
    );
    expect(screen.getByText(/a failure inside the channel page, not of the whole site/)).toBeInTheDocument();
  });
});

describe('FE-1 layering: one broken route does not unmount the rest of the app', () => {
  function Neighbour(): React.ReactElement {
    return <p>neighbour alive</p>;
  }

  function App(): React.ReactElement {
    return (
      // A minimal sketch of App.tsx's structure: an outer boundary around the
      // router, a layout with an Outlet, per-route section boundaries inside it.
      <ErrorBoundary>
        <Routes>
          <Route
            path="/"
            element={
              <div>
                <p>layout shell</p>
                <Outlet />
              </div>
            }
          >
            <Route index element={<Neighbour />} />
            <Route path="broken" element={<ErrorBoundary section="the broken page"><RouteCrasher /></ErrorBoundary>} />
          </Route>
        </Routes>
      </ErrorBoundary>
    );
  }

  it('a crash in one section boundary is contained by it and the shell stays mounted', async () => {
    render(
      <MemoryRouter initialEntries={['/broken']}>
        <App />
      </MemoryRouter>,
    );
    // The section boundary caught it: its fallback, its section note.
    expect(await screen.findByText(/a failure inside the broken page/)).toBeInTheDocument();
    // ...and the shell around it is untouched: no app-level fallback, no reload affordance churn.
    // (The index neighbour is not on screen on /broken; "the shell" is the layout.)
    expect(screen.getByText('layout shell')).toBeInTheDocument();
    // The outer boundary never fired — one report, from the section boundary.
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    expect(screen.queryByText('Something went wrong')).toBeDefined();
    expect(screen.getAllByText('Something went wrong')).toHaveLength(1);
  });
});
