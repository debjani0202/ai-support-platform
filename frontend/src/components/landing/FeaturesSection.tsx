import { Link } from 'react-router-dom';
import { ArrowRight, Ticket } from 'lucide-react';
import decisionMakingImg from '@/assets/decision-making human.png';
import bellImg from '@/assets/bell.png';
import consultingImg from '@/assets/consulting.png';

interface FeatureCard {
  id: string;
  title: string;
  description: string;
  image?: string;
  iconFallback?: 'ticket';
  boxClass: string;
}

const FEATURES: FeatureCard[] = [
  {
    id: 'smart-ticket-management',
    title: 'Smart Ticket Management',
    description:
      'Create, track, and manage support tickets with an intuitive and easy-to-use interface.',
    iconFallback: 'ticket',
    boxClass: 'bg-purple-100/70 text-purple-600',
  },
  {
    id: 'ai-powered-assistance',
    title: 'AI-Powered Assistance',
    description:
      'Get intelligent suggestions and automatic ticket categorization using AI.',
    image: decisionMakingImg,
    boxClass: 'bg-sky-100/70 text-sky-600',
  },
  {
    id: 'real-time-notifications',
    title: 'Real-Time Notifications',
    description:
      'Stay updated with instant notifications on ticket progress and replies.',
    image: bellImg,
    boxClass: 'bg-amber-100/70 text-amber-600',
  },
  {
    id: 'secure-reliable',
    title: 'Secure & Reliable',
    description:
      'Built with industry-standard security practices to keep your data safe.',
    image: consultingImg,
    boxClass: 'bg-emerald-100/70 text-emerald-600',
  },
];

export function FeaturesSection() {
  return (
    <section id="features" className="py-20 sm:py-28 bg-white relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="text-center max-w-2xl mx-auto mb-14 sm:mb-20">
          <span className="text-xs font-bold uppercase tracking-widest text-blue-600">
            Features
          </span>
          <h2 className="mt-2 text-3xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
            Everything you need for efficient support
          </h2>
          <p className="mt-3.5 text-sm sm:text-base text-slate-500 leading-relaxed">
            Powerful features to help customers and support teams work together seamlessly.
          </p>
        </div>

        {/* 4 Cards Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6 lg:gap-8">
          {FEATURES.map((feature) => (
            <div
              key={feature.id}
              className="bg-white rounded-3xl border border-slate-100/90 p-7 sm:p-8 shadow-[0_4px_25px_-4px_rgba(0,0,0,0.04)] hover:shadow-[0_12px_30px_-6px_rgba(0,0,0,0.07)] transition-all duration-200 flex flex-col justify-between"
            >
              <div>
                <div
                  className={`w-14 h-14 rounded-2xl flex items-center justify-center p-3 mb-6 shadow-xs ${feature.boxClass}`}
                >
                  {feature.image ? (
                    <img
                      src={feature.image}
                      alt=""
                      aria-hidden="true"
                      className="w-full h-full object-contain"
                    />
                  ) : (
                    <Ticket className="w-7 h-7 text-purple-600" aria-hidden="true" />
                  )}
                </div>
                <h3 className="text-lg font-bold text-slate-900 tracking-tight mb-2.5">
                  {feature.title}
                </h3>
                <p className="text-xs sm:text-sm text-slate-500 leading-relaxed">
                  {feature.description}
                </p>
              </div>

              <div className="pt-6 mt-auto">
                <Link
                  to="/login"
                  className="inline-flex items-center gap-1.5 text-xs sm:text-sm font-semibold text-blue-600 hover:text-blue-700 group transition-colors"
                >
                  <span>Learn more</span>
                  <ArrowRight className="w-3.5 h-3.5 transition-transform group-hover:translate-x-1" />
                </Link>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
