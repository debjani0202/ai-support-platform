import robotTalkingImg from '@/assets/robot-talking.png';
import realtimeImg from '@/assets/realtime.png';
import consultingImg from '@/assets/consulting.png';

interface BenefitItem {
  id: string;
  image: string;
  line1: string;
  line2: string;
  boxClass: string;
}

const BENEFITS: BenefitItem[] = [
  {
    id: 'ai-classification',
    image: robotTalkingImg,
    line1: 'AI-Assisted',
    line2: 'Ticket Classification',
    boxClass: 'bg-[#ede9fe] text-indigo-700',
  },
  {
    id: 'realtime-tracking',
    image: realtimeImg,
    line1: 'Real-Time',
    line2: 'Ticket Tracking',
    boxClass: 'bg-[#e0f2fe] text-blue-700',
  },
  {
    id: 'secure-management',
    image: consultingImg,
    line1: 'Secure & Reliable',
    line2: 'Support Management',
    boxClass: 'bg-[#dcfce7] text-emerald-700',
  },
];

export function KeyBenefits() {
  return (
    <div className="pt-10 sm:pt-14 flex flex-wrap items-center justify-center gap-6 sm:gap-10 lg:gap-16">
      {BENEFITS.map((item) => (
        <div
          key={item.id}
          className="flex items-center gap-3.5 group select-none"
          aria-label={`${item.line1} ${item.line2}`}
        >
          <div
            className={`w-11 h-11 rounded-2xl flex items-center justify-center p-2 shrink-0 shadow-xs transition-transform group-hover:scale-105 ${item.boxClass}`}
          >
            <img
              src={item.image}
              alt=""
              aria-hidden="true"
              className="w-full h-full object-contain"
            />
          </div>
          <div className="text-left font-bold text-slate-900 text-xs sm:text-sm tracking-tight leading-snug">
            <span className="block">{item.line1}</span>
            <span className="block">{item.line2}</span>
          </div>
        </div>
      ))}
    </div>
  );
}
