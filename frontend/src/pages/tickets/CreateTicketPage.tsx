import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate, Link } from 'react-router-dom';
import { createTicketSchema, type CreateTicketFormValues } from '@/lib/validation/ticket.schema';
import { createTicketApi } from '@/lib/api/tickets';
import { AppNavbar } from '@/components/layout/AppNavbar';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { Sparkles, AlertCircle, ArrowLeft } from 'lucide-react';
import { toast } from 'sonner';

export function CreateTicketPage() {
  const navigate = useNavigate();
  const [generalError, setGeneralError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<CreateTicketFormValues>({
    resolver: zodResolver(createTicketSchema),
    defaultValues: {
      title: '',
      description: '',
      priority: 'MEDIUM',
      category: null,
    },
  });

  const selectedCategory = watch('category');

  const onSubmit = async (values: CreateTicketFormValues) => {
    setGeneralError(null);
    try {
      // If category is null or empty, backend AI classifier will categorize it
      const response = await createTicketApi({
        title: values.title.trim(),
        description: values.description.trim(),
        priority: values.priority,
        category: values.category || null,
      });

      toast.success(
        `Ticket #${response.id} created successfully! Categorized as ${response.category}.`
      );
      navigate(`/tickets/${response.id}`);
    } catch (err: unknown) {
      const apiErr = err as {
        message?: string;
        errors?: Record<string, string>;
      };

      if (apiErr.errors) {
        Object.entries(apiErr.errors).forEach(([field, msg]) => {
          if (field === 'title' || field === 'description' || field === 'priority') {
            setError(field, { message: msg });
          }
        });
      }

      if (apiErr.message) {
        setGeneralError(apiErr.message);
      } else {
        setGeneralError('Failed to create ticket. Please check your input and try again.');
      }
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <AppNavbar />

      <main className="flex-1 max-w-3xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
        <div>
          <Link
            to="/tickets"
            className="inline-flex items-center gap-1.5 text-xs text-slate-500 hover:text-slate-800 transition-colors mb-3"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            Back to Tickets
          </Link>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
            Submit New Support Ticket
          </h1>
          <p className="text-xs text-slate-500 mt-1">
            Describe your issue. Our AI classification pipeline will triage and tag your ticket.
          </p>
        </div>

        <Card>
          <CardHeader>
            <CardTitle>Ticket Details</CardTitle>
            <CardDescription>
              Provide clear and detailed information to help our support team resolve your issue quickly.
            </CardDescription>
          </CardHeader>

          <CardContent>
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-5" noValidate>
              {generalError && (
                <div className="rounded-lg bg-rose-50 border border-rose-200 p-3.5 flex items-start gap-2.5 text-rose-700 text-xs">
                  <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                  <span>{generalError}</span>
                </div>
              )}

              {/* Title input */}
              <Input
                label="Ticket Title"
                placeholder="e.g. Cannot process credit card invoice payment"
                {...register('title')}
                error={errors.title?.message}
                helperText="Brief summary of the issue (max 255 characters)"
              />

              {/* Priority Select */}
              <div className="space-y-1.5 text-left">
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                  Priority Level
                </label>
                <select
                  {...register('priority')}
                  className="flex h-10 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 transition-colors shadow-xs"
                >
                  <option value="LOW">Low (General inquiry, cosmetic issue)</option>
                  <option value="MEDIUM">Medium (Normal business impact)</option>
                  <option value="HIGH">High (Major function impaired)</option>
                  <option value="URGENT">Urgent (Critical system block, billing down)</option>
                </select>
                {errors.priority && (
                  <p className="text-xs text-rose-600 font-medium">{errors.priority.message}</p>
                )}
              </div>

              {/* Category Select (with AI auto-classify option) */}
              <div className="space-y-1.5 text-left">
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                  Category (Optional)
                </label>
                <select
                  {...register('category')}
                  className="flex h-10 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 transition-colors shadow-xs"
                >
                  <option value="">⚡ Auto-classify using AI (Recommended)</option>
                  <option value="ACCOUNT_ACCESS">Account Access</option>
                  <option value="PAYMENT">Payment & Invoicing</option>
                  <option value="TECHNICAL_ISSUE">Technical Issue</option>
                  <option value="BUG_REPORT">Bug Report</option>
                  <option value="FEATURE_REQUEST">Feature Request</option>
                  <option value="GENERAL_QUERY">General Query</option>
                </select>

                {/* AI banner shown when category is empty/auto */}
                {!selectedCategory && (
                  <div className="p-3 rounded-lg bg-indigo-50/70 border border-indigo-100 flex items-start gap-2.5 text-indigo-800 text-xs mt-2">
                    <Sparkles className="w-4 h-4 text-indigo-600 shrink-0 mt-0.5" />
                    <span>
                      <strong>AI Auto-Classification Enabled:</strong> Leaving category empty sends a null value.
                      The backend will automatically analyze your title and description using regex rules and local Ollama (<code>qwen3:4b</code>).
                    </span>
                  </div>
                )}
              </div>

              {/* Description textarea */}
              <div className="space-y-1.5 text-left">
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
                  Detailed Description
                </label>
                <textarea
                  rows={6}
                  placeholder="Provide complete steps to reproduce the issue, error codes, and what you expected to happen..."
                  {...register('description')}
                  className="flex w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 transition-colors shadow-xs"
                />
                {errors.description && (
                  <p className="text-xs text-rose-600 font-medium">{errors.description.message}</p>
                )}
              </div>

              {/* Action Buttons */}
              <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-100">
                <Link to="/tickets">
                  <Button type="button" variant="outline">
                    Cancel
                  </Button>
                </Link>
                <Button type="submit" variant="primary" isLoading={isSubmitting}>
                  Create Ticket
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      </main>
    </div>
  );
}
