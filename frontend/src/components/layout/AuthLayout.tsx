import React from 'react';
import { ShieldCheck } from 'lucide-react';

interface AuthLayoutProps {
  children: React.ReactNode;
  title: string;
  subtitle: string;
}

export function AuthLayout({ children, title, subtitle }: AuthLayoutProps) {
  return (
    <div
      className="min-h-screen flex flex-col justify-center items-center px-4 sm:px-6 lg:px-8 py-12 relative overflow-hidden"
      style={{
        background:
          'radial-gradient(circle at 12% 18%, #dbeafe 0%, transparent 45%), radial-gradient(circle at 88% 85%, #fed7aa 0%, transparent 48%), radial-gradient(circle at 88% 18%, #fce7f3 0%, transparent 45%), radial-gradient(circle at 18% 85%, #ede9fe 0%, transparent 45%), #f8fafc',
      }}
    >
      <div className="w-full max-w-sm sm:max-w-md mx-auto">
        <div className="text-center mb-6">
          <div className="inline-flex items-center justify-center p-3 rounded-2xl bg-white/90 border border-blue-100 shadow-xs text-blue-600 mb-3.5 backdrop-blur-xs">
            <ShieldCheck className="w-7 h-7 text-blue-600" />
          </div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-900">{title}</h2>
          <p className="mt-1 text-xs text-slate-500">{subtitle}</p>
        </div>

        <div className="bg-white rounded-3xl shadow-xl shadow-slate-200/50 border border-slate-100/80 p-7 sm:p-9">
          {children}
        </div>
      </div>
    </div>
  );
}
