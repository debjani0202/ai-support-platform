import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import * as Dialog from '@radix-ui/react-dialog';
import type { TicketResponse } from '@/types/ticket.types';
import { deleteTicketApi } from '@/lib/api/tickets';
import { Button } from '@/components/ui/Button';
import { Trash2, AlertTriangle, X, AlertCircle } from 'lucide-react';
import { toast } from 'sonner';

interface DeleteTicketModalProps {
  ticket: TicketResponse;
  isOpen: boolean;
  onClose: () => void;
  onDeleted?: () => void;
}

export function DeleteTicketModal({
  ticket,
  isOpen,
  onClose,
  onDeleted,
}: DeleteTicketModalProps) {
  const navigate = useNavigate();
  const [isDeleting, setIsDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleDelete = async () => {
    setIsDeleting(true);
    setError(null);

    try {
      await deleteTicketApi(ticket.id);
      toast.success(`Ticket #${ticket.id} has been permanently deleted.`);
      onClose();
      if (onDeleted) {
        onDeleted();
      } else {
        navigate('/tickets');
      }
    } catch (err: unknown) {
      const apiErr = err as { message?: string; status?: number };
      if (apiErr.status === 403) {
        setError('You are not authorized to delete this ticket. Agents may only delete assigned tickets; Admins may delete any ticket.');
      } else {
        setError(apiErr.message || 'An error occurred while attempting to delete the ticket.');
      }
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <Dialog.Root open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 bg-slate-950/40 backdrop-blur-xs z-50 animate-in fade-in duration-200" />
        <Dialog.Content className="fixed left-[50%] top-[50%] z-50 w-full max-w-md translate-x-[-50%] translate-y-[-50%] rounded-xl bg-white p-6 shadow-xl border border-slate-200 animate-in zoom-in-95 duration-200 focus:outline-none">
          <div className="flex items-start justify-between mb-4">
            <div className="flex items-center gap-2">
              <div className="p-2 rounded-lg bg-rose-50 text-rose-600">
                <AlertTriangle className="w-5 h-5" />
              </div>
              <div>
                <Dialog.Title className="text-base font-bold text-slate-900">
                  Delete Ticket #{ticket.id}
                </Dialog.Title>
                <Dialog.Description className="text-xs text-slate-500">
                  This action is permanent and cannot be undone.
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

          <div className="space-y-4">
            {error && (
              <div className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <div className="p-3 rounded-lg bg-slate-50 border border-slate-100 text-xs text-slate-600 space-y-1">
              <p className="font-semibold text-slate-800">
                Ticket: <span className="font-normal">{ticket.title}</span>
              </p>
              <p className="text-slate-500">
                Deleting this ticket will also remove all associated comments and audit log entries permanently from the database.
              </p>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={onClose}
                disabled={isDeleting}
              >
                Cancel
              </Button>
              <Button
                type="button"
                variant="danger"
                size="sm"
                isLoading={isDeleting}
                onClick={handleDelete}
              >
                <Trash2 className="w-3.5 h-3.5 mr-1.5" />
                Permanently Delete
              </Button>
            </div>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
