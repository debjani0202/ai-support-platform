import React, { useState } from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import type { TicketResponse } from '@/types/ticket.types';
import { assignTicketApi } from '@/lib/api/tickets';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { UserCheck, X, AlertCircle } from 'lucide-react';
import { toast } from 'sonner';

interface AssignAgentModalProps {
  ticket: TicketResponse;
  isOpen: boolean;
  onClose: () => void;
  onAssigned: (updatedTicket: TicketResponse) => void;
}

export function AssignAgentModal({
  ticket,
  isOpen,
  onClose,
  onAssigned,
}: AssignAgentModalProps) {
  const [agentIdInput, setAgentIdInput] = useState<string>(
    ticket.assignedTo !== null ? String(ticket.assignedTo) : ''
  );
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const parsedId = Number(agentIdInput.trim());

    if (!agentIdInput.trim() || isNaN(parsedId) || parsedId <= 0 || !Number.isInteger(parsedId)) {
      setError('Please provide a valid numeric Agent ID (positive integer).');
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      const updated = await assignTicketApi(ticket.id, parsedId);
      toast.success(`Ticket #${ticket.id} assigned to Agent #${parsedId}.`);
      onAssigned(updated);
      onClose();
    } catch (err: unknown) {
      const apiErr = err as { message?: string; errors?: Record<string, string> };
      if (apiErr.errors?.agentId) {
        setError(apiErr.errors.agentId);
      } else if (apiErr.message) {
        setError(apiErr.message);
      } else {
        setError('Failed to assign ticket. Ensure the target user exists and has the AGENT role.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog.Root open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 bg-slate-950/40 backdrop-blur-xs z-50 animate-in fade-in duration-200" />
        <Dialog.Content className="fixed left-[50%] top-[50%] z-50 w-full max-w-md translate-x-[-50%] translate-y-[-50%] rounded-xl bg-white p-6 shadow-xl border border-slate-200 animate-in zoom-in-95 duration-200 focus:outline-none">
          <div className="flex items-start justify-between mb-4">
            <div className="flex items-center gap-2">
              <div className="p-2 rounded-lg bg-indigo-50 text-indigo-600">
                <UserCheck className="w-5 h-5" />
              </div>
              <div>
                <Dialog.Title className="text-base font-bold text-slate-900">
                  Assign Ticket #{ticket.id}
                </Dialog.Title>
                <Dialog.Description className="text-xs text-slate-500">
                  Assign this ticket to an active support agent by their numeric user ID.
                </Dialog.Description>
              </div>
            </div>

            <Dialog.Close asChild>
              <button
                type="button"
                className="text-slate-400 hover:text-slate-600 p-1 rounded-md transition-colors"
                aria-label="Close"
              >
                <X className="w-4 h-4" />
              </button>
            </Dialog.Close>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            {error && (
              <div className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <div className="space-y-1">
              <Input
                label="Agent ID"
                type="number"
                min={1}
                step={1}
                value={agentIdInput}
                onChange={(e) => {
                  setAgentIdInput(e.target.value);
                  if (error) setError(null);
                }}
                placeholder="e.g. 2"
                helperText={
                  ticket.assignedTo !== null
                    ? `Currently assigned to Agent #${ticket.assignedTo}`
                    : 'Currently unassigned'
                }
                disabled={isSubmitting}
                autoFocus
              />
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={onClose}
                disabled={isSubmitting}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                variant="primary"
                size="sm"
                isLoading={isSubmitting}
                disabled={isSubmitting}
              >
                Confirm Assignment
              </Button>
            </div>
          </form>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
