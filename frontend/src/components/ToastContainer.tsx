import { useToastStore, type ToastType } from '../stores/toastStore';
import { X, CheckCircle, AlertCircle, Info } from 'lucide-react';

const iconMap: Record<ToastType, React.ReactNode> = {
  success: <CheckCircle className="h-4 w-4 text-vibe-success" />,
  error: <AlertCircle className="h-4 w-4 text-vibe-danger" />,
  info: <Info className="h-4 w-4 text-vibe-accent" />,
};

const bgMap: Record<ToastType, string> = {
  success: 'border-vibe-success/40',
  error: 'border-vibe-danger/40',
  info: 'border-vibe-accent/40',
};

export default function ToastContainer() {
  const { toasts, removeToast } = useToastStore();
  if (toasts.length === 0) return null;
  return (
    <div className="fixed top-4 right-4 z-[100] flex flex-col gap-2 max-w-sm" aria-live="polite">
      {toasts.map((t) => (
        <div
          key={t.id}
          role={t.type === 'error' ? 'alert' : 'status'}
          className={'flex items-center gap-2 rounded-lg border bg-vibe-card px-4 py-3 text-sm text-vibe-text shadow-lg ' + bgMap[t.type]}
        >
          {iconMap[t.type]}
          <span className="flex-1">{t.message}</span>
          <button onClick={() => removeToast(t.id)} aria-label="Dismiss notification" className="text-slate-500 hover:text-white transition-colors">
            <X className="w-3 h-3" />
          </button>
        </div>
      ))}
    </div>
  );
}
