import { useState, useEffect, useCallback } from 'react';
import { useAuth } from '@/hooks/useAuth';
import { AppNavbar } from '@/components/layout/AppNavbar';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import {
  getMyAgentRequestApi,
  submitAgentRequestApi,
} from '@/lib/api/agentRequests';
import type { AgentRequestResponse } from '@/types/agentRequest.types';
import {
  User,
  Shield,
  Clock,
  CheckCircle2,
  XCircle,
  AlertCircle,
  Sparkles,
  RefreshCw,
  Send,
  HelpCircle,
} from 'lucide-react';
import { toast } from 'sonner';

export function ProfilePage() {
  const { user, refreshSession } = useAuth();
  const [request, setRequest] = useState<AgentRequestResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [activating, setActivating] = useState(false);
  const [reason, setReason] = useState('');
  const [showApplyForm, setShowApplyForm] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchLatestRequest = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await getMyAgentRequestApi();
      setRequest(data);
    } catch (err: unknown) {
      const apiErr = err as { message?: string };
      setError(apiErr.message || 'Failed to load request status.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchLatestRequest();
  }, [fetchLatestRequest]);

  const handleSubmitRequest = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      setSubmitting(true);
      const created = await submitAgentRequestApi({ reason: reason.trim() });
      setRequest(created);
      setReason('');
      setShowApplyForm(false);
      toast.success('Agent access request submitted successfully.');
    } catch (err: unknown) {
      const apiErr = err as { message?: string };
      const msg = apiErr.message || 'Failed to submit agent request.';
      setError(msg);
      toast.error(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleActivateWorkspace = async () => {
    try {
      setActivating(true);
      await refreshSession();
      toast.success('Agent workspace activated! Your role has been updated.');
      await fetchLatestRequest();
    } catch (err: unknown) {
      const apiErr = err as { message?: string };
      toast.error(apiErr.message || 'Failed to refresh session. Please try logging in again.');
    } finally {
      setActivating(false);
    }
  };

  const isCustomer = user?.role === 'CUSTOMER';
  const isAgent = user?.role === 'AGENT';
  const isAdmin = user?.role === 'ADMIN';

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100">
      <AppNavbar />

      <main className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-10 space-y-8">
        <div>
          <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-slate-900 dark:text-white">
            User Profile
          </h1>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
            Manage your account details and role permissions.
          </p>
        </div>

        {/* Account Details Card */}
        <Card className="border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-sm">
          <CardHeader className="pb-4 border-b border-slate-100 dark:border-slate-800">
            <div className="flex items-center space-x-3">
              <div className="p-2.5 rounded-xl bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400">
                <User className="w-5 h-5" />
              </div>
              <div>
                <CardTitle className="text-lg">Account Information</CardTitle>
                <CardDescription>Your registered identity and active platform role</CardDescription>
              </div>
            </div>
          </CardHeader>
          <CardContent className="pt-6">
            <dl className="grid grid-cols-1 sm:grid-cols-3 gap-6">
              <div>
                <dt className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
                  Full Name
                </dt>
                <dd className="mt-1 text-sm font-medium text-slate-900 dark:text-slate-100">
                  {user?.name || '—'}
                </dd>
              </div>
              <div>
                <dt className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
                  Email Address
                </dt>
                <dd className="mt-1 text-sm font-medium text-slate-900 dark:text-slate-100">
                  {user?.email || '—'}
                </dd>
              </div>
              <div>
                <dt className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
                  Current Role
                </dt>
                <dd className="mt-1">
                  <span
                    className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                      isAdmin
                        ? 'bg-purple-100 text-purple-800 dark:bg-purple-900/40 dark:text-purple-300'
                        : isAgent
                        ? 'bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300'
                        : 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-300'
                    }`}
                  >
                    {user?.role}
                  </span>
                </dd>
              </div>
            </dl>
          </CardContent>
        </Card>

        {/* Role Privileges / Agent Access Section */}
        <Card className="border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-sm">
          <CardHeader className="pb-4 border-b border-slate-100 dark:border-slate-800">
            <div className="flex items-center justify-between">
              <div className="flex items-center space-x-3">
                <div className="p-2.5 rounded-xl bg-blue-50 dark:bg-blue-950/60 text-blue-600 dark:text-blue-400">
                  <Shield className="w-5 h-5" />
                </div>
                <div>
                  <CardTitle className="text-lg">Support Agent Access</CardTitle>
                  <CardDescription>
                    Request permission to join the support team and handle customer tickets
                  </CardDescription>
                </div>
              </div>
              {request && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={fetchLatestRequest}
                  disabled={loading}
                  className="text-slate-500 hover:text-slate-700 dark:hover:text-slate-300"
                >
                  <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
                </Button>
              )}
            </div>
          </CardHeader>

          <CardContent className="pt-6">
            {/* If user is already an AGENT or ADMIN */}
            {(isAgent || isAdmin) && (
              <div className="flex items-start space-x-3 p-4 rounded-xl bg-blue-50/80 dark:bg-blue-950/30 border border-blue-200 dark:border-blue-900">
                <CheckCircle2 className="w-5 h-5 text-blue-600 dark:text-blue-400 mt-0.5 flex-shrink-0" />
                <div>
                  <h4 className="text-sm font-semibold text-blue-900 dark:text-blue-200">
                    Active Elevated Role: {user?.role}
                  </h4>
                  <p className="mt-1 text-xs text-blue-700 dark:text-blue-300">
                    {isAdmin
                      ? 'You have Administrator privileges, which include full access to tickets, analytics, user management, and agent request reviews.'
                      : 'You are an active Support Agent with permissions to view assigned tickets, claim unassigned requests, and respond to customers.'}
                  </p>
                </div>
              </div>
            )}

            {/* If user is CUSTOMER */}
            {isCustomer && (
              <div className="space-y-6">
                {error && (
                  <div className="flex items-center space-x-2 p-3 text-sm rounded-lg bg-rose-50 text-rose-700 dark:bg-rose-950/40 dark:text-rose-300 border border-rose-200 dark:border-rose-900">
                    <AlertCircle className="w-4 h-4 flex-shrink-0" />
                    <span>{error}</span>
                  </div>
                )}

                {loading ? (
                  <div className="flex items-center justify-center py-8 text-slate-500">
                    <RefreshCw className="w-5 h-5 animate-spin mr-2" />
                    <span className="text-sm">Checking request status...</span>
                  </div>
                ) : request?.status === 'PENDING' ? (
                  /* PENDING STATE */
                  <div className="p-5 rounded-xl bg-amber-50 dark:bg-amber-950/30 border border-amber-200 dark:border-amber-800/80 space-y-3">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center space-x-2 text-amber-800 dark:text-amber-300 font-semibold text-sm">
                        <Clock className="w-4 h-4" />
                        <span>Request Pending Review</span>
                      </div>
                      <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-amber-200/80 text-amber-900 dark:bg-amber-900/60 dark:text-amber-200">
                        PENDING
                      </span>
                    </div>
                    <p className="text-xs text-amber-700 dark:text-amber-300">
                      Your application submitted on{' '}
                      <span className="font-medium">
                        {new Date(request.createdAt).toLocaleString()}
                      </span>{' '}
                      is currently being reviewed by an administrator.
                    </p>
                    {request.reason && (
                      <div className="mt-2 p-3 bg-white/60 dark:bg-slate-900/60 rounded-lg text-xs text-slate-700 dark:text-slate-300 italic border border-amber-100 dark:border-amber-900/40">
                        &ldquo;{request.reason}&rdquo;
                      </div>
                    )}
                  </div>
                ) : request?.status === 'APPROVED' ? (
                  /* APPROVED STATE */
                  <div className="p-5 rounded-xl bg-emerald-50 dark:bg-emerald-950/30 border border-emerald-200 dark:border-emerald-800/80 space-y-4">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center space-x-2 text-emerald-800 dark:text-emerald-300 font-semibold text-sm">
                        <CheckCircle2 className="w-5 h-5" />
                        <span>Request Approved!</span>
                      </div>
                      <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-emerald-200/80 text-emerald-900 dark:bg-emerald-900/60 dark:text-emerald-200">
                        APPROVED
                      </span>
                    </div>
                    <p className="text-xs text-emerald-700 dark:text-emerald-300">
                      An administrator has approved your application to become a Support Agent.
                      {request.adminNotes && (
                        <span className="block mt-1 font-medium">
                          Note from reviewer: {request.adminNotes}
                        </span>
                      )}
                    </p>
                    <div className="pt-2">
                      <Button
                        onClick={handleActivateWorkspace}
                        disabled={activating}
                        className="bg-emerald-600 hover:bg-emerald-700 text-white font-medium flex items-center space-x-2"
                      >
                        <Sparkles className="w-4 h-4" />
                        <span>{activating ? 'Activating...' : 'Activate Agent Workspace'}</span>
                      </Button>
                    </div>
                  </div>
                ) : (
                  /* REJECTED OR NO REQUEST */
                  <div className="space-y-5">
                    {request?.status === 'REJECTED' && (
                      <div className="p-5 rounded-xl bg-rose-50 dark:bg-rose-950/30 border border-rose-200 dark:border-rose-800/80 space-y-3">
                        <div className="flex items-center justify-between">
                          <div className="flex items-center space-x-2 text-rose-800 dark:text-rose-300 font-semibold text-sm">
                            <XCircle className="w-5 h-5" />
                            <span>Request Declined</span>
                          </div>
                          <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-rose-200/80 text-rose-900 dark:bg-rose-900/60 dark:text-rose-200">
                            REJECTED
                          </span>
                        </div>
                        <p className="text-xs text-rose-700 dark:text-rose-300">
                          Reviewed on {request.reviewedAt ? new Date(request.reviewedAt).toLocaleString() : 'recently'}.
                        </p>
                        {request.rejectionReason && (
                          <div className="p-3 bg-white/70 dark:bg-slate-900/60 rounded-lg border border-rose-100 dark:border-rose-900/40">
                            <span className="text-xs font-semibold text-rose-900 dark:text-rose-200 block mb-1">
                              Reason for rejection:
                            </span>
                            <p className="text-xs text-rose-800 dark:text-rose-300">
                              {request.rejectionReason}
                            </p>
                          </div>
                        )}
                        {!showApplyForm && (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => setShowApplyForm(true)}
                            className="mt-2 text-xs"
                          >
                            Apply Again
                          </Button>
                        )}
                      </div>
                    )}

                    {(!request || showApplyForm) && (
                      <form onSubmit={handleSubmitRequest} className="space-y-4">
                        <div>
                          <label
                            htmlFor="agent-reason"
                            className="block text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider mb-1"
                          >
                            Why do you want to become a Support Agent? (Optional)
                          </label>
                          <textarea
                            id="agent-reason"
                            rows={4}
                            value={reason}
                            onChange={(e) => setReason(e.target.value)}
                            maxLength={1000}
                            placeholder="Provide any relevant background, customer service experience, or technical expertise (max 1000 characters)..."
                            className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition"
                          />
                          <p className="mt-1 text-right text-xs text-slate-400">
                            {reason.length}/1000
                          </p>
                        </div>

                        <div className="flex items-center space-x-3">
                          <Button
                            type="submit"
                            disabled={submitting}
                            className="bg-indigo-600 hover:bg-indigo-700 text-white font-medium flex items-center space-x-2"
                          >
                            <Send className="w-4 h-4" />
                            <span>{submitting ? 'Submitting...' : 'Submit Agent Request'}</span>
                          </Button>
                          {request && showApplyForm && (
                            <Button
                              type="button"
                              variant="ghost"
                              onClick={() => setShowApplyForm(false)}
                            >
                              Cancel
                            </Button>
                          )}
                        </div>
                      </form>
                    )}

                    {!request && !showApplyForm && (
                      <div className="flex items-center justify-between p-4 rounded-xl bg-slate-50 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-700">
                        <div className="flex items-center space-x-3">
                          <HelpCircle className="w-5 h-5 text-slate-400" />
                          <div>
                            <p className="text-sm font-medium text-slate-800 dark:text-slate-200">
                              Become a Support Agent
                            </p>
                            <p className="text-xs text-slate-500 dark:text-slate-400">
                              Assist users, resolve tickets, and contribute to the platform.
                            </p>
                          </div>
                        </div>
                        <Button onClick={() => setShowApplyForm(true)} size="sm">
                          Apply Now
                        </Button>
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}
          </CardContent>
        </Card>
      </main>
    </div>
  );
}
