 interface PaginationProps {
   page: number;
   pages: number;
   onPageChange: (page: number) => void;
 }
 
 export default function Pagination({ page, pages, onPageChange }: PaginationProps) {
   if (pages <= 1) return null;
 
   const getPageNumbers = () => {
     const delta = 2;
     const range: (number | string)[] = [];
     const start = Math.max(2, page - delta);
     const end = Math.min(pages - 1, page + delta);
 
     range.push(1);
     if (start > 2) range.push('…');
     for (let i = start; i <= end; i++) range.push(i);
     if (end < pages - 1) range.push('…');
     if (pages > 1) range.push(pages);
 
     return range;
   };
 
 return (
   <div className="mt-6 flex items-center justify-center gap-1 text-xs tabular-nums text-vibe-muted">
      {page > 1 && (
        <button onClick={() => onPageChange(page - 1)} className="rounded px-2 py-1 transition-colors hover:text-vibe-accent">
         prev
        </button>
      )}
      {getPageNumbers().map((p, i) =>
        typeof p === 'string' ? (
          <span key={`ellipsis-${i}`} className="px-1 text-vibe-faint">{p}</span>
        ) : (
          <button
            key={p}
            onClick={() => onPageChange(p)}
            className={p === page ? 'rounded bg-vibe-accent/10 px-2 py-1 font-semibold text-vibe-accent' : 'rounded px-2 py-1 transition-colors hover:text-vibe-accent'}
          >
            {p}
          </button>
        )
      )}
      {page < pages && (
        <button onClick={() => onPageChange(page + 1)} className="rounded px-2 py-1 transition-colors hover:text-vibe-accent">
         next
        </button>
      )}
     </div>
   );
 }
