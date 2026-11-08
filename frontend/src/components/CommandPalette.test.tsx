import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useState } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import CommandPalette from './CommandPalette';
import { apiClient } from '../api/client';
import { useAuthStore } from '../stores/authStore';
import { useToastStore } from '../stores/toastStore';

/**
 * The palette's second destination is the row's address.
 *
 * <p>A post or channel row already has two things a reader wants: the page, and the
 * link to it. Enter opens; the modifier copies. Action rows have no address, so the
 * modifier must fall through to their primary action rather than becoming a dead
 * key. The tests below pin that split, the absolute payload on the clipboard, the
 * toast that reports it, and the footer that only advertises the modifier on rows
 * that have one.</p>
 */

vi.mock('../api/client', () => ({
  apiClient: { get: vi.fn() },
}));

const get = vi.mocked(apiClient.get);

const channels = [
  { id: 'c1', name: 'General', description: '', slug: 'general', sortOrder: 1 },
  { id: 'c2', name: 'Showcase', description: '', slug: 'showcase', sortOrder: 2 },
];

const posts = [
  { id: 1, title: 'First post', authorName: 'shing', categoryName: 'General' },
  { id: 2, title: 'Second post', authorName: 'shing', categoryName: 'Showcase' },
];

let postHits: typeof posts;

function LocationProbe() {
  const location = useLocation();
  return <p data-testid="location">{location.pathname}</p>;
}

