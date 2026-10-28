import { Link } from 'react-router-dom';
import { ArrowRight, MessageSquare, BarChart2, Send } from 'lucide-react';

export function LandingCtaSection() {
  return (
    <section className="py-16 sm:py-24 bg-white relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="relative overflow-hidden rounded-3xl bg-gradient-to-r from-blue-100/70 via-indigo-100/50 to-purple-100/60 border border-blue-100/90 p-8 sm:p-12 lg:p-16 shadow-xs">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-10 items-center">
            {/* Left Content Column */}
            <div className="lg:col-span-7 space-y-4">
              <span className="inline-block text-xs font-bold uppercase tracking-wider text-blue-600">
                Ready to get started?
              </span>
              <h2 className="text-3xl sm:text-4xl font-extrabold text-slate-900 tracking-tight leading-tight">
                Simplify your support process today
              </h2>
              <p className="text-sm sm:text-base text-slate-600 max-w-lg leading-relaxed">
                Join our platform and experience faster, smarter, and more reliable support
                ticket management.
              </p>
              <div className="pt-3 flex flex-wrap items-center gap-3.5">
                <Link
                  to="/login"
                  className="inline-flex items-center gap-2 px-6 py-3 rounded-full text-sm font-semibold text-white bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 shadow-md shadow-blue-500/20 transition-all"
                >
                  <span>Get Started</span>
                  <ArrowRight className="w-4 h-4" />
                </Link>
                <Link
                  to="/login"
                  className="inline-flex items-center justify-center px-6 py-3 rounded-full text-sm font-semibold text-slate-700 bg-white hover:bg-slate-50 border border-slate-200/90 shadow-xs transition-all"
                >
                  Sign In
                </Link>
              </div>
            </div>

            {/* Right Decorative Illustrative Support Graphic */}
            <div
              className="lg:col-span-5 hidden sm:flex justify-center lg:justify-end relative"
              aria-hidden="true"
            >
              {/* Floating Chat Badge */}
              <div className="absolute -top-4 left-4 w-11 h-11 rounded-2xl bg-[#ede9fe] text-indigo-600 flex items-center justify-center shadow-md border border-white z-20">
                <MessageSquare className="w-5 h-5" />
              </div>

              {/* Floating Paper Plane Badge */}
              <div className="absolute -bottom-3 -right-2 w-11 h-11 rounded-2xl bg-[#ede9fe] text-purple-600 flex items-center justify-center shadow-md border border-white z-20">
                <Send className="w-5 h-5" />
              </div>

              {/* Floating Bar Chart Badge */}
              <div className="absolute -bottom-4 left-6 w-11 h-11 rounded-2xl bg-[#e0f2fe] text-blue-600 flex items-center justify-center shadow-md border border-white z-20">
                <BarChart2 className="w-5 h-5" />
              </div>

              {/* Central Illustrative Card */}
              <div className="w-full max-w-xs rounded-2xl bg-white/95 border border-slate-100 shadow-xl p-5 space-y-3 backdrop-blur-xs select-none">
                <div className="flex items-center gap-2 border-b border-slate-100 pb-3">
                  <span className="w-2.5 h-2.5 rounded-full bg-blue-500" />
                  <span className="w-2.5 h-2.5 rounded-full bg-amber-400" />
                  <span className="w-2.5 h-2.5 rounded-full bg-purple-500" />
                  <span className="ml-auto w-16 h-2 bg-slate-100 rounded-full" />
                </div>
                <div className="space-y-2 py-1">
                  <div className="h-2.5 bg-blue-100/70 rounded-full w-4/5" />
                  <div className="h-2 bg-slate-100 rounded-full w-full" />
                  <div className="h-2 bg-slate-100 rounded-full w-3/4" />
                  <div className="h-2 bg-purple-100/70 rounded-full w-2/3" />
                </div>
                <div className="pt-2 flex items-center justify-between">
                  <span className="w-20 h-2 bg-slate-100 rounded-full" />
                  <span className="w-8 h-2 bg-blue-200/80 rounded-full" />
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
