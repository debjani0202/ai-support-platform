import { useState, useEffect, useCallback, useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { getTicketsApi } from '@/lib/api/tickets';
import type {
  TicketResponse,
  TicketPriority,
  TicketCategory,
} from '@/types/ticket.types';
import type { PageResponse } from '@/types/api.types';
import { AppNavbar } from '@/components/layout/AppNavbar';
import { StatusBadge } from '@/components/badges/StatusBadge';
import { PriorityBadge } from '@/components/badges/PriorityBadge';
import { CategoryBadge } from '@/components/badges/CategoryBadge';
import { Button } from '@/components/ui/Button';
import {
  Search,
  Filter,
  PlusCircle,
  ArrowUpDown,
  RefreshCw,
  AlertCircle,
  Ticket as TicketIcon,
  ChevronLeft,
  ChevronRight,
  UserCheck,
} from 'lucide-react';
import { formatDistanceToNow, parseISO } from 'date-fns';

export function TicketListPage() {
  const { user } = useAuth();
  const navigate = useNavigate();

  // Filter & Search states
  const [searchInput, setSearchInput] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [selectedStatus, setSelectedStatus] = useState<string>('');
  const [selectedPriority, setSelectedPriority] = useState<string>('');
  const [selectedCategory, setSelectedCategory] = useState<string>('');
  const [assignedAgentId, setAssignedAgentId] = useState<string>('');

  // Pagination & Sorting states
  const [page, setPage] = useState<number>(0);
  const [size, setSize] = useState<number>(10);
  const [sortBy, setSortBy] = useState<'createdAt' | 'updatedAt' | 'priority' | 'status'>('createdAt');
  const [direction, setDirection] = useState<'asc' | 'desc'>('desc');

  // Query response state
  const [ticketData, setTicketData] = useState<PageResponse<TicketResponse> | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Debounce search input by 300ms
  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedSearch(searchInput.trim());
      setPage(0); // Reset page on search change
    }, 300);

    return () => clearTimeout(handler);
  }, [searchInput]);

  // Fetch tickets with active parameters
  const fetchTickets = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await getTicketsApi({
        page,
        size,
        sortBy,
        direction,
        search: debouncedSearch || undefined,
        status: selectedStatus || undefined,
        priority: (selectedPriority as TicketPriority) || undefined,
        category: (selectedCategory as TicketCategory) || undefined,
        assignedAgentId: user?.role === 'ADMIN' && assignedAgentId ? Number(assignedAgentId) : undefined,
      });
      setTicketData(data);
    } catch (err: unknown) {
      const apiErr = err as { message?: string };
      setError(apiErr.message || 'Failed to fetch tickets from server.');
    } finally {
      setIsLoading(false);
    }
  }, [page, size, sortBy, direction, debouncedSearch, selectedStatus, selectedPriority, selectedCategory, assignedAgentId, user?.role]);

  useEffect(() => {
    fetchTickets();
  }, [fetchTickets]);

  // Handle sort header click
  const handleSort = (field: 'createdAt' | 'updatedAt' | 'priority' | 'status') => {
    if (sortBy === field) {
      setDirection((prev) => (prev === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortBy(field);
      setDirection('desc');
    }
    setPage(0);
  };

  // Reset all filters
  const resetFilters = () => {
    setSearchInput('');
    setDebouncedSearch('');
    setSelectedStatus('');
    setSelectedPriority('');
    setSelectedCategory('');
    setAssignedAgentId('');
    setPage(0);
  };

  const hasActiveFilters = useMemo(() => {
    return !!(debouncedSearch || selectedStatus || selectedPriority || selectedCategory || assignedAgentId);
  }, [debouncedSearch, selectedStatus, selectedPriority, selectedCategory, assignedAgentId]);

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <AppNavbar />

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
        {/* Page Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
              <TicketIcon className="w-6 h-6 text-indigo-600" />
              {user?.role === 'CUSTOMER'
                ? 'My Support Tickets'
                : user?.role === 'AGENT'
                ? 'My Assigned Tickets'
                : 'All Support Tickets'}
            </h1>
            <p className="text-xs text-slate-500 mt-1">
              {user?.role === 'CUSTOMER'
                ? 'View and monitor tickets submitted by your account'
                : user?.role === 'AGENT'
                ? 'Manage work queue and tickets assigned to you'
                : 'Global ticket management and operational monitoring'}
            </p>
          </div>

          <Link to="/tickets/new">
            <Button variant="primary" className="shadow-sm">
              <PlusCircle className="w-4 h-4 mr-1.5" />
              Submit Ticket
            </Button>
          </Link>
        </div>

        {/* Filter & Search Bar */}
        <div className="bg-white p-4 rounded-xl border border-slate-200/80 shadow-xs space-y-3">
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-3">
            {/* Search input (debounced 300ms) */}
            <div className="sm:col-span-2 relative">
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                placeholder="Search title & description..."
                className="w-full pl-9 pr-3 py-2 text-xs rounded-lg border border-slate-300 bg-white placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-colors"
              />
            </div>

            {/* Status Filter */}
            <div>
              <select
                value={selectedStatus}
                onChange={(e) => {
                  setSelectedStatus(e.target.value);
                  setPage(0);
                }}
                className="w-full px-2.5 py-2 text-xs rounded-lg border border-slate-300 bg-white text-slate-700 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
              >
                <option value="">Status: All</option>
                <option value="OPEN">Open</option>
                <option value="IN_PROGRESS">In Progress</option>
                <option value="RESOLVED">Resolved</option>
                <option value="CLOSED">Closed</option>
              </select>
            </div>

            {/* Priority Filter */}
            <div>
              <select
                value={selectedPriority}
                onChange={(e) => {
                  setSelectedPriority(e.target.value);
                  setPage(0);
                }}
                className="w-full px-2.5 py-2 text-xs rounded-lg border border-slate-300 bg-white text-slate-700 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
              >
                <option value="">Priority: All</option>
                <option value="LOW">Low</option>
                <option value="MEDIUM">Medium</option>
                <option value="HIGH">High</option>
                <option value="URGENT">Urgent</option>
              </select>
            </div>

            {/* Category Filter */}
            <div>
              <select
                value={selectedCategory}
                onChange={(e) => {
                  setSelectedCategory(e.target.value);
                  setPage(0);
                }}
                className="w-full px-2.5 py-2 text-xs rounded-lg border border-slate-300 bg-white text-slate-700 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
              >
                <option value="">Category: All</option>
                <option value="ACCOUNT_ACCESS">Account Access</option>
                <option value="PAYMENT">Payment</option>
                <option value="TECHNICAL_ISSUE">Technical Issue</option>
                <option value="BUG_REPORT">Bug Report</option>
                <option value="FEATURE_REQUEST">Feature Request</option>
                <option value="GENERAL_QUERY">General Query</option>
              </select>
            </div>

            {/* ADMIN Only: Agent ID filter */}
            {user?.role === 'ADMIN' ? (
              <div>
                <input
                  type="number"
                  value={assignedAgentId}
                  onChange={(e) => {
                    setAssignedAgentId(e.target.value);
                    setPage(0);
                  }}
                  placeholder="Agent ID..."
                  className="w-full px-2.5 py-2 text-xs rounded-lg border border-slate-300 bg-white text-slate-700 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                />
              </div>
            ) : (
              <div className="hidden lg:block" />
            )}
          </div>

          {/* Active filter counter and Reset button */}
          {hasActiveFilters && (
            <div className="flex items-center justify-between pt-2 border-t border-slate-100 text-xs text-slate-500">
              <span className="flex items-center gap-1.5 text-indigo-600 font-medium">
                <Filter className="w-3.5 h-3.5" />
                Active filters applied
              </span>
              <button
                onClick={resetFilters}
                className="text-xs text-rose-600 hover:text-rose-700 font-medium hover:underline"
              >
                Clear all filters
              </button>
            </div>
          )}
        </div>

        {/* Error Banner */}
        {error && (
          <div className="p-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 flex items-center justify-between text-xs">
            <div className="flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{error}</span>
            </div>
            <Button variant="outline" size="sm" onClick={fetchTickets}>
              <RefreshCw className="w-3.5 h-3.5 mr-1" /> Retry
            </Button>
          </div>
        )}

        {/* Ticket Data Table Container */}
        <div className="bg-white rounded-xl border border-slate-200/80 shadow-card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="bg-slate-50/80 border-b border-slate-200 text-slate-600 font-semibold uppercase tracking-wider text-[11px]">
                  <th className="py-3 px-4 w-16">ID</th>
                  <th className="py-3 px-4">Title</th>
                  <th
                    className="py-3 px-4 cursor-pointer hover:bg-slate-100 transition-colors select-none"
                    onClick={() => handleSort('status')}
                  >
                    <div className="flex items-center gap-1">
                      <span>Status</span>
                      <ArrowUpDown className="w-3 h-3 text-slate-400" />
                    </div>
                  </th>
                  <th
                    className="py-3 px-4 cursor-pointer hover:bg-slate-100 transition-colors select-none"
                    onClick={() => handleSort('priority')}
                  >
                    <div className="flex items-center gap-1">
                      <span>Priority</span>
                      <ArrowUpDown className="w-3 h-3 text-slate-400" />
                    </div>
                  </th>
                  <th className="py-3 px-4">Category</th>
                  <th className="py-3 px-4">Assigned Agent</th>
                  <th
                    className="py-3 px-4 cursor-pointer hover:bg-slate-100 transition-colors select-none"
                    onClick={() => handleSort('createdAt')}
                  >
                    <div className="flex items-center gap-1">
                      <span>Created</span>
                      <ArrowUpDown className="w-3 h-3 text-slate-400" />
                    </div>
                  </th>
                </tr>
              </thead>

              <tbody className="divide-y divide-slate-100 text-slate-700">
                {isLoading ? (
                  // Loading Skeleton Rows
                  Array.from({ length: 5 }).map((_, idx) => (
                    <tr key={idx} className="animate-pulse">
                      <td className="py-4 px-4"><div className="h-4 w-8 bg-slate-200 rounded" /></td>
                      <td className="py-4 px-4"><div className="h-4 w-48 bg-slate-200 rounded" /></td>
                      <td className="py-4 px-4"><div className="h-4 w-20 bg-slate-200 rounded-full" /></td>
                      <td className="py-4 px-4"><div className="h-4 w-14 bg-slate-200 rounded" /></td>
                      <td className="py-4 px-4"><div className="h-4 w-24 bg-slate-200 rounded" /></td>
                      <td className="py-4 px-4"><div className="h-4 w-20 bg-slate-200 rounded" /></td>
                      <td className="py-4 px-4"><div className="h-4 w-20 bg-slate-200 rounded" /></td>
                    </tr>
                  ))
                ) : ticketData && ticketData.content.length > 0 ? (
                  // Real Tickets
                  ticketData.content.map((ticket) => (
                    <tr
                      key={ticket.id}
                      onClick={() => navigate(`/tickets/${ticket.id}`)}
                      className="hover:bg-indigo-50/30 cursor-pointer transition-colors group"
                    >
                      <td className="py-3.5 px-4 font-mono font-medium text-slate-500 group-hover:text-indigo-600">
                        #{ticket.id}
                      </td>
                      <td className="py-3.5 px-4 font-medium text-slate-900 group-hover:text-indigo-600 transition-colors">
                        {ticket.title}
                      </td>
                      <td className="py-3.5 px-4">
                        <StatusBadge status={ticket.status} />
                      </td>
                      <td className="py-3.5 px-4">
                        <PriorityBadge priority={ticket.priority} />
                      </td>
                      <td className="py-3.5 px-4">
                        <CategoryBadge category={ticket.category} />
                      </td>
                      <td className="py-3.5 px-4">
                        {ticket.assignedTo !== null ? (
                          <span className="inline-flex items-center gap-1 font-mono text-slate-700 bg-slate-100 px-2 py-0.5 rounded text-[11px]">
                            <UserCheck className="w-3 h-3 text-slate-400" />
                            Agent #{ticket.assignedTo}
                          </span>
                        ) : (
                          <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium bg-amber-50 text-amber-700 border border-amber-200/60">
                            Unassigned
                          </span>
                        )}
                      </td>
                      <td className="py-3.5 px-4 text-slate-500 whitespace-nowrap">
                        {formatDistanceToNow(parseISO(ticket.createdAt), { addSuffix: true })}
                      </td>
                    </tr>
                  ))
                ) : (
                  // Empty State
                  <tr>
                    <td colSpan={7} className="py-12 px-4 text-center">
                      <div className="max-w-sm mx-auto flex flex-col items-center justify-center space-y-3">
                        <div className="p-3 rounded-full bg-slate-100 text-slate-400">
                          <TicketIcon className="w-8 h-8" />
                        </div>
                        <h3 className="text-sm font-semibold text-slate-900">
                          {hasActiveFilters ? 'No matching tickets' : 'No tickets found'}
                        </h3>
                        <p className="text-xs text-slate-500">
                          {hasActiveFilters
                            ? 'Try adjusting your search criteria or reset your filters.'
                            : user?.role === 'CUSTOMER'
                            ? 'You have not submitted any support tickets yet.'
                            : 'There are currently no tickets in this queue.'}
                        </p>
                        {hasActiveFilters ? (
                          <Button variant="outline" size="sm" onClick={resetFilters}>
                            Clear filters
                          </Button>
                        ) : (
                          <Link to="/tickets/new">
                            <Button variant="primary" size="sm">
                              Submit your first ticket
                            </Button>
                          </Link>
                        )}
                      </div>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          {/* Pagination Footer */}
          {ticketData && ticketData.totalPages > 0 && (
            <div className="p-4 bg-slate-50/60 border-t border-slate-200 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-slate-500">
              {/* Accurate count sourced strictly from totalElements */}
              <div>
                Showing{' '}
                <span className="font-semibold text-slate-700">
                  {ticketData.totalElements === 0 ? 0 : ticketData.page * ticketData.size + 1}
                </span>{' '}
                to{' '}
                <span className="font-semibold text-slate-700">
                  {Math.min((ticketData.page + 1) * ticketData.size, ticketData.totalElements)}
                </span>{' '}
                of <span className="font-semibold text-slate-700">{ticketData.totalElements}</span> tickets
              </div>

              <div className="flex items-center gap-3">
                {/* Page Size Selector */}
                <div className="flex items-center gap-1.5">
                  <span>Rows:</span>
                  <select
                    value={size}
                    onChange={(e) => {
                      setSize(Number(e.target.value));
                      setPage(0);
                    }}
                    className="px-2 py-1 rounded border border-slate-300 bg-white text-slate-700 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                  >
                    <option value={10}>10</option>
                    <option value={20}>20</option>
                    <option value={50}>50</option>
                  </select>
                </div>

                {/* Page Navigation Controls */}
                <div className="flex items-center gap-1">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={ticketData.first || isLoading}
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    title="Previous page"
                  >
                    <ChevronLeft className="w-4 h-4" />
                  </Button>
                  <span className="px-2 font-medium text-slate-700">
                    Page {ticketData.page + 1} of {ticketData.totalPages}
                  </span>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={ticketData.last || isLoading}
                    onClick={() => setPage((p) => Math.min(ticketData.totalPages - 1, p + 1))}
                    title="Next page"
                  >
                    <ChevronRight className="w-4 h-4" />
                  </Button>
                </div>
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