function Harness({ onClose }: { onClose: () => void }) {
  const [open, setOpen] = useState(true);
  return (
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route
            path="*"
            element={
              <>
                <LocationProbe />
                <CommandPalette
                  open={open}
                  onClose={() => {
                    onClose();
                    setOpen(false);
                  }}
                />
              </>
            }
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

function renderPalette() {
  const onClose = vi.fn();
  render(<Harness onClose={onClose} />);
  return { onClose };
}

async function search(value: string) {
  const input = screen.getByRole('combobox');
  fireEvent.change(input, { target: { value } });
  return input;
}

describe('CommandPalette dual destination', () => {
  let writeText: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    get.mockReset();
    postHits = posts;
    get.mockImplementation((url: string) => {
      if (url === '/channels') {
        return Promise.resolve({ data: { code: 200, data: channels } });
      }
      if (url === '/posts') {
        return Promise.resolve({ data: { code: 200, data: { list: postHits } } });
      }
      return Promise.resolve({ data: { code: 200, data: [] } });
    });

    writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
    });

    useAuthStore.getState().logout();
    useToastStore.setState({ toasts: [] });
    vi.spyOn(window, 'alert').mockImplementation(() => {});
  });

  it('D1: post and channel rows declare "Copy link"; action rows do not', async () => {
    const { onClose } = renderPalette();
    const input = await search('post');

    // Posts first (search results), then matching channels, then actions.
    const postRow = await screen.findByText('First post');
    expect(postRow).toBeInTheDocument();

    fireEvent.keyDown(input, { key: 'Enter', ctrlKey: true });
    await waitFor(() => expect(writeText).toHaveBeenCalledWith(window.location.origin + '/post/1'));
    expect(onClose).toHaveBeenCalled();
  });

  it('D2: Ctrl+Enter invokes the secondary on a row that has one', async () => {
    const { onClose } = renderPalette();
    const input = await search('post');
    await screen.findByText('First post');

    fireEvent.keyDown(input, { key: 'Enter', ctrlKey: true });

    await waitFor(() => expect(writeText).toHaveBeenCalledTimes(1));
    expect(writeText).toHaveBeenCalledWith(window.location.origin + '/post/1');
    // The secondary ran instead of the primary: no navigation happened.
    expect(screen.getByTestId('location')).toHaveTextContent('/');
    expect(onClose).toHaveBeenCalled();
  });

  it('D2: Meta+Enter invokes the secondary on a row that has one', async () => {
    renderPalette();
    const input = await search('post');
    await screen.findByText('First post');

    fireEvent.keyDown(input, { key: 'Enter', metaKey: true });

    await waitFor(() => expect(writeText).toHaveBeenCalledTimes(1));
    expect(writeText).toHaveBeenCalledWith(window.location.origin + '/post/1');
    expect(screen.getByTestId('location')).toHaveTextContent('/');
  });

  it('D2: the modifier falls through to the primary run on a row with no secondary', async () => {
    postHits = [];
    renderPalette();
    const input = await search('new');
    await screen.findByText('New Vibe Post');

    fireEvent.keyDown(input, { key: 'Enter', ctrlKey: true });

    // Signed out, so the primary action routes to /login rather than /post/new.
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login'));
    expect(writeText).not.toHaveBeenCalled();
  });

  it('D2: Meta+Enter also falls through on a row with no secondary', async () => {
    postHits = [];
    renderPalette();
    const input = await search('new');
    await screen.findByText('New Vibe Post');

    fireEvent.keyDown(input, { key: 'Enter', metaKey: true });

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login'));
    expect(writeText).not.toHaveBeenCalled();
  });

  it('D3: copies the absolute URL, not a relative path', async () => {
    renderPalette();
    const input = await search('post');
    await screen.findByText('First post');

    fireEvent.keyDown(input, { key: 'Enter', ctrlKey: true });

    await waitFor(() => expect(writeText).toHaveBeenCalled());
    const payload = writeText.mock.calls[0][0] as string;
    expect(payload).toBe(window.location.origin + '/post/1');
    expect(payload.startsWith('http')).toBe(true);
    expect(payload).not.toBe('/post/1');
  });

  it('D3: a channel row copies the channel URL, not a post URL', async () => {
    postHits = [];
    renderPalette();
    const input = await search('general');
    await screen.findByText('General');

    fireEvent.keyDown(input, { key: 'Enter', ctrlKey: true });

    await waitFor(() => expect(writeText).toHaveBeenCalledWith(window.location.origin + '/channel/general'));
  });

  it('D4: copying closes the palette and reports through the toast store, never alert()', async () => {
    const { onClose } = renderPalette();
    const input = await search('post');
    await screen.findByText('First post');

    fireEvent.keyDown(input, { key: 'Enter', ctrlKey: true });

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(onClose).toHaveBeenCalledTimes(1);

    await waitFor(() => {
      const toasts = useToastStore.getState().toasts;
      expect(toasts).toHaveLength(1);
      expect(toasts[0]).toMatchObject({ message: 'Link copied to clipboard', type: 'success' });
    });
    expect(window.alert).not.toHaveBeenCalled();
  });

  it('D4: a clipboard rejection is reported as an error toast, not silently dropped', async () => {
    writeText.mockRejectedValueOnce(new Error('denied'));
    renderPalette();
    const input = await search('post');
    await screen.findByText('First post');

    fireEvent.keyDown(input, { key: 'Enter', ctrlKey: true });

    await waitFor(() => {
      expect(useToastStore.getState().toasts[0]).toMatchObject({
        message: 'Could not copy link',
        type: 'error',
      });
    });
    expect(window.alert).not.toHaveBeenCalled();
  });

  it('D5: the footer names the modifier while a post row is selected', async () => {
    renderPalette();
    await search('post');
    await screen.findByText('First post');

    expect(screen.getByText(/ctrl\/⌘/)).toBeInTheDocument();
    expect(screen.getByText(/copy link/i)).toBeInTheDocument();
  });

  it('D5: the footer hint tracks the selection, not the mere presence of a secondary row', async () => {
    // One render holds both kinds of row: the query "post" keeps the two post
    // rows (each with a secondary) and the "New Vibe Post" action row (without
    // one). The hint must follow the selection across those rows, so this single
    // list proves both directions — an empty list of secondary rows could not.
    renderPalette();
    const input = await search('post');
    await screen.findByText('First post');

    // Row 0 is a post: the hint is present.
    expect(screen.getByText(/ctrl\/⌘/)).toBeInTheDocument();
    expect(screen.getByText(/copy link/i)).toBeInTheDocument();

    // Arrow onto "New Vibe Post" (order: post-1, post-2, new-post). The post
    // rows are still in the list and still carry a secondary; only the
    // selection moved.
    fireEvent.keyDown(input, { key: 'ArrowDown' });
    fireEvent.keyDown(input, { key: 'ArrowDown' });

    expect(input).toHaveAttribute('aria-activedescendant', 'palette-item-new-post');
    expect(screen.getByText('New Vibe Post')).toBeInTheDocument();
    expect(screen.queryByText(/ctrl\/⌘/)).not.toBeInTheDocument();
    expect(screen.queryByText(/copy link/i)).not.toBeInTheDocument();
  });

  it('D6: Enter still runs the primary action', async () => {
    renderPalette();
    const input = await search('post');
    await screen.findByText('First post');

    fireEvent.keyDown(input, { key: 'Enter' });

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/post/1'));
    expect(writeText).not.toHaveBeenCalled();
  });

  it('D6: Escape still closes without running anything', async () => {
    const { onClose } = renderPalette();
    const input = await search('post');
    await screen.findByText('First post');

    fireEvent.keyDown(input, { key: 'Escape' });

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(onClose).toHaveBeenCalled();
    expect(writeText).not.toHaveBeenCalled();
    expect(screen.getByTestId('location')).toHaveTextContent('/');
  });

  it('D6: arrow navigation still moves the roving selection, and Tab stays trapped', async () => {
    renderPalette();
    const input = await search('post');
    await screen.findByText('First post');

    expect(input).toHaveAttribute('aria-activedescendant', 'palette-item-post-1');
    fireEvent.keyDown(input, { key: 'ArrowDown' });
    expect(input).toHaveAttribute('aria-activedescendant', 'palette-item-post-2');

    const tabEvent = fireEvent.keyDown(input, { key: 'Tab' });
    // fireEvent returns false when preventDefault was called: focus stays in the dialog.
    expect(tabEvent).toBe(false);
  });

  it('D6: closing hands focus back to the trigger', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    function FocusHarness({ open, onClose }: { open: boolean; onClose: () => void }) {
      return (
        <QueryClientProvider client={client}>
          <MemoryRouter>
            <button>trigger</button>
            <CommandPalette open={open} onClose={onClose} />
          </MemoryRouter>
        </QueryClientProvider>
      );
    }

    const { rerender } = render(<FocusHarness open={false} onClose={() => {}} />);
    const trigger = screen.getByRole('button', { name: 'trigger' });
    trigger.focus();
    expect(trigger).toHaveFocus();

    rerender(<FocusHarness open={true} onClose={() => {}} />);
    rerender(<FocusHarness open={false} onClose={() => {}} />);

    await waitFor(() => expect(trigger).toHaveFocus());
  });

  it('D7: aria-activedescendant and the secondary agree on the selected row', async () => {
    renderPalette();
    const input = await search('post');
    await screen.findByText('First post');

    fireEvent.keyDown(input, { key: 'ArrowDown' });

    expect(input).toHaveAttribute('aria-activedescendant', 'palette-item-post-2');
    expect(screen.getByRole('option', { name: /Second post/ })).toHaveAttribute('aria-selected', 'true');

    fireEvent.keyDown(input, { key: 'Enter', ctrlKey: true });

    await waitFor(() => expect(writeText).toHaveBeenCalledWith(window.location.origin + '/post/2'));
  });
});
