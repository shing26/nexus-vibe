import { useToastStore, type ToastType } from '../stores/toastStore';
import { X, CheckCircle, AlertCircle, Info } from 'lucide-react';

const iconMap: Record<ToastType, React.ReactNode> = {
  success: <CheckCircle className="w-4 h-4 text-vibe-emerald" />,
  error: <AlertCircle className="w-4 h-4 text-red-400" />,
  info: <Info className="w-4 h-4 text-vibe-cyan" />,
};

const bgMap: Record<ToastType, string> = {
  success: 'border-vibe-emerald/30',
  error: 'border-red-500/30',
  info: 'border-vibe-cyan/30',
};

export default function ToastContainer() {
  const { toasts, removeToast } = useToastStore();
  if (toasts.length === 0) return null;
  return (
    <div className="fixed top-4 right-4 z-[100] flex flex-col gap-2 max-w-sm">
      {toasts.map((t) => (
        <div
          key={t.id}
          className={'flex items-center gap-2 px-4 py-3 rounded-xl bg-black/80 border backdrop-blur-sm text-white text-xs font-mono shadow-lg animate-in slide-in-from-right ' + bgMap[t.type]}
        >
          {iconMap[t.type]}
          <span className="flex-1">{t.message}</span>
          <button onClick={() => removeToast(t.id)} className="text-slate-500 hover:text-white transition-colors">
            <X className="w-3 h-3" />
          </button>
        </div>
      ))}
    </div>
  );
}
