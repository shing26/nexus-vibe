import React from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle } from 'lucide-react';
import { reportCrash } from '../api/crashReporter';
import i18next from 'i18next';

interface Props {
  children: React.ReactNode;
  /** One line on the fallback screen naming the section that failed, e.g. "the admin console". */
  section?: string;
}

interface State { hasError: boolean; error?: Error; }

/**
 * The boundary's fallback is intentionally not a full-page layout: the inner
 * boundaries mount inside `MainLayout`'s `<Outlet />` or the admin panel's
 * content column, so a route crash keeps the navigation, the theme and the
 * toast container alive — a broken page is a section-level failure, not an
 * app-level one (ADR-0016 argument). The outermost boundary around the whole
 * router stays the only one that can render full-screen, for a crash in the
 * layout itself.
 */
export default class ErrorBoundary extends React.Component<Props, State> {
  constructor(props: Props) { super(props); this.state = { hasError: false }; }
  static getDerivedStateFromError(error: Error) { return { hasError: true, error }; }
  /**
   * FE-1: a caught render error now also leaves the browser, tagged with the
   * component stack — the one breadcrumb `error.stack` cannot give. The global
   * handlers in `crashReporter.ts` cover what no boundary sees; this covers
   * what one does. Every boundary in the tree reports: dedupe and window
   * caps are the crash reporter's job, not the caller's.
   */
  componentDidCatch(error: Error, info: React.ErrorInfo) {
    void reportCrash({
      kind: 'boundary',
      message: error.message,
      stack: error.stack,
      componentStack: info.componentStack ?? undefined,
    });
  }
  render() {
    if (this.state.hasError) {
      return (
        <div className={this.props.section ? 'py-16 text-center' : 'max-w-3xl mx-auto px-4 py-24 text-center'}>
          <AlertTriangle className={this.props.section ? 'h-10 w-10 text-vibe-danger/50 mx-auto mb-4' : 'h-16 w-16 text-vibe-danger/50 mx-auto mb-4'} />
          <h1 className="mb-2 text-xl font-semibold text-vibe-text">{i18next.t('common:errorBoundary.title')}</h1>
          <p className="mx-auto mb-2 max-w-md text-xs text-vibe-muted">{this.state.error?.message}</p>
          {this.props.section && (
            <p className="mb-2 text-xs text-vibe-muted">
              {i18next.t('common:errorBoundary.sectionNote', { section: this.props.section })}
            </p>
          )}
          <div className="flex items-center justify-center gap-3">
            <Link to="/" className="inline-flex items-center gap-2 rounded-md bg-vibe-accent px-4 py-2 text-xs font-medium text-white transition-colors hover:opacity-90" onClick={() => this.setState({ hasError: false })}>
              {i18next.t('common:backHome')}
            </Link>
            <button
              type="button"
              onClick={() => {
                this.setState({ hasError: false });
                window.location.reload();
              }}
              className="inline-flex items-center gap-2 rounded-md border border-vibe-border px-4 py-2 text-xs font-medium text-vibe-muted transition-colors hover:border-vibe-accent/40 hover:text-vibe-accent focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-vibe-accent"
            >
              {i18next.t('common:errorBoundary.retry')}
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
