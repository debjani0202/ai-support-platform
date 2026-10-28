import { Link } from 'react-router-dom';
import { BrandLogo } from '@/components/common/BrandLogo';

export function LandingFooter() {
  const handleScrollTo = (sectionId: string) => {
    const element = document.getElementById(sectionId);
    if (element) {
      element.scrollIntoView({ behavior: 'smooth' });
    }
  };

  return (
    <footer id="contact" className="border-t border-slate-100/90 bg-white py-14 sm:py-18">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 md:grid-cols-12 gap-10 lg:gap-12 mb-14">
          {/* Brand Info Column */}
          <div className="md:col-span-6 space-y-3.5">
            <div className="flex items-center gap-2.5">
              <BrandLogo size={24} />
              <span className="font-bold text-slate-900 tracking-tight text-base">
                AI Support <span className="text-blue-600">Platform</span>
              </span>
            </div>
            <p className="text-xs sm:text-sm text-slate-500 max-w-sm leading-relaxed">
              An AI-powered support ticket management platform designed for faster resolutions
              and better customer experiences.
            </p>
          </div>

          {/* Product Navigation Column */}
          <div className="md:col-span-3 space-y-3.5">
            <h4 className="text-xs font-bold uppercase tracking-wider text-slate-900">
              Product
            </h4>
            <ul className="space-y-2.5 text-xs sm:text-sm text-slate-500">
              <li>
                <button
                  type="button"
                  onClick={() => handleScrollTo('features')}
                  className="hover:text-blue-600 transition-colors cursor-pointer"
                >
                  Features
                </button>
              </li>
              <li>
                <button
                  type="button"
                  onClick={() => handleScrollTo('how-it-works')}
                  className="hover:text-blue-600 transition-colors cursor-pointer"
                >
                  How It Works
                </button>
              </li>
              <li>
                <Link to="/login" className="hover:text-blue-600 transition-colors">
                  Sign In
                </Link>
              </li>
              <li>
                <Link to="/login" className="hover:text-blue-600 transition-colors">
                  Get Started
                </Link>
              </li>
            </ul>
          </div>

          {/* Support Platform Navigation Column */}
          <div className="md:col-span-3 space-y-3.5">
            <h4 className="text-xs font-bold uppercase tracking-wider text-slate-900">
              Support
            </h4>
            <ul className="space-y-2.5 text-xs sm:text-sm text-slate-500">
              <li>
                <Link to="/login" className="hover:text-blue-600 transition-colors">
                  Submit a Ticket
                </Link>
              </li>
              <li>
                <Link to="/login" className="hover:text-blue-600 transition-colors">
                  View Tickets
                </Link>
              </li>
            </ul>
          </div>
        </div>

        {/* Bottom Bar */}
        <div className="border-t border-slate-100 pt-8 flex flex-col sm:flex-row items-center justify-between gap-4 text-xs text-slate-400">
          <p>© 2026 AI Support Platform. All rights reserved.</p>
          <p className="text-slate-500 font-medium">Smarter Support. Faster Resolutions.</p>
        </div>
      </div>
    </footer>
  );
}
