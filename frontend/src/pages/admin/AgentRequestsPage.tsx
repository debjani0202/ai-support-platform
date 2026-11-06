import { useState, useEffect, useCallback } from 'react';
import { AppNavbar } from '@/components/layout/AppNavbar';
import { Card, CardContent } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import {
  getAdminAgentRequestsApi,
  approveAgentRequestApi,
  rejectAgentRequestApi,
} from '@/lib/api/agentRequests';
import type {
  AgentRequestResponse,
  AgentRequestStatus,
} from '@/types/agentRequest.types';
import type { PageResponse } from '@/types/api.types';
import {
  UserCheck,
  CheckCircle2,
  XCircle,
  Clock,
  RefreshCw,
  X,
  AlertTriangle,
  ShieldAlert,
} from 'lucide-react';
import { toast } from 'sonner';

export function AgentRequestsPage() {
  const [pageData, setPageData] = useState<PageResponse<AgentRequestResponse> | null>(null);
  const [statusFilter, setStatusFilter] = useState<AgentRequestStatus | 'ALL'>('ALL');
  const [currentPage, setCurrentPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Review Modal State
  const [selectedRequest, setSelectedRequest] = useState<AgentRequestResponse | null>(null);
  const [modalMode, setModalMode] = useState<'APPROVE' | 'REJECT' | null>(null);
  const [adminNotes, setAdminNotes] = useState('');
  const [rejectionReason, setRejectionReason] = useState('');
  const [rejectionError, setRejectionError] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  const fetchRequests = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const params = {
        page: currentPage,
        size: 10,
        status: statusFilter === 'ALL' ? undefined : statusFilter,
      };
      const data = await getAdminAgentRequestsApi(params);
      setPageData(data);
    } catch (err: unknown) {
      const apiErr = err as { message?: string };
      setError(apiErr.message || 'Failed to fetch agent requests.');
    } finally {
      setLoading(false);
    }
  }, [currentPage, statusFilter]);

  useEffect(() => {
    fetchRequests();
  }, [fetchRequests]);

  const handleOpenReviewModal = (req: AgentRequestResponse, mode: 'APPROVE' | 'REJECT') => {
    setSelectedRequest(req);
    setModalMode(mode);
    setAdminNotes('');
    setRejectionReason('');
    setRejectionError(null);
  };

  const handleCloseModal = () => {
    setSelectedRequest(null);
    setModalMode(null);
    setAdminNotes('');
    setRejectionReason('');
    setRejectionError(null);
  };

  const handleConfirmReview = async () => {
    if (!selectedRequest || !modalMode) return;

    if (modalMode === 'REJECT') {
      const trimmed = rejectionReason.trim();
      if (!trimmed || trimmed.length < 5) {
        setRejectionError('Rejection reason must be at least 5 characters long.');
        return;
      }
    }

    try {
      setActionLoading(true);
      setRejectionError(null);

      if (modalMode === 'APPROVE') {
        await approveAgentRequestApi(selectedRequest.id, {
          adminNotes: adminNotes.trim() || undefined,
        });
        toast.success(`Request for ${selectedRequest.userName} approved successfully.`);
      } else {
        await rejectAgentRequestApi(selectedRequest.id, {
          rejectionReason: rejectionReason.trim(),
        });
        toast.success(`Request for ${selectedRequest.userName} rejected.`);
      }

      handleCloseModal();
      await fetchRequests();
    } catch (err: unknown) {
      const apiErr = err as { message?: string };
      const msg = apiErr.message || 'Failed to review agent request.';
      setRejectionError(msg);
      toast.error(msg);
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100">
      <AppNavbar />

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10 space-y-6">
        {/* Page Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-slate-900 dark:text-white flex items-center gap-3">
              <UserCheck className="w-8 h-8 text-indigo-600 dark:text-indigo-400" />
              <span>Agent Access Requests</span>
            </h1>
            <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
              Review and manage customer applications for Support Agent privileges.
            </p>
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={fetchRequests}
            disabled={loading}
            className="self-start sm:self-auto flex items-center gap-2"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
            <span>Refresh</span>
          </Button>
        </div>

        {/* Filter Tabs */}
        <div className="flex border-b border-slate-200 dark:border-slate-800 space-x-1 sm:space-x-4">
          {(['ALL', 'PENDING', 'APPROVED', 'REJECTED'] as const).map((status) => (
            <button
              key={status}
              onClick={() => {
                setStatusFilter(status);
                setCurrentPage(0);
              }}
              className={`py-2 px-3 text-sm font-medium border-b-2 transition-colors ${
                statusFilter === status
                  ? 'border-indigo-600 text-indigo-600 dark:border-indigo-400 dark:text-indigo-400'
                  : 'border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200'
              }`}
            >
              {status === 'ALL'
                ? 'All Requests'
                : status.charAt(0) + status.slice(1).toLowerCase()}
            </button>
          ))}
        </div>

        {/* Error message */}
        {error && (
          <div className="p-4 rounded-xl bg-rose-50 dark:bg-rose-950/40 text-rose-700 dark:text-rose-300 border border-rose-200 dark:border-rose-900 text-sm flex items-center gap-2">
            <ShieldAlert className="w-5 h-5 flex-shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {/* Requests Table Card */}
        <Card className="border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-sm overflow-hidden">
          <CardContent className="p-0">
            {loading ? (
              <div className="flex items-center justify-center py-16 text-slate-500">
                <RefreshCw className="w-6 h-6 animate-spin mr-3 text-indigo-600" />
                <span className="text-sm">Loading applications...</span>
              </div>
            ) : !pageData || pageData.content.length === 0 ? (
              <div className="text-center py-16 px-4">
                <Clock className="w-12 h-12 text-slate-300 dark:text-slate-600 mx-auto mb-3" />
                <h3 className="text-base font-medium text-slate-900 dark:text-slate-100">
                  No requests found
                </h3>
                <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
                  {statusFilter === 'ALL'
                    ? 'There are currently no agent applications submitted.'
                    : `There are no requests with status "${statusFilter}".`}
                </p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm text-slate-600 dark:text-slate-300">
                  <thead className="bg-slate-50 dark:bg-slate-800/60 text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider border-b border-slate-200 dark:border-slate-800">
                    <tr>
                      <th scope="col" className="px-6 py-3.5">
                        Applicant
                      </th>
                      <th scope="col" className="px-6 py-3.5">
                        Submitted
                      </th>
                      <th scope="col" className="px-6 py-3.5">
                        Status
                      </th>
                      <th scope="col" className="px-6 py-3.5">
                        Applicant Reason
                      </th>
                      <th scope="col" className="px-6 py-3.5">
                        Decision / Notes
                      </th>
                      <th scope="col" className="px-6 py-3.5 text-right">
                        Actions
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                    {pageData.content.map((req: AgentRequestResponse) => (
                      <tr
                        key={req.id}
                        className="hover:bg-slate-50/60 dark:hover:bg-slate-800/40 transition"
                      >
                        <td className="px-6 py-4">
                          <div className="font-medium text-slate-900 dark:text-slate-100">
                            {req.userName}
                          </div>
                          <div className="text-xs text-slate-500 dark:text-slate-400">
                            {req.userEmail}
                          </div>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-xs text-slate-500 dark:text-slate-400">
                          {new Date(req.createdAt).toLocaleDateString()}
                          <span className="block text-[11px] text-slate-400">
                            {new Date(req.createdAt).toLocaleTimeString([], {
                              hour: '2-digit',
                              minute: '2-digit',
                            })}
                          </span>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          <span
                            className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                              req.status === 'APPROVED'
                                ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-300'
                                : req.status === 'REJECTED'
                                ? 'bg-rose-100 text-rose-800 dark:bg-rose-900/40 dark:text-rose-300'
                                : 'bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300'
                            }`}
                          >
                            {req.status}
                          </span>
                        </td>
                        <td className="px-6 py-4 max-w-xs">
                          {req.reason ? (
                            <span className="line-clamp-2 text-xs text-slate-700 dark:text-slate-300">
                              {req.reason}
                            </span>
                          ) : (
                            <span className="text-xs text-slate-400 italic">None provided</span>
                          )}
                        </td>
                        <td className="px-6 py-4 max-w-xs text-xs">
                          {req.status === 'APPROVED' ? (
                            <div>
                              <span className="text-emerald-600 dark:text-emerald-400 font-medium">
                                Approved by {req.reviewerName || 'Admin'}
                              </span>
                              {req.adminNotes && (
                                <p className="text-slate-500 line-clamp-1 italic mt-0.5">
                                  &ldquo;{req.adminNotes}&rdquo;
                                </p>
                              )}
                            </div>
                          ) : req.status === 'REJECTED' ? (
                            <div>
                              <span className="text-rose-600 dark:text-rose-400 font-medium">
                                Rejected by {req.reviewerName || 'Admin'}
                              </span>
                              {req.rejectionReason && (
                                <p className="text-slate-500 line-clamp-2 italic mt-0.5">
                                  &ldquo;{req.rejectionReason}&rdquo;
                                </p>
                              )}
                            </div>
                          ) : (
                            <span className="text-amber-600 dark:text-amber-400 text-xs font-medium">
                              Awaiting decision
                            </span>
                          )}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-right space-x-2">
                          {req.status === 'PENDING' ? (
                            <>
                              <Button
                                size="sm"
                                onClick={() => handleOpenReviewModal(req, 'APPROVE')}
                                className="bg-emerald-600 hover:bg-emerald-700 text-white text-xs h-8 px-2.5"
                              >
                                Approve
                              </Button>
                              <Button
                                size="sm"
                                variant="outline"
                                onClick={() => handleOpenReviewModal(req, 'REJECT')}
                                className="text-rose-600 border-rose-200 hover:bg-rose-50 dark:border-rose-900 dark:hover:bg-rose-950/50 text-xs h-8 px-2.5"
                              >
                                Reject
                              </Button>
                            </>
                          ) : (
                            <span className="text-xs text-slate-400">Reviewed</span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {/* Pagination Controls */}
            {pageData && pageData.totalPages > 1 && (
              <div className="flex items-center justify-between px-6 py-4 border-t border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-800/30">
                <span className="text-xs text-slate-500 dark:text-slate-400">
                  Showing page {pageData.page + 1} of {pageData.totalPages} (
                  {pageData.totalElements} total)
                </span>
                <div className="flex items-center space-x-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={pageData.first}
                    onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
                  >
                    Previous
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={pageData.last}
                    onClick={() => setCurrentPage((p) => p + 1)}
                  >
                    Next
                  </Button>
                </div>
              </div>
            )}
          </CardContent>
        </Card>

        {/* Review Modal Dialog */}
        {selectedRequest && modalMode && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm">
            <div className="w-full max-w-lg bg-white dark:bg-slate-900 rounded-2xl shadow-2xl border border-slate-200 dark:border-slate-800 overflow-hidden animate-in fade-in zoom-in-95 duration-150">
              <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 dark:border-slate-800">
                <h3 className="text-base font-semibold text-slate-900 dark:text-white flex items-center gap-2">
                  {modalMode === 'APPROVE' ? (
                    <>
                      <CheckCircle2 className="w-5 h-5 text-emerald-600" />
                      <span>Approve Agent Application</span>
                    </>
                  ) : (
                    <>
                      <XCircle className="w-5 h-5 text-rose-600" />
                      <span>Reject Agent Application</span>
                    </>
                  )}
                </h3>
                <button
                  onClick={handleCloseModal}
                  className="p-1 rounded-lg text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              <div className="p-6 space-y-4">
                {/* Applicant Info Summary */}
                <div className="p-3.5 bg-slate-50 dark:bg-slate-800/60 rounded-xl space-y-1 text-xs">
                  <div className="flex justify-between">
                    <span className="text-slate-500 font-medium">Applicant:</span>
                    <span className="font-semibold text-slate-800 dark:text-slate-200">
                      {selectedRequest.userName} ({selectedRequest.userEmail})
                    </span>
                  </div>
                  {selectedRequest.reason && (
                    <div className="mt-2 pt-2 border-t border-slate-200 dark:border-slate-700">
                      <span className="text-slate-500 font-medium block mb-0.5">
                        Applicant&apos;s reason:
                      </span>
                      <p className="italic text-slate-700 dark:text-slate-300">
                        &ldquo;{selectedRequest.reason}&rdquo;
                      </p>
                    </div>
                  )}
                </div>

                {rejectionError && (
                  <div className="p-3 bg-rose-50 text-rose-700 dark:bg-rose-950/40 dark:text-rose-300 rounded-lg text-xs flex items-center gap-2 border border-rose-200 dark:border-rose-900">
                    <AlertTriangle className="w-4 h-4 flex-shrink-0" />
                    <span>{rejectionError}</span>
                  </div>
                )}

                {modalMode === 'APPROVE' ? (
                  <div>
                    <label
                      htmlFor="admin-notes"
                      className="block text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider mb-1"
                    >
                      Admin Notes (Optional)
                    </label>
                    <textarea
                      id="admin-notes"
                      rows={3}
                      value={adminNotes}
                      onChange={(e) => setAdminNotes(e.target.value)}
                      maxLength={1000}
                      placeholder="Add an optional welcome note or onboarding instruction..."
                      className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent transition"
                    />
                    <p className="mt-1 text-right text-xs text-slate-400">
                      {adminNotes.length}/1000
                    </p>
                  </div>
                ) : (
                  <div>
                    <label
                      htmlFor="rejection-reason"
                      className="block text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider mb-1"
                    >
                      Rejection Reason (Required, min 5 chars)
                    </label>
                    <textarea
                      id="rejection-reason"
                      rows={3}
                      value={rejectionReason}
                      onChange={(e) => {
                        setRejectionReason(e.target.value);
                        if (rejectionError) setRejectionError(null);
                      }}
                      maxLength={1000}
                      placeholder="Explain to the customer why this request cannot be approved at this time..."
                      className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-transparent transition"
                    />
                    <p className="mt-1 text-right text-xs text-slate-400">
                      {rejectionReason.length}/1000
                    </p>
                  </div>
                )}
              </div>

              <div className="flex items-center justify-end px-6 py-4 bg-slate-50 dark:bg-slate-800/40 border-t border-slate-100 dark:border-slate-800 gap-3">
                <Button
                  variant="ghost"
                  onClick={handleCloseModal}
                  disabled={actionLoading}
                >
                  Cancel
                </Button>
                <Button
                  onClick={handleConfirmReview}
                  disabled={actionLoading}
                  className={
                    modalMode === 'APPROVE'
                      ? 'bg-emerald-600 hover:bg-emerald-700 text-white'
                      : 'bg-rose-600 hover:bg-rose-700 text-white'
                  }
                >
                  {actionLoading
                    ? 'Processing...'
                    : modalMode === 'APPROVE'
                    ? 'Confirm Approval'
                    : 'Confirm Rejection'}
                </Button>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
