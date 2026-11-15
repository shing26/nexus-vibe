interface SkeletonProps {
  className?: string;
}

/**
 * The one loading placeholder. A plain pulse block on the surface color — the
 * five copied shimmer gradients died with the forge pivot, and any surface that
 * is loading composes this instead of growing its own skeleton.
 */
export default function Skeleton({ className = '' }: SkeletonProps) {
  return (
    <div
      aria-hidden="true"
      className={'animate-pulse motion-reduce:animate-none rounded bg-vibe-surface ' + className}
    />
  );
}
