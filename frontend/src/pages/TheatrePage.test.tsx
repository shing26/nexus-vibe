import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import TheatrePage, { TheatreEntry } from './TheatrePage';
import { apiClient } from '../api/client';

vi.stubGlobal('matchMedia', (query: string) => ({
  matches: true,
  media: query,
  onchange: null,
  addListener: vi.fn(),
  removeListener: vi.fn(),
  addEventListener: vi.fn(),
  removeEventListener: vi.fn(),
  dispatchEvent: vi.fn(),
}));

vi.mock('../api/client', () => ({
  apiClient: { get: vi.fn() },
}));

const get = vi.mocked(apiClient.get);

const steps = (extra: Array<{ id: string; title?: string; mode: string; outcome: string }> = []) => [
  { id: 'input', title: 'Input accepted', detail: 'detail-input', mode: 'REAL', outcome: 'OK', payload: 'code', offsetMs: '0' },
  { id: 'prompt', title: 'Prompt isolated', detail: 'detail-prompt', mode: 'REAL', outcome: 'OK', payload: null, offsetMs: '400' },
  { id: 'model', title: 'Model returns broken JSON', detail: 'detail-model', mode: 'INJECTED', outcome: 'WARN', payload: 'raw', offsetMs: '900' },
  { id: 'repair', title: 'Repaired JSON accepted', detail: 'detail-repair', mode: 'REAL', outcome: 'OK', payload: '{}', offsetMs: '1300' },
  ...extra,
];

const scenario = (overrides: Partial<Record<string, unknown>> = {}) => ({
  id: 'repaired-json',
  title: 'Repair the broken JSON',
  subtitle: 'The model returns fenced, truncated JSON.',
  lesson: 'Repair-and-retry is a real ladder.',
  sampleCode: 'int x = 1;',
  promptPreview: '---BEGIN CODE---\nint x = 1;',
  rawOutput: 'raw',
  repairedJson: '{}',
  score: 4,
  severity: 'high',
  approved: false,
  modelAttempts: 2,
  durationMs: '36',
  failClosed: false,
  steps: steps(),
  adr: 'docs/adr/0012-the-review-pipeline-is-pluggable-at-two-seams.md',
  ...overrides,
});

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <TheatrePage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function stepBadges() {
  return screen.getAllByTestId('mode-badge').map((badge) => badge.getAttribute('data-mode'));
}

describe('TheatrePage', () => {
  beforeEach(() => {
    get.mockReset();
  });

  it('renders the disclosure and all four scenarios on success', async () => {
    get.mockResolvedValue({
      data: {
        code: 200,
        data: [
          scenario(),
          scenario({ id: 'empty-placeholder', title: 'Reject the empty answer' }),
          scenario({ id: 'prompt-injection', title: 'Contain the instruction' }),
          scenario({ id: 'model-down', title: 'Survive the dead model', failClosed: true, score: null, severity: null, approved: null }),
        ],
      },
    });

    renderPage();

    expect(await screen.findByTestId('theatre-disclosure')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Repair the broken JSON/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Reject the empty answer/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Contain the instruction/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Survive the dead model/ })).toBeInTheDocument();
  });

  it('labels every revealed step with its honesty mode', async () => {
    get.mockResolvedValue({ data: { code: 200, data: [scenario()] } });

    renderPage();

    await screen.findByText('Repaired JSON accepted');
    expect(stepBadges()).toContain('REAL');
    expect(stepBadges()).toContain('INJECTED');
  });

  it('switches scenarios and shows the fail-closed state', async () => {
    const user = userEvent.setup();
    get.mockResolvedValue({
      data: {
        code: 200,
        data: [
          scenario(),
          scenario({
            id: 'model-down',
            title: 'Survive the dead model',
            failClosed: true,
            score: null,
            severity: null,
            approved: null,
            steps: steps([{ id: 'fail-closed', title: 'No verdict, no publication', mode: 'POLICY', outcome: 'FAIL' }]),
          }),
        ],
      },
    });

    renderPage();

    await user.click(await screen.findByRole('button', { name: /Survive the dead model/ }));
    expect(await screen.findByTestId('fail-closed-chip')).toBeInTheDocument();
    expect(stepBadges()).toContain('POLICY');
  });

  it('shows an error panel with a retry control on failure', async () => {
    get.mockRejectedValue(new Error('network down'));

    renderPage();

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
  });

  it('renders step payloads in expandable details', async () => {
    get.mockResolvedValue({ data: { code: 200, data: [scenario()] } });

    const { container } = renderPage();
    await screen.findByText('Repaired JSON accepted');
    expect(container.querySelectorAll('details summary').length).toBeGreaterThanOrEqual(2);
  });
});

describe('TheatreEntry', () => {
  beforeEach(() => {
    get.mockReset();
  });

  it('links to the theatre route', () => {
    render(
      <MemoryRouter>
        <TheatreEntry label="Enter the Chaos Theatre" desc="Watch AI fail and recover" />
      </MemoryRouter>,
    );

    expect(screen.getByTestId('theatre-entry')).toHaveAttribute('to', '/theatre');
    expect(screen.getByText('Enter the Chaos Theatre')).toBeInTheDocument();
  });
});
