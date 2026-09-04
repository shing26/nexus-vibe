import React from 'react';

interface ShimmerButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  children: React.ReactNode;
  className?: string;
}

export const ShimmerButton: React.FC<ShimmerButtonProps> = ({
  children,
  className = '',
  ...props
}) => {
  return (
    <button
      {...props}
      className={`relative inline-flex items-center justify-center overflow-hidden rounded-lg bg-gradient-to-r from-vibe-cyan to-vibe-purple p-[1px] font-mono text-xs font-medium text-white transition-transform active:scale-95 hover:scale-[1.02] ${className}`}
    >
      <span className="absolute inset-0 bg-[linear-gradient(110deg,transparent,25%,rgba(255,255,255,0.4),45%,transparent)] bg-[length:200%_100%] animate-shimmer" />
      <span className="relative flex items-center gap-1.5 rounded-[7px] bg-vibe-bg/90 px-3.5 py-1.5 backdrop-blur-sm transition-colors hover:bg-transparent">
        {children}
      </span>
    </button>
  );
};
