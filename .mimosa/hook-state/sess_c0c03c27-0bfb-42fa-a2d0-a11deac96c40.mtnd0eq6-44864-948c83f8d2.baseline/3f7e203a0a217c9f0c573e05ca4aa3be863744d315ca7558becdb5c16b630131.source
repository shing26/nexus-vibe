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
   <div className="flex items-center justify-center gap-1 mt-6 text-xs font-mono text-slate-500">
      {page > 1 && (
        <button onClick={() => onPageChange(page - 1)} className="hover:text-vibe-cyan transition-colors px-2 py-1 active:scale-[0.97]">
         上一页
        </button>
      )}
      {getPageNumbers().map((p, i) =>
        typeof p === 'string' ? (
          <span key={`ellipsis-${i}`} className="px-1 text-slate-600">{p}</span>
        ) : (
          <button
            key={p}
            onClick={() => onPageChange(p)}
            className={p === page ? 'font-bold text-slate-100 px-2 py-1 active:scale-[0.97]' : 'hover:text-vibe-cyan transition-colors px-2 py-1 active:scale-[0.97]'}
          >
            {p}
          </button>
        )
      )}
      {page < pages && (
        <button onClick={() => onPageChange(page + 1)} className="hover:text-vibe-cyan transition-colors px-2 py-1 active:scale-[0.97]">
         下一页
        </button>
      )}
     </div>
   );
 }
