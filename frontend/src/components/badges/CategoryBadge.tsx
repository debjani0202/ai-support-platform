import type { TicketCategory } from '@/types/ticket.types';
import { cn } from '@/lib/utils/cn';

interface CategoryBadgeProps {
  category?: TicketCategory | string | null;
  className?: string;
}

export function CategoryBadge({ category, className }: CategoryBadgeProps) {
  if (!category) {
    return (
      <span
        className={cn(
          'inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-slate-100 text-slate-500 italic border border-slate-200 select-none',
          className
        )}
      >
        Uncategorized
      </span>
    );
  }

  const formatCategory = (cat: string) => {
    return cat
      .split('_')
      .map((w) => w.charAt(0) + w.slice(1).toLowerCase())
      .join(' ');
  };

  return (
    <span
      className={cn(
        'inline-flex items-center px-2.5 py-0.5 rounded-md text-xs font-medium bg-indigo-50/70 text-indigo-700 border border-indigo-100/80 select-none',
        className
      )}
    >
      {formatCategory(category)}
    </span>
  );
}
