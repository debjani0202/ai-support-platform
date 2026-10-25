import type { TicketPriority } from '@/types/ticket.types';
import { cn } from '@/lib/utils/cn';

interface PriorityBadgeProps {
  priority: TicketPriority | string;
  className?: string;
}

export function PriorityBadge({ priority, className }: PriorityBadgeProps) {
  const normalized = priority?.toUpperCase();

  const getStyle = () => {
    switch (normalized) {
      case 'URGENT':
        return 'bg-rose-50 text-rose-700 border-rose-200/80';
      case 'HIGH':
        return 'bg-orange-50 text-orange-700 border-orange-200/80';
      case 'MEDIUM':
        return 'bg-blue-50 text-blue-700 border-blue-200/80';
      case 'LOW':
      default:
        return 'bg-slate-100 text-slate-700 border-slate-200';
    }
  };

  const getLabel = () => {
    return normalized.charAt(0) + normalized.slice(1).toLowerCase();
  };

  return (
    <span
      className={cn(
        'inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold border select-none',
        getStyle(),
        className
      )}
    >
      {getLabel()}
    </span>
  );
}
