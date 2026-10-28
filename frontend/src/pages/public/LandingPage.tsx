import { Link } from 'react-router-dom';
import { ArrowRight, Mail, Sparkles } from 'lucide-react';
import { LandingNavbar } from '@/components/landing/LandingNavbar';
import { TypingHeroBadge } from '@/components/landing/TypingHeroBadge';
import { KeyBenefits } from '@/components/landing/KeyBenefits';
import { FeaturesSection } from '@/components/landing/FeaturesSection';
import { HowItWorksSection } from '@/components/landing/HowItWorksSection';
import { LandingCtaSection } from '@/components/landing/LandingCtaSection';
import { LandingFooter } from '@/components/landing/LandingFooter';

export function LandingPage() {
  return (
    <div className="min-h-screen bg-white text-slate-800 antialiased selection:bg-blue-100 selection:text-blue-900">
      {/* 1. Compact Sticky Header */}
      <LandingNavbar />

      <main>
        {/* 2. Hero Section */}
        <section
          id="hero"
          className="relative pt-12 pb-20 sm:pt-18 sm:pb-28 overflow-hidden border-b border-slate-100/80"
          style={{
            background:
              'radial-gradient(ellipse 60% 50% at 15% 20%, #dbeafe 0%, transparent 70%), radial-gradient(ellipse 60% 50% at 85% 20%, #ede9fe 0%, transparent 70%), radial-gradient(ellipse 50% 40% at 50% 40%, #eff6ff 0%, transparent 80%), #ffffff',
          }}
        >
          {/* Flanking Decorative Support Visuals (Left Flank) */}
          <div
            className="hidden xl:block absolute left-4 2xl:left-12 top-1/2 -translate-y-1/2 pointer-events-none select-none z-0"
            aria-hidden="true"
          >
            <div className="relative w-48 h-72">
              {/* 5x5 Dot Matrix Grid */}
              <svg
                width="80"
                height="80"
                viewBox="0 0 80 80"
                fill="none"
                className="absolute top-4 left-6 text-blue-300/40"
              >
                {Array.from({ length: 25 }).map((_, i) => (
                  <circle
                    key={i}
                    cx={(i % 5) * 16 + 8}
                    cy={Math.floor(i / 5) * 16 + 8}
                    r="2.5"
                    fill="currentColor"
                  />
                ))}
              </svg>

              {/* Floating Message Envelope Card */}
              <div className="absolute top-16 left-0 -rotate-12 bg-white/95 rounded-2xl p-3 shadow-xl shadow-indigo-100/80 border border-slate-100/80 backdrop-blur-xs flex items-center justify-center">
                <div className="w-12 h-10 rounded-xl bg-blue-50 border border-blue-100 flex items-center justify-center text-blue-600">
                  <Mail className="w-5 h-5" />
                </div>
              </div>

              {/* Small Floating 3D Cube / Diamond Accent */}
              <div className="absolute bottom-10 right-4 w-9 h-9 rounded-xl bg-gradient-to-tr from-indigo-500 to-purple-400 rotate-45 shadow-md shadow-indigo-200/60 opacity-80" />
            </div>
          </div>

          {/* Flanking Decorative Support Visuals (Right Flank) */}
          <div
            className="hidden xl:block absolute right-4 2xl:left-auto 2xl:right-12 top-1/2 -translate-y-1/2 pointer-events-none select-none z-0"
            aria-hidden="true"
          >
            <div className="relative w-64 h-80">
              {/* Floating Support Ticket Document Card */}
              <div className="absolute top-8 right-8 rotate-6 bg-white/95 rounded-2xl p-4 shadow-xl shadow-purple-100/80 border border-slate-100/80 backdrop-blur-xs w-32 space-y-2.5">
                <div className="flex items-center justify-between pb-1 border-b border-slate-100">
                  <Sparkles className="w-4 h-4 text-purple-600" />
                  <span className="w-8 h-1.5 bg-blue-500 rounded-full" />
                </div>
                <div className="h-2 bg-blue-500 rounded-full w-4/5" />
                <div className="h-1.5 bg-slate-200 rounded-full w-full" />
                <div className="h-1.5 bg-slate-200 rounded-full w-3/4" />
                <div className="h-1.5 bg-purple-200 rounded-full w-1/2" />
              </div>

              {/* Small Floating Purple Envelope */}
              <div className="absolute top-0 right-0 rotate-12 bg-white/95 rounded-xl p-2.5 shadow-lg shadow-purple-100/60 border border-slate-100">
                <div className="w-8 h-7 rounded-lg bg-purple-50 flex items-center justify-center text-purple-600">
                  <Mail className="w-4 h-4" />
                </div>
              </div>

              {/* Curved Connecting Accent Vector Line with Nodes */}
              <svg
                width="160"
                height="100"
                viewBox="0 0 160 100"
                fill="none"
                className="absolute bottom-4 right-4"
              >
                <path
                  d="M10 80 C 60 90, 100 20, 150 30"
                  stroke="#93c5fd"
                  strokeWidth="2"
                  strokeDasharray="4 4"
                />
                <circle cx="10" cy="80" r="4" fill="#3b82f6" />
                <circle cx="150" cy="30" r="4" fill="#6366f1" />
              </svg>

              {/* 5x5 Dot Matrix Grid */}
              <svg
                width="80"
                height="80"
                viewBox="0 0 80 80"
                fill="none"
                className="absolute bottom-2 left-2 text-indigo-300/40"
              >
                {Array.from({ length: 25 }).map((_, i) => (
                  <circle
                    key={i}
                    cx={(i % 5) * 16 + 8}
                    cy={Math.floor(i / 5) * 16 + 8}
                    r="2.5"
                    fill="currentColor"
                  />
                ))}
              </svg>
            </div>
          </div>

          {/* Central Hero Content */}
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 text-center relative z-10">
            {/* Live Typing Animated Badge */}
            <div className="flex justify-center mb-6 sm:mb-8">
              <TypingHeroBadge />
            </div>

            {/* 3-Line Centered Heading matching reference */}
            <h1 className="text-4xl sm:text-5xl lg:text-6xl font-black text-slate-900 tracking-tight leading-tight sm:leading-tight max-w-4xl mx-auto mb-6">
              <span className="block">AI-Powered</span>
              <span className="block bg-gradient-to-r from-blue-600 via-indigo-600 to-purple-600 bg-clip-text text-transparent my-1 sm:my-2">
                Support Ticket
              </span>
              <span className="block">Management</span>
            </h1>

            {/* Subtitle */}
            <p className="text-sm sm:text-base lg:text-lg text-slate-600 max-w-2xl mx-auto leading-relaxed mb-9">
              Create, track, and resolve support tickets with the power of AI. Get faster responses,
              better collaboration, and a smoother support experience for your customers and teams.
            </p>

            {/* Hero Action Buttons */}
            <div className="flex flex-wrap items-center justify-center gap-4 mb-8">
              <Link
                to="/login"
                className="inline-flex items-center gap-2 px-8 py-3.5 rounded-full text-sm sm:text-base font-semibold text-white bg-gradient-to-r from-blue-600 via-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 shadow-md shadow-blue-500/25 transition-all"
              >
                <span>Get Started</span>
                <ArrowRight className="w-4 h-4" />
              </Link>
              <Link
                to="/login"
                className="inline-flex items-center justify-center px-8 py-3.5 rounded-full text-sm sm:text-base font-semibold text-slate-700 bg-white hover:bg-slate-50 border border-slate-200/90 shadow-xs transition-all"
              >
                Sign In
              </Link>
            </div>

            {/* 3 Key Benefits Strip */}
            <KeyBenefits />
          </div>
        </section>

        {/* 3. Features Grid Section */}
        <FeaturesSection />

        {/* 4. How It Works 3-Step Section */}
        <HowItWorksSection />

        {/* 5. Bottom Call to Action Section */}
        <LandingCtaSection />
      </main>

      {/* 6. Honest Clean Footer */}
      <LandingFooter />
    </div>
  );
}
