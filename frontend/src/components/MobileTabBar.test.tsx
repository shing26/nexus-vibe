import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import MobileTabBar from './MobileTabBar';
import { useAuthStore } from '../stores/authStore';

/**
 * What a thumb can reach on a phone.
 *
 * <p>jsdom has no layout and applies no media queries, so this cannot assert that the
 * bar is invisible at 1280px or that content clears it at 390px — that belongs to a
 * real browser pass. What it can pin, and what broke silently before, is the set of
 * destinations and where each one points: Messages, Settings, Drafts and the user's
 * own profile had no pressable route onto them below `sm` at all.</p>
 */

function renderBar(initialPath = '/') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="*" element={<MobileTabBar />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('MobileTabBar', () => {
  it('offers the four one-tap destinations the header hides below sm', () => {
    renderBar();

    for (const [label, href] of [
      ['Home', '/'],
      ['Search', '/search'],
      ['Post', '/post/new'],
      ['Messages', '/user/messages'],
    ] as const) {
      const link = screen.getByRole('link', { name: label });
      expect(link).toHaveAttribute('href', href);
    }
  });

  it('hides itself from a desktop viewport in CSS, and says so in the markup', () => {
    const { container } = renderBar();

    // The only part of "lg and below only" that jsdom can check: the class is the
    // mechanism, and if it is dropped the bar starts covering the footer on a laptop.
    expect(container.firstElementChild).toHaveClass('lg:hidden');
  });

  it('carries settings, drafts, profile and logout on the fifth tab', async () => {
    const user = userEvent.setup();
    useAuthStore.getState().setAuth('t', { id: '7', username: 'shing' }, 'r');

    renderBar();
    const me = screen.getByRole('button', { name: 'Me' });
    expect(me).toHaveAttribute('aria-expanded', 'false');

    await user.click(me);
    expect(me).toHaveAttribute('aria-expanded', 'true');

    const menu = screen.getByRole('menu', { name: 'Account' });
    expect(menu).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: /My profile/i })).toHaveAttribute('href', '/user/7');
    expect(screen.getByRole('menuitem', { name: /Drafts/i })).toHaveAttribute('href', '/drafts');
    expect(screen.getByRole('menuitem', { name: /Settings/i })).toHaveAttribute('href', '/user/settings');

    await user.click(screen.getByRole('menuitem', { name: /Log out/i }));
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });

  it('lights the tab that owns the current route', () => {
    renderBar('/user/messages');

    expect(screen.getByRole('link', { name: 'Messages' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Home' })).not.toHaveAttribute('aria-current');
  });

  it('sends a signed-out visitor to the login page rather than to a profile that does not exist', async () => {
    const user = userEvent.setup();
    useAuthStore.getState().logout();

    renderBar();
    await user.click(screen.getByRole('button', { name: 'Me' }));

    // There is no id to build /user/:id from, so the row has to be a way back in.
    expect(screen.getByRole('menuitem', { name: /My profile/i })).toHaveAttribute('href', '/login');
  });
});
