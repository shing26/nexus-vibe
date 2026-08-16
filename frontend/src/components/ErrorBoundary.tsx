import React from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle } from 'lucide-react';

interface Props { children: React.ReactNode; }
interface State { hasError: boolean; error?: Error; }

export default class ErrorBoundary extends React.Component<Props, State> {
  constructor(props: Props) { super(props); this.state = { hasError: false }; }
  static getDerivedStateFromError(error: Error) { return { hasError: true, error }; }
  render() {
    if (this.state.hasError) {
      return (
        <div className="max-w-3xl mx-auto px-4 py-24 text-center">
          <AlertTriangle className="w-16 h-16 text-red-400/50 mx-auto mb-4" />
          <h1 className="text-xl font-mono font-bold text-slate-100 mb-2">Something went wrong</h1>
          <p className="text-xs font-mono text-slate-500 mb-2 max-w-md mx-auto">{this.state.error?.message}</p>
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
