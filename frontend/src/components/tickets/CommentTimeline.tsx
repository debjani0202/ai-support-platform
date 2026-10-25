import type { CommentResponse } from '@/types/comment.types';
import { useAuth } from '@/hooks/useAuth';
import { User, MessageSquare, Clock } from 'lucide-react';
import { formatDistanceToNow, parseISO } from 'date-fns';

interface CommentTimelineProps {
  comments: CommentResponse[];
  isLoading: boolean;
}

export function CommentTimeline({ comments, isLoading }: CommentTimelineProps) {
  const { user } = useAuth();

  if (isLoading) {
    return (
      <div className="space-y-4 animate-pulse">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="p-4 rounded-xl bg-slate-100 border border-slate-200/60 space-y-2">
            <div className="h-4 w-32 bg-slate-200 rounded" />
            <div className="h-12 w-full bg-slate-200 rounded" />
          </div>
        ))}
      </div>
    );
  }

  if (comments.length === 0) {
    return (
      <div className="p-8 text-center bg-slate-50 rounded-xl border border-dashed border-slate-200 text-slate-500 space-y-2">
        <MessageSquare className="w-8 h-8 mx-auto text-slate-400 stroke-1" />
        <p className="text-sm font-medium text-slate-700">No comments yet</p>
        <p className="text-xs text-slate-500">
          Be the first to add a note or reply to this ticket below.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {comments.map((comment) => {
        const isAuthor = comment.authorId === user?.userId;

        return (
          <div
            key={comment.id}
            className={`p-4 rounded-xl border transition-colors ${
              isAuthor
                ? 'bg-indigo-50/40 border-indigo-100/80'
                : 'bg-white border-slate-200/80 shadow-xs'
            }`}
          >
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-2">
                <div
                  className={`p-1.5 rounded-full ${
                    isAuthor ? 'bg-indigo-100 text-indigo-700' : 'bg-slate-100 text-slate-600'
                  }`}
                >
                  <User className="w-3.5 h-3.5" />
                </div>
                <span className="text-xs font-semibold text-slate-900">
                  {comment.authorName}
                </span>
                {isAuthor && (
                  <span className="px-1.5 py-0.2 rounded text-[10px] font-semibold bg-indigo-100 text-indigo-700 border border-indigo-200/60">
                    You
                  </span>
                )}
                <span className="text-[11px] font-mono text-slate-400">
                  (User #{comment.authorId})
                </span>
              </div>

              <div className="flex items-center gap-1 text-[11px] text-slate-400">
                <Clock className="w-3 h-3" />
                <span>
                  {formatDistanceToNow(parseISO(comment.createdAt), { addSuffix: true })}
                </span>
              </div>
            </div>

            <p className="text-sm text-slate-800 whitespace-pre-wrap leading-relaxed pl-7">
              {comment.content}
            </p>
          </div>
        );
      })}
    </div>
  );
}
