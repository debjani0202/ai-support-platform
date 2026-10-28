import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link } from 'react-router-dom';
import { forgotPasswordSchema, type ForgotPasswordFormValues } from '@/lib/validation/auth.schema';
import { forgotPasswordApi } from '@/lib/api/auth';
import { AuthLayout } from '@/components/layout/AuthLayout';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { AlertCircle, CheckCircle2, ArrowLeft } from 'lucide-react';
import { toast } from 'sonner';

export function ForgotPasswordPage() {
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [isSubmitted, setIsSubmitted] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ForgotPasswordFormValues>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: {
      email: '',
    },
  });

  const onSubmit = async (data: ForgotPasswordFormValues) => {
    setGeneralError(null);
    try {
      const response = await forgotPasswordApi({
        email: data.email,
      });

      setIsSubmitted(true);
      setSuccessMessage(response.message || 'If an account exists with this email address, a password reset link has been sent.');
      toast.success('Password reset instructions sent');
    } catch (err: unknown) {
      const apiErr = err as {
        status?: number;
        message?: string;
        errors?: Record<string, string>;
      };

      if (apiErr.errors?.email) {
        setError('email', { message: apiErr.errors.email });
      } else if (apiErr.message) {
        setGeneralError(apiErr.message);
      } else {
        setGeneralError('Unable to process your request. Please try again.');
      }
    }
  };

  return (
    <AuthLayout
      title="Forgot your password?"
      subtitle="Enter your email to receive a password reset link"
    >
      {isSubmitted ? (
        <div className="space-y-5">
          <div className="rounded-lg bg-emerald-50 border border-emerald-200/80 p-4 flex items-start gap-3 text-emerald-800 text-xs">
            <CheckCircle2 className="w-5 h-5 shrink-0 mt-0.5 text-emerald-600" />
            <div className="space-y-1">
              <p className="font-medium text-emerald-900">Check your inbox</p>
              <p className="text-emerald-700 leading-relaxed">{successMessage}</p>
            </div>
          </div>

          <div className="pt-2">
            <Link to="/login" className="block w-full">
              <Button type="button" variant="outline" fullWidth>
                <ArrowLeft className="w-4 h-4 mr-2" />
                Return to Sign In
              </Button>
            </Link>
          </div>
        </div>
      ) : (
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-5" noValidate>
          {generalError && (
            <div className="rounded-lg bg-rose-50 border border-rose-200/80 p-3.5 flex items-start gap-2.5 text-rose-700 text-xs">
              <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
              <span>{generalError}</span>
            </div>
          )}

          <Input
            label="Email address"
            type="email"
            autoComplete="email"
            placeholder="name@example.com"
            {...register('email')}
            error={errors.email?.message}
            disabled={isSubmitting}
          />

          <Button
            type="submit"
            fullWidth
            isLoading={isSubmitting}
            className="h-11 rounded-xl bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 text-white font-semibold text-sm shadow-md shadow-blue-500/25 transition-all"
          >
            Send Reset Link
          </Button>

          <div className="pt-2 text-center text-xs text-slate-500">
            Remember your password?{' '}
            <Link
              to="/login"
              className="font-medium text-blue-600 hover:text-blue-500 hover:underline"
            >
              Sign in
            </Link>
          </div>
        </form>
      )}
    </AuthLayout>
  );
}
