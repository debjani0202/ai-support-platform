import { useState, useEffect, useCallback } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { getTicketByIdApi } from '@/lib/api/tickets';
import { getTicketCommentsApi } from '@/lib/api/comments';
import { getTicketAuditLogsApi } from '@/lib/api/audit';
import type { TicketResponse } from '@/types/ticket.types';
import type { CommentResponse } from '@/types/comment.types';
import type { TicketAuditLogResponse } from '@/types/audit.types';
import { AppNavbar } from '@/components/layout/AppNavbar';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/Card';
import { StatusBadge } from '@/components/badges/StatusBadge';
import { PriorityBadge } from '@/components/badges/PriorityBadge';
import { CategoryBadge } from '@/components/badges/CategoryBadge';
import { Button } from '@/components/ui/Button';
import { StatusTransitionControl } from '@/components/tickets/StatusTransitionControl';
import { AssignAgentModal } from '@/components/tickets/AssignAgentModal';
import { DeleteTicketModal } from '@/components/tickets/DeleteTicketModal';
import { CommentTimeline } from '@/components/tickets/CommentTimeline';
import { CommentForm } from '@/components/tickets/CommentForm';
import { AuditTimeline } from '@/components/tickets/AuditTimeline';
import {
  ArrowLeft,
  Calendar,
  Clock,
  User,
  UserCheck,
  AlertCircle,
  ShieldAlert,
  HelpCircle,
  MessageSquare,
  History,
  Trash2,
  UserPlus,
} from 'lucide-react';
import { format, parseISO } from 'date-fns';

type ActiveTab = 'comments' | 'audit';

