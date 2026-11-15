import type { ReactNode } from 'react';

interface ExternalLinkProps {
  href: string;
  /** Spoken and hover text; names what is on the other side, because "ADRs" alone does not. */
  title: string;
  children: ReactNode;
}

/**
 * One anchor shape for leaving the SPA, used by the navbar and the footer.
 *
 * <p>The security attributes are the point of the component existing: `noopener` keeps the
 * opened page off `window.opener`, and writing them once here means no surface can add an
 * outbound link and forget them.</p>
 */
export const ExternalLink: React.FC<ExternalLinkProps> = ({ href, title, children }) => (
  <a
    href={href}
    target="_blank"
    rel="noopener noreferrer"
    title={title}
    className={
      'inline-flex items-center gap-1 text-xs text-vibe-muted transition-colors hover:text-vibe-accent ' +
      'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-vibe-accent'
    }
  >
    {children}
  </a>
);
