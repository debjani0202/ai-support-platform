import { useState, useEffect, useRef } from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import type { AttachmentResponse } from '@/types/attachment.types';
import { deleteTicketAttachmentApi } from '@/lib/api/attachments';
import { Button } from '@/components/ui/Button';
import { Trash2, AlertTriangle, X, AlertCircle } from 'lucide-react';
import { toast } from 'sonner';

interface DeleteAttachmentModalProps {
  ticketId: number;
  attachment: AttachmentResponse | null;
  isOpen: boolean;
  onClose: () => void;
  onDeleted: () => void | Promise<void>;
  onConflict?: () => void | Promise<void>;
}

interface ApiErrorLike {
  status?: number;
  message?: string;
}

function getSafeErrorDetails(err: unknown): ApiErrorLike {
  if (err && typeof err === 'object') {
    const record = err as Record<string, unknown>;
    return {
      status: typeof record.status === 'number' ? record.status : undefined,
      message: typeof record.message === 'string' && record.message.trim() ? record.message : undefined,
    };
  }
  return {};
}

export function DeleteAttachmentModal({
  ticketId,
  attachment,
  isOpen,
  onClose,
  onDeleted,
  onConflict,
}: DeleteAttachmentModalProps) {
  const [isDeleting, setIsDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const isMountedRef = useRef(true);

  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    if (isOpen) {
      setError(null);
      setIsDeleting(false);
    }
  }, [isOpen, attachment?.id]);

  if (!attachment) {
    return null;
  }

  const handleDelete = async () => {
    setIsDeleting(true);
    setError(null);

    try {
      await deleteTicketAttachmentApi(ticketId, attachment.id);
      if (!isMountedRef.current) {
        return;
      }
      toast.success(`Attachment "${attachment.originalFileName}" deleted.`);
      onClose();
      await onDeleted();
    } catch (err: unknown) {
      if (!isMountedRef.current) {
        return;
      }
      const apiErr = getSafeErrorDetails(err);
      if (apiErr.status === 403) {
        setError(apiErr.message || 'You are not authorized to delete this attachment.');
      } else if (apiErr.status === 404) {
        setError(apiErr.message || 'This attachment or ticket could no longer be found.');
      } else if (apiErr.status === 409) {
        setError(
          apiErr.message || 'Cannot delete attachments because this ticket is closed.'
        );
        await onConflict?.();
      } else {
        setError(apiErr.message || 'An error occurred while attempting to delete the attachment.');
      }
    } finally {
      if (isMountedRef.current) {
        setIsDeleting(false);
      }
    }
  };

  return (
    <Dialog.Root
      open={isOpen}
      onOpenChange={(open) => {
        if (!open && !isDeleting) {
          onClose();
        }
      }}
    >
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
                  Delete Attachment
                </Dialog.Title>
                <Dialog.Description className="text-xs text-slate-500">
                  This action permanently removes the file and cannot be undone.
                </Dialog.Description>
              </div>
            </div>

            <Dialog.Close asChild>
              <button
                type="button"
                disabled={isDeleting}
                className="text-slate-400 hover:text-slate-600 p-1 rounded-md transition-colors disabled:opacity-50"
                aria-label="Close"
              >
                <X className="w-4 h-4" />
              </button>
            </Dialog.Close>
          </div>

          <div className="space-y-4">
            {error && (
              <div
                role="alert"
                className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-center gap-2"
              >
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <div className="p-3 rounded-lg bg-slate-50 border border-slate-100 text-xs text-slate-600 space-y-1">
              <p className="font-semibold text-slate-800 break-all">
                File: <span className="font-normal">{attachment.originalFileName}</span>
              </p>
              <p className="text-slate-500">
                Uploaded by {attachment.uploadedByName}. Deleting this file will record an audit entry and remove it from ticket #{ticketId}.
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
                Delete Attachment
              </Button>
            </div>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