export function TicketDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [ticket, setTicket] = useState<TicketResponse | null>(null);
  const [isLoadingTicket, setIsLoadingTicket] = useState<boolean>(true);
  const [errorStatus, setErrorStatus] = useState<number | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Comments state
  const [comments, setComments] = useState<CommentResponse[]>([]);
  const [isLoadingComments, setIsLoadingComments] = useState<boolean>(false);

  // Audit logs state
  const [auditLogs, setAuditLogs] = useState<TicketAuditLogResponse[]>([]);
  const [isLoadingAudit, setIsLoadingAudit] = useState<boolean>(false);

  // Tab navigation
  const [activeTab, setActiveTab] = useState<ActiveTab>('comments');

  // Modals state
  const [isAssignModalOpen, setIsAssignModalOpen] = useState(false);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);

  // Fetch ticket details
  const fetchTicket = useCallback(async () => {
    if (!id || isNaN(Number(id))) {
      setErrorStatus(404);
      setErrorMessage('Invalid ticket ID');
      setIsLoadingTicket(false);
      return;
    }

    setIsLoadingTicket(true);
    setErrorStatus(null);
    setErrorMessage(null);

    try {
      const data = await getTicketByIdApi(Number(id));
      setTicket(data);
    } catch (err: unknown) {
      const apiErr = err as { status?: number; message?: string };
      setErrorStatus(apiErr.status || 500);
      setErrorMessage(apiErr.message || 'Failed to load ticket details.');
    } finally {
      setIsLoadingTicket(false);
    }
  }, [id]);

  // Fetch comments
  const fetchComments = useCallback(async (ticketId: number) => {
    setIsLoadingComments(true);
    try {
      const data = await getTicketCommentsApi(ticketId);
      setComments(data);
    } catch {
      // Non-fatal error; keep existing comments
    } finally {
      setIsLoadingComments(false);
    }
  }, []);

  // Fetch audit logs
  const fetchAuditLogs = useCallback(async (ticketId: number) => {
    setIsLoadingAudit(true);
    try {
      const data = await getTicketAuditLogsApi(ticketId);
      setAuditLogs(data);
    } catch {
      // Non-fatal error; keep existing logs
    } finally {
      setIsLoadingAudit(false);
    }
  }, []);

  useEffect(() => {
    fetchTicket();
  }, [fetchTicket]);

 useEffect(() => {
  const ticketId = ticket?.id;

  if (ticketId === undefined) {
    return;
  }

  fetchComments(ticketId);
  fetchAuditLogs(ticketId);
}, [ticket?.id, fetchComments, fetchAuditLogs]);

  const handleCommentAdded = (newComment: CommentResponse) => {
    setComments((prev) => [...prev, newComment]);
  };

  const handleTicketUpdated = (updated: TicketResponse) => {
    setTicket(updated);
    // Refresh audit history to reflect the transition or assignment
    fetchAuditLogs(updated.id);
  };

  const handleTicketDeleted = () => {
    navigate('/tickets');
  };

  // Determine deletion permissions:
  // Admin can delete any ticket.
  // Agent can delete only if assignedTo === user.userId.
  // Customer cannot delete.
  const canDeleteTicket =
    user?.role === 'ADMIN' ||
    (user?.role === 'AGENT' && ticket?.assignedTo === user?.userId);

  // Determine assign permission: Admin only
  const canAssignTicket = user?.role === 'ADMIN';

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <AppNavbar />

      <main className="flex-1 max-w-5xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
        <div>
          <Link
            to="/tickets"
            className="inline-flex items-center gap-1.5 text-xs text-slate-500 hover:text-slate-800 transition-colors mb-3"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            Back to Tickets
          </Link>
        </div>

        {/* Loading Skeleton */}
        {isLoadingTicket && (
          <div className="space-y-4 animate-pulse">
            <div className="h-10 w-2/3 bg-slate-200 rounded-lg" />
            <div className="h-48 w-full bg-slate-200 rounded-xl" />
            <div className="h-32 w-full bg-slate-200 rounded-xl" />
          </div>
        )}

        {/* Error: 404 Not Found */}
        {!isLoadingTicket && errorStatus === 404 && (
          <div className="p-8 bg-white rounded-xl border border-slate-200 text-center max-w-md mx-auto space-y-3">
            <div className="p-3 rounded-full bg-amber-50 text-amber-600 inline-flex">
              <HelpCircle className="w-8 h-8" />
            </div>
            <h2 className="text-lg font-bold text-slate-900">Ticket Not Found</h2>
            <p className="text-xs text-slate-500">
              {errorMessage || `Ticket #${id} could not be found in the system.`}
            </p>
            <Link to="/tickets">
              <Button variant="primary" size="sm">
                Return to Tickets
              </Button>
            </Link>
          </div>
        )}

        {/* Error: 403 Forbidden */}
        {!isLoadingTicket && errorStatus === 403 && (
          <div className="p-8 bg-white rounded-xl border border-slate-200 text-center max-w-md mx-auto space-y-3">
            <div className="p-3 rounded-full bg-rose-50 text-rose-600 inline-flex">
              <ShieldAlert className="w-8 h-8" />
            </div>
            <h2 className="text-lg font-bold text-slate-900">Access Denied</h2>
            <p className="text-xs text-slate-500">
              {errorMessage || `You do not have permission to view ticket #${id}.`}
            </p>
            <Link to="/tickets">
              <Button variant="primary" size="sm">
                Return to Tickets
              </Button>
            </Link>
          </div>
        )}

        {/* Error: Other */}
        {!isLoadingTicket && errorStatus && errorStatus !== 404 && errorStatus !== 403 && (
          <div className="p-6 bg-rose-50 border border-rose-200 rounded-xl text-rose-700 flex items-center justify-between text-xs">
            <div className="flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{errorMessage || 'An error occurred while loading this ticket.'}</span>
            </div>
            <Button variant="outline" size="sm" onClick={() => fetchTicket()}>
              Retry
            </Button>
          </div>
        )}

        {/* Ticket Workspace View */}
        {!isLoadingTicket && ticket && (
          <div className="space-y-6">
            {/* Header Card */}
            <div className="bg-white p-6 rounded-xl border border-slate-200/80 shadow-card space-y-4">
              <div className="flex flex-col lg:flex-row lg:items-start lg:justify-between gap-4">
                <div className="space-y-1.5 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-mono text-xs font-semibold text-indigo-600 bg-indigo-50 px-2 py-0.5 rounded">
                      #{ticket.id}
                    </span>
                    <StatusBadge status={ticket.status} />
                    <PriorityBadge priority={ticket.priority} />
                    <CategoryBadge category={ticket.category} />
                  </div>
                  <h1 className="text-xl font-bold text-slate-900 tracking-tight pt-1">
                    {ticket.title}
                  </h1>
                </div>

                {/* Role-governed Action Buttons */}
                <div className="flex flex-wrap items-center gap-2 self-start">
                  {/* Status Progression Control (Agent & Admin) */}
                  <StatusTransitionControl
                    ticket={ticket}
                    userRole={user?.role}
                    onStatusUpdated={handleTicketUpdated}
                  />

                  {/* Admin Assignment Trigger */}
                  {canAssignTicket && (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setIsAssignModalOpen(true)}
                      className="shadow-xs"
                    >
                      <UserPlus className="w-3.5 h-3.5 mr-1.5 text-slate-500" />
                      Assign Agent
                    </Button>
                  )}

                  {/* Authorized Deletion Trigger */}
                  {canDeleteTicket && (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setIsDeleteModalOpen(true)}
                      className="text-rose-600 hover:text-rose-700 hover:bg-rose-50 border-rose-200"
                    >
                      <Trash2 className="w-3.5 h-3.5 mr-1.5" />
                      Delete
                    </Button>
                  )}
                </div>
              </div>

              {/* Metadata strip */}
              <div className="pt-3 border-t border-slate-100 grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs text-slate-500">
                <div className="flex items-center gap-1.5">
                  <User className="w-3.5 h-3.5 text-slate-400" />
                  <span>
                    Created by:{' '}
                    <strong className="text-slate-700">
                      {ticket.createdBy === user?.userId ? 'You' : `User #${ticket.createdBy}`}
                    </strong>
                  </span>
                </div>

                <div className="flex items-center gap-1.5">
                  <UserCheck className="w-3.5 h-3.5 text-slate-400" />
                  <span>
                    Assigned to:{' '}
                    <strong className="text-slate-700">
                      {ticket.assignedTo !== null ? `Agent #${ticket.assignedTo}` : 'Unassigned'}
                    </strong>
                  </span>
                </div>

                <div className="flex items-center gap-1.5">
                  <Calendar className="w-3.5 h-3.5 text-slate-400" />
                  <span>
                    Created:{' '}
                    <strong className="text-slate-700">
                      {format(parseISO(ticket.createdAt), 'PP')}
                    </strong>
                  </span>
                </div>

                <div className="flex items-center gap-1.5">
                  <Clock className="w-3.5 h-3.5 text-slate-400" />
                  <span>
                    Updated:{' '}
                    <strong className="text-slate-700">
                      {format(parseISO(ticket.updatedAt), 'PPp')}
                    </strong>
                  </span>
                </div>
              </div>
            </div>

            {/* Description Card */}
            <Card>
              <CardHeader>
                <CardTitle className="text-base">Ticket Description</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="text-sm text-slate-800 whitespace-pre-wrap leading-relaxed bg-slate-50/60 p-4 rounded-lg border border-slate-100">
                  {ticket.description}
                </div>
              </CardContent>
            </Card>

            {/* Collaboration & History Tabs */}
            <div className="bg-white rounded-xl border border-slate-200/80 shadow-card overflow-hidden">
              {/* Tab navigation headers */}
              <div className="flex border-b border-slate-200 bg-slate-50/50 px-4 pt-2 gap-2">
                <button
                  type="button"
                  onClick={() => setActiveTab('comments')}
                  className={`flex items-center gap-2 px-4 py-2.5 text-xs font-semibold rounded-t-lg transition-colors border-b-2 -mb-[1px] ${
                    activeTab === 'comments'
                      ? 'bg-white text-indigo-600 border-indigo-600 shadow-xs'
                      : 'text-slate-600 border-transparent hover:text-slate-900 hover:bg-slate-100/60'
                  }`}
                >
                  <MessageSquare className="w-3.5 h-3.5" />
                  <span>Discussion</span>
                  <span
                    className={`ml-1 px-1.5 py-0.2 rounded-full text-[10px] font-mono ${
                      activeTab === 'comments'
                        ? 'bg-indigo-50 text-indigo-700 font-bold'
                        : 'bg-slate-200/80 text-slate-600'
                    }`}
                  >
                    {comments.length}
                  </span>
                </button>

                <button
                  type="button"
                  onClick={() => setActiveTab('audit')}
                  className={`flex items-center gap-2 px-4 py-2.5 text-xs font-semibold rounded-t-lg transition-colors border-b-2 -mb-[1px] ${
                    activeTab === 'audit'
                      ? 'bg-white text-indigo-600 border-indigo-600 shadow-xs'
                      : 'text-slate-600 border-transparent hover:text-slate-900 hover:bg-slate-100/60'
                  }`}
                >
                  <History className="w-3.5 h-3.5" />
                  <span>Audit Trail</span>
                  <span
                    className={`ml-1 px-1.5 py-0.2 rounded-full text-[10px] font-mono ${
                      activeTab === 'audit'
                        ? 'bg-indigo-50 text-indigo-700 font-bold'
                        : 'bg-slate-200/80 text-slate-600'
                    }`}
                  >
                    {auditLogs.length}
                  </span>
                </button>
              </div>

              {/* Tab Contents */}
              <div className="p-6">
                {activeTab === 'comments' ? (
                  <div className="space-y-6">
                    {/* Existing Comments */}
                    <CommentTimeline
                      comments={comments}
                      isLoading={isLoadingComments}
                    />

                    {/* New Comment Form */}
                    <div className="pt-4 border-t border-slate-200/80">
                      <CommentForm
                        ticketId={ticket.id}
                        onCommentAdded={handleCommentAdded}
                      />
                    </div>
                  </div>
                ) : (
                  <div>
                    <AuditTimeline
                      logs={auditLogs}
                      isLoading={isLoadingAudit}
                    />
                  </div>
                )}
              </div>
            </div>

            {/* Admin Assign Modal */}
            <AssignAgentModal
              ticket={ticket}
              isOpen={isAssignModalOpen}
              onClose={() => setIsAssignModalOpen(false)}
              onAssigned={handleTicketUpdated}
            />

            {/* Authorized Deletion Modal */}
            <DeleteTicketModal
              ticket={ticket}
              isOpen={isDeleteModalOpen}
              onClose={() => setIsDeleteModalOpen(false)}
              onDeleted={handleTicketDeleted}
            />
          </div>
        )}
      </main>
    </div>
  );
}
