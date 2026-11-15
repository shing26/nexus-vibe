import { ArrowLeft } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

interface BackButtonProps {
  label?: string;
  className?: string;
}

export default function BackButton({ label = 'Back', className = '' }: BackButtonProps) {
  const navigate = useNavigate();

  const goBack = () => {
    const idx = (window.history.state as { idx?: number } | null)?.idx;
    if (typeof idx === 'number' && idx > 0) {
      navigate(-1);
    } else {
      navigate('/');
    }
  };

  return (
    <button
      type="button"
      onClick={goBack}
      aria-label="Go back"
      className={
        'inline-flex items-center gap-1.5 rounded-md border border-vibe-border bg-vibe-card px-2.5 py-1.5 text-xs text-vibe-muted transition-colors hover:border-vibe-accent/40 hover:text-vibe-text ' +
        className
      }
    >
      <ArrowLeft className="h-3.5 w-3.5" />
      <span>{label}</span>
    </button>
  );
}
