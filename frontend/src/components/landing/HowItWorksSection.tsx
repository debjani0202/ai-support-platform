import { ArrowRight } from 'lucide-react';
import submitImg from '@/assets/submit.png';
import desicionImg from '@/assets/desicion.png';
import accomplishImg from '@/assets/accomplish.png';

interface Step {
  stepNumber: string;
  title: string;
  description: string;
  image: string;
  boxClass: string;
}

const STEPS: Step[] = [
  {
    stepNumber: '1',
    title: 'Submit a Ticket',
    description: 'Describe your issue with the necessary details.',
    image: submitImg,
    boxClass: 'bg-purple-100/70',
  },
  {
    stepNumber: '2',
    title: 'Get AI Assistance',
    description: 'Our AI automatically categorizes and routes your ticket to the right team.',
    image: desicionImg,
    boxClass: 'bg-sky-100/70',
  },
  {
    stepNumber: '3',
    title: 'Track & Resolve',
    description: 'Stay updated and get notified until your issue is resolved.',
    image: accomplishImg,
    boxClass: 'bg-blue-100/70',
  },
];

export function HowItWorksSection() {
  return (
    <section id="how-it-works" className="py-20 sm:py-28 bg-[#f8faff] border-y border-slate-100/80 relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="text-center max-w-2xl mx-auto mb-16 sm:mb-20">
          <span className="text-xs font-bold uppercase tracking-widest text-blue-600">
            How It Works
          </span>
          <h2 className="mt-2 text-3xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
            Get Support in 3 Simple Steps
          </h2>
          <p className="mt-3.5 text-sm sm:text-base text-slate-500 leading-relaxed">
            From raising a ticket to resolution — it's quick and easy.
          </p>
        </div>

        {/* 3 Steps Container */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-10 lg:gap-12 relative items-start">
          {STEPS.map((step, index) => (
            <div key={step.stepNumber} className="relative flex flex-col items-center text-center">
              {/* Step indicator pill & Icon Container */}
              <div className="flex items-center justify-center gap-3.5 mb-6">
                <span className="w-11 h-11 rounded-full bg-[#ede9fe] text-[#4f46e5] font-black text-base flex items-center justify-center shadow-xs">
                  {step.stepNumber}
                </span>
                <div
                  className={`w-14 h-14 rounded-2xl flex items-center justify-center p-3 shadow-xs ${step.boxClass}`}
                >
                  <img
                    src={step.image}
                    alt=""
                    aria-hidden="true"
                    className="w-full h-full object-contain"
                  />
                </div>
              </div>

              {/* Title & Description */}
              <h3 className="text-lg font-bold text-slate-900 tracking-tight mb-2.5">
                {step.title}
              </h3>
              <p className="text-xs sm:text-sm text-slate-500 max-w-xs leading-relaxed">
                {step.description}
              </p>

              {/* Connecting flow arrow for steps 1 and 2 (desktop only) */}
              {index < STEPS.length - 1 && (
                <div
                  className="hidden md:flex absolute top-6 -right-6 lg:-right-7 text-slate-300 pointer-events-none"
                  aria-hidden="true"
                >
                  <ArrowRight className="w-6 h-6" />
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
