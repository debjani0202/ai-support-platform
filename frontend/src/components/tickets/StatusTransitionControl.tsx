import { useState } from 'react';
import type { TicketResponse, TicketStatus } from '@/types/ticket.types';
import { updateTicketApi } from '@/lib/api/tickets';
import { Button } from '@/components/ui/Button';
import { ArrowRight, CheckCircle2, PlayCircle, XCircle } from 'lucide-react';
import { toast } from 'sonner';

interface StatusTransitionControlProps {
  ticket: TicketResponse;
  userRole?: string;
  onStatusUpdated: (updatedTicket: TicketResponse) => void;
}

export function StatusTransitionControl({
  ticket,
  userRole,
  onStatusUpdated,
}: StatusTransitionControlProps) {
  const [isUpdating, setIsUpdating] = useState(false);

  // Status transitions are permitted only for AGENT and ADMIN roles
  if (userRole !== 'AGENT' && userRole !== 'ADMIN') {
    return null;
  }

  // Calculate the strictly allowed forward transition based on backend state machine
  const getNextStatusDetails = (): {
    nextStatus: TicketStatus;
    label: string;
    icon: React.ReactNode;
    variant: 'primary' | 'secondary';
  } | null => {
    switch (ticket.status) {
      case 'OPEN':
        return {
          nextStatus: 'IN_PROGRESS',
          label: 'Start Work (In Progress)',
          icon: <PlayCircle className="w-4 h-4 mr-1.5" />,
          variant: 'primary',
        };
      case 'IN_PROGRESS':
        return {
          nextStatus: 'RESOLVED',
          label: 'Resolve Ticket',
          icon: <CheckCircle2 className="w-4 h-4 mr-1.5" />,
          variant: 'primary',
        };
      case 'RESOLVED':
        return {
          nextStatus: 'CLOSED',
          label: 'Close Ticket',
          icon: <XCircle className="w-4 h-4 mr-1.5" />,
          variant: 'secondary',
        };
      case 'CLOSED':
      default:
        return null; // Terminal state
    }
  };

  const nextTransition = getNextStatusDetails();

  const handleTransition = async () => {
    if (!nextTransition) return;

    setIsUpdating(true);
    try {
      const updated = await updateTicketApi(ticket.id, {
        title: ticket.title,
        description: ticket.description,
        status: nextTransition.nextStatus,
        category: ticket.category,
        priority: ticket.priority,
      });

      toast.success(
        `Status transitioned from ${ticket.status} to ${updated.status}.`
      );
      onStatusUpdated(updated);
    } catch (err: unknown) {
      const apiErr = err as { message?: string };
      toast.error(apiErr.message || 'Status transition rejected by server.');
    } finally {
      setIsUpdating(false);
    }
  };

  if (!nextTransition) {
    return (
      <span className="inline-flex items-center text-xs font-medium text-slate-400 bg-slate-100 px-3 py-1.5 rounded-lg border border-slate-200">
        Terminal State (Closed)
      </span>
    );
  }

  return (
    <Button
      variant={nextTransition.variant}
      size="sm"
      isLoading={isUpdating}
      onClick={handleTransition}
      className="shadow-xs"
    >
      {nextTransition.icon}
      <span>{nextTransition.label}</span>
      <ArrowRight className="w-3.5 h-3.5 ml-1.5 opacity-70" />
    </Button>
  );
}
