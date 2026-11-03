import React from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle } from 'lucide-react';
import { reportCrash } from '../api/crashReporter';

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
          <AlertTriangle className={this.props.section ? 'w-10 h-10 text-red-400/50 mx-auto mb-4' : 'w-16 h-16 text-red-400/50 mx-auto mb-4'} />
          <h1 className="text-xl font-mono font-bold text-slate-100 mb-2">Something went wrong</h1>
          <p className="text-xs font-mono text-slate-500 mb-2 max-w-md mx-auto">{this.state.error?.message}</p>
          {this.props.section && (
            <p className="text-xs font-mono text-slate-600 mb-2">a failure inside {this.props.section}, not of the whole site</p>
          )}
          <div className="flex items-center justify-center gap-3">
            <Link to="/" className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-vibe-cyan/20 border border-vibe-cyan/30 text-vibe-cyan text-xs font-mono hover:bg-vibe-cyan/30 transition-colors" onClick={() => this.setState({ hasError: false })}>
              ← Back to Home
            </Link>
            <button
              type="button"
              onClick={() => {
                this.setState({ hasError: false });
                window.location.reload();
              }}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-lg border border-vibe-border text-slate-300 text-xs font-mono hover:border-vibe-cyan/40 hover:text-vibe-cyan transition-colors"
            >
              ↻ Retry
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
