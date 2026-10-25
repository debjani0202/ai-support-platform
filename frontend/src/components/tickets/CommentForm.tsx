import React, { useState } from 'react';
import { createTicketCommentApi } from '@/lib/api/comments';
import type { CommentResponse } from '@/types/comment.types';
import { Button } from '@/components/ui/Button';
import { Send, AlertCircle } from 'lucide-react';
import { toast } from 'sonner';

interface CommentFormProps {
  ticketId: number;
  onCommentAdded: (comment: CommentResponse) => void;
}

export function CommentForm({ ticketId, onCommentAdded }: CommentFormProps) {
  const [content, setContent] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = content.trim();

    if (!trimmed) {
      setError('Comment cannot be empty.');
      return;
    }

    if (trimmed.length > 5000) {
      setError('Comment cannot exceed 5000 characters.');
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      const newComment = await createTicketCommentApi(ticketId, { content: trimmed });
      setContent('');
      toast.success('Comment added to conversation.');
      onCommentAdded(newComment);
    } catch (err: unknown) {
      const apiErr = err as { message?: string; errors?: Record<string, string> };
      if (apiErr.errors?.content) {
        setError(apiErr.errors.content);
      } else if (apiErr.message) {
        setError(apiErr.message);
      } else {
        setError('Failed to post comment. Please try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-3">
      {error && (
        <div className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      <div>
        <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-1.5">
          Add Reply / Note
        </label>
        <textarea
          rows={3}
          value={content}
          onChange={(e) => {
            setContent(e.target.value);
            if (error) setError(null);
          }}
          disabled={isSubmitting}
          placeholder="Type your message or update here (max 5000 characters)..."
          className="w-full rounded-lg border border-slate-300 bg-white p-3 text-sm text-slate-900 placeholder:text-slate-400 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 disabled:bg-slate-50 transition-colors shadow-xs"
        />
        <div className="flex justify-between items-center text-[11px] text-slate-400 mt-1">
          <span>Markdown formatting supported as plain text</span>
          <span>{content.length} / 5000 chars</span>
        </div>
      </div>

      <div className="flex justify-end">
        <Button
          type="submit"
          variant="primary"
          size="sm"
          isLoading={isSubmitting}
          disabled={isSubmitting || !content.trim()}
        >
          <Send className="w-3.5 h-3.5 mr-1.5" />
          Post Reply
        </Button>
      </div>
    </form>
  );
}
