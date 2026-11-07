import React, { useState, useEffect, useCallback } from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import type { TicketResponse } from '@/types/ticket.types';
import type { AgentResponse } from '@/types/user.types';
import { assignTicketApi } from '@/lib/api/tickets';
import { getAgentsApi } from '@/lib/api/users';
import { Button } from '@/components/ui/Button';
import { UserCheck, X, AlertCircle, Loader2, RefreshCw } from 'lucide-react';
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
  const [agents, setAgents] = useState<AgentResponse[]>([]);
  const [selectedAgentId, setSelectedAgentId] = useState<string>('');
  const [isLoadingAgents, setIsLoadingAgents] = useState(false);
  const [fetchError, setFetchError] = useState<string | null>(null);

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const fetchAgents = useCallback(async () => {
    setIsLoadingAgents(true);
    setFetchError(null);
    setAgents([]); // Clear any previous agent data to prevent stale selection
    setSelectedAgentId('');
    try {
      const data = await getAgentsApi();
      setAgents(data);
    } catch (err: unknown) {
      const apiErr = err as { message?: string };
      setFetchError(apiErr.message || 'Failed to load support agents.');
      setAgents([]);
    } finally {
      setIsLoadingAgents(false);
    }
  }, []);

  useEffect(() => {
    if (isOpen) {
      setSubmitError(null);
      fetchAgents();
    }
  }, [isOpen, fetchAgents]);

  useEffect(() => {
    if (isOpen && !isLoadingAgents && !fetchError) {
      if (ticket.assignedTo !== null) {
        const found = agents.some((a) => a.id === ticket.assignedTo);
        if (found) {
          setSelectedAgentId(String(ticket.assignedTo));
        } else {
          // The assigned agent is not in the returned agents list (e.g. role changed)
          setSelectedAgentId('');
        }
      } else {
        setSelectedAgentId('');
      }
    }
  }, [isOpen, isLoadingAgents, fetchError, ticket.assignedTo, agents]);

  const isCurrentAssigneeInList =
    ticket.assignedTo !== null
      ? agents.some((a) => a.id === ticket.assignedTo)
      : true;

  const currentAssignedAgent =
    ticket.assignedTo !== null
      ? agents.find((a) => a.id === ticket.assignedTo)
      : null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const parsedId = Number(selectedAgentId);

    if (fetchError) {
      setSubmitError('Cannot assign ticket while agent directory fetch has failed.');
      return;
    }

    if (!selectedAgentId || isNaN(parsedId) || parsedId <= 0 || !Number.isInteger(parsedId)) {
      setSubmitError('Please select a valid support agent.');
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);

    try {
      const updated = await assignTicketApi(ticket.id, parsedId);
      const assignedAgent = agents.find((a) => a.id === parsedId);
      const agentDisplayName = assignedAgent ? assignedAgent.name : `Agent #${parsedId}`;
      toast.success(`Ticket #${ticket.id} assigned to ${agentDisplayName}.`);
      onAssigned(updated);
      onClose();
    } catch (err: unknown) {
      const apiErr = err as { message?: string; errors?: Record<string, string> };
      if (apiErr.errors?.agentId) {
        setSubmitError(apiErr.errors.agentId);
      } else if (apiErr.message) {
        setSubmitError(apiErr.message);
      } else {
        setSubmitError('Failed to assign ticket. Ensure the target user exists and has the AGENT role.');
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
                  Assign this ticket to a support agent from the available agent directory.
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
            {fetchError && (
              <div className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <AlertCircle className="w-4 h-4 shrink-0" />
                  <span>{fetchError}</span>
                </div>
                <button
                  type="button"
                  onClick={fetchAgents}
                  className="inline-flex items-center gap-1 font-semibold text-rose-800 hover:underline"
                >
                  <RefreshCw className="w-3 h-3" /> Retry
                </button>
              </div>
            )}

            {submitError && (
              <div className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{submitError}</span>
              </div>
            )}

            {ticket.assignedTo !== null && !isLoadingAgents && !fetchError && !isCurrentAssigneeInList && (
              <div className="p-3 rounded-lg bg-amber-50 border border-amber-200 text-amber-800 text-xs flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0 text-amber-600" />
                <span>
                  The currently assigned agent (ID #{ticket.assignedTo}) is no longer available in the agent directory (e.g., role changed or demoted). Please select a new agent from the list below.
                </span>
              </div>
            )}

            {!isLoadingAgents && !fetchError && agents.length === 0 && (
              <div className="p-3 rounded-lg bg-amber-50 border border-amber-200 text-amber-800 text-xs flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0 text-amber-600" />
                <span>
                  No available support agents found in the system. New agents must be created or approved before tickets can be assigned.
                </span>
              </div>
            )}

            <div className="space-y-1.5 text-left">
              <label
                htmlFor="agent-select"
                className="block text-xs font-semibold uppercase tracking-wider text-slate-700"
              >
                Assignee
              </label>

              {isLoadingAgents ? (
                <div className="flex h-10 w-full items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-500">
                  <Loader2 className="w-4 h-4 animate-spin text-indigo-600" />
                  <span>Loading agents...</span>
                </div>
              ) : (
                <select
                  id="agent-select"
                  value={selectedAgentId}
                  onChange={(e) => {
                    setSelectedAgentId(e.target.value);
                    if (submitError) setSubmitError(null);
                  }}
                  disabled={isSubmitting || Boolean(fetchError) || agents.length === 0}
                  className="flex h-10 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 transition-colors shadow-xs disabled:bg-slate-50 disabled:text-slate-400"
                >
                  <option value="" disabled>
                    {agents.length === 0 ? 'No available agents' : 'Select an agent...'}
                  </option>
                  {ticket.assignedTo !== null && !isCurrentAssigneeInList && (
                    <option value={String(ticket.assignedTo)} disabled>
                      Current: Agent #{ticket.assignedTo} (No longer available as an agent)
                    </option>
                  )}
                  {agents.map((agent) => (
                    <option key={agent.id} value={String(agent.id)}>
                      {agent.name} ({agent.email})
                    </option>
                  ))}
                </select>
              )}

              <p className="text-xs text-slate-500">
                {ticket.assignedTo !== null
                  ? isCurrentAssigneeInList && currentAssignedAgent
                    ? `Currently assigned to ${currentAssignedAgent.name}`
                    : `Currently assigned to Agent #${ticket.assignedTo}`
                  : 'Currently unassigned'}
              </p>
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
                disabled={
                  isSubmitting ||
                  isLoadingAgents ||
                  Boolean(fetchError) ||
                  !selectedAgentId ||
                  agents.length === 0
                }
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
