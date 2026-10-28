import { useState, useEffect } from 'react';
import { Sparkles } from 'lucide-react';

const MESSAGES = [
  'Smarter Support. Faster Resolutions.',
  'AI Ticket Classification in Seconds.',
  'Real-Time Updates. Zero Guesswork.',
];

export function TypingHeroBadge() {
  const [currentText, setCurrentText] = useState('');
  const [messageIndex, setMessageIndex] = useState(0);
  const [isDeleting, setIsDeleting] = useState(false);
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(false);

  // Check reduced-motion preference with defensive environment check
  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return;
    }

    try {
      const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
      setPrefersReducedMotion(mediaQuery.matches);

      const handler = (e: MediaQueryListEvent) => setPrefersReducedMotion(e.matches);
      mediaQuery.addEventListener('change', handler);
      return () => mediaQuery.removeEventListener('change', handler);
    } catch {
      // Fallback if matchMedia is not fully supported
    }
  }, []);

  useEffect(() => {
    if (prefersReducedMotion) {
      setCurrentText(MESSAGES[0]);
      return;
    }

    const targetMessage = MESSAGES[messageIndex];
    let timeout: NodeJS.Timeout;

    if (!isDeleting) {
      if (currentText.length < targetMessage.length) {
        // Typing forward
        timeout = setTimeout(() => {
          setCurrentText(targetMessage.slice(0, currentText.length + 1));
        }, 75);
      } else {
        // Pause at complete text before backspacing
        timeout = setTimeout(() => {
          setIsDeleting(true);
        }, 2000);
      }
    } else {
      if (currentText.length > 0) {
        // Deleting backward
        timeout = setTimeout(() => {
          setCurrentText(targetMessage.slice(0, currentText.length - 1));
        }, 35);
      } else {
        // Finished deleting, move to next message
        setIsDeleting(false);
        setMessageIndex((prev) => (prev + 1) % MESSAGES.length);
      }
    }

    return () => clearTimeout(timeout);
  }, [currentText, isDeleting, messageIndex, prefersReducedMotion]);

  return (
    <div
      className="inline-flex items-center gap-2 px-4 py-1 rounded-full bg-[#ede9fe]/80 border border-[#c7d2fe]/70 shadow-xs backdrop-blur-xs select-none h-8 min-w-[280px] sm:min-w-[340px] justify-center transition-all"
      aria-live="polite"
    >
      <Sparkles className="w-3.5 h-3.5 text-[#6366f1] shrink-0" aria-hidden="true" />
      <span className="text-xs sm:text-sm font-semibold text-[#3730a3] whitespace-nowrap overflow-hidden">
        {currentText}
        {!prefersReducedMotion && (
          <span className="inline-block w-0.5 h-3.5 ml-0.5 bg-[#6366f1] animate-pulse align-middle" />
        )}
      </span>
    </div>
  );
}
