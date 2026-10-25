import type { TicketStatus } from '@/types/ticket.types';
import { cn } from '@/lib/utils/cn';

interface StatusBadgeProps {
  status: TicketStatus | string;
  className?: string;
}

export function StatusBadge({ status, className }: StatusBadgeProps) {
  const normalized = status?.toUpperCase();

  const getStyle = () => {
    switch (normalized) {
      case 'OPEN':
        return 'bg-sky-50 text-sky-700 border-sky-200/80';
      case 'IN_PROGRESS':
        return 'bg-amber-50 text-amber-700 border-amber-200/80';
      case 'RESOLVED':
        return 'bg-emerald-50 text-emerald-700 border-emerald-200/80';
      case 'CLOSED':
        return 'bg-slate-100 text-slate-600 border-slate-200';
      default:
        return 'bg-slate-100 text-slate-700 border-slate-200';
    }
  };

  const getLabel = () => {
    switch (normalized) {
      case 'IN_PROGRESS':
        return 'In Progress';
      default:
        return normalized.charAt(0) + normalized.slice(1).toLowerCase();
    }
  };

  return (
    <span
      className={cn(
        'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border shadow-xs select-none',
        getStyle(),
        className
      )}
    >
      <span className="w-1.5 h-1.5 rounded-full bg-current opacity-80 mr-1.5" />
      {getLabel()}
    </span>
  );
}
