import { Link } from 'react-router-dom';
import { ShieldAlert } from 'lucide-react';
import { Button } from '@/components/ui/Button';

export function ForbiddenPage() {
  return (
    <div className="min-h-screen bg-slate-50 flex flex-col items-center justify-center p-6 text-center">
      <div className="p-4 rounded-2xl bg-amber-50 text-amber-600 mb-4 border border-amber-200/60 shadow-sm">
        <ShieldAlert className="w-12 h-12" />
      </div>
      <h1 className="text-2xl font-bold text-slate-900 tracking-tight">403 — Access Denied</h1>
      <p className="mt-2 text-sm text-slate-600 max-w-md">
        Your current role does not have permission to view or manage this resource.
      </p>
      <div className="mt-6">
        <Link to="/dashboard">
          <Button variant="primary">Return to Dashboard</Button>
        </Link>
      </div>
    </div>
  );
}
