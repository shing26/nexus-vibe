import { BookOpen, Bug, Cpu, Megaphone, Palette, Terminal, Zap } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import i18next from 'i18next';

/**
 * The one list of channels the frontend owns.
 *
 * <p>Sidebar, the home channel grid and the admin dashboard all read this module and
 * resolve the display name through the dictionary (`channel:names.<slug>.label`), so
 * there is no second place a channel's display name can drift. The backend remains the
 * source of truth for ids and membership; this file only maps slugs to icons and
 * dictionary keys for the surfaces that render before (or without) a channels request.</p>
 */
export interface ChannelMeta {
  slug: string;
  labelKey: string;
  descKey: string;
  icon: LucideIcon;
}

export const CHANNELS: ChannelMeta[] = [
  { slug: 'announcements', labelKey: 'channel:names.announcements.label', descKey: 'channel:names.announcements.desc', icon: Megaphone },
  { slug: 'prompts', labelKey: 'channel:names.prompts.label', descKey: 'channel:names.prompts.desc', icon: Terminal },
  { slug: 'showcase', labelKey: 'channel:names.showcase.label', descKey: 'channel:names.showcase.desc', icon: Palette },
  { slug: 'agents', labelKey: 'channel:names.agents.label', descKey: 'channel:names.agents.desc', icon: Cpu },
  { slug: 'vibe-coding', labelKey: 'channel:names.vibe-coding.label', descKey: 'channel:names.vibe-coding.desc', icon: Zap },
  { slug: 'debug', labelKey: 'channel:names.debug.label', descKey: 'channel:names.debug.desc', icon: Bug },
  { slug: 'resources', labelKey: 'channel:names.resources.label', descKey: 'channel:names.resources.desc', icon: BookOpen },
];

/**
 * Display name for a channel slug, resolved through the dictionary. A slug this
 * module does not know returns an empty string, so the caller can fall back to
 * the name the backend sent — the API, not this file, is the source of truth for
 * channels that were added after it was written.
 */
export function channelLabel(slug: string | null | undefined): string {
  if (!slug) return '';
  const known = CHANNELS.find((c) => c.slug === slug);
  return known ? i18next.t(known.labelKey) : '';
}
