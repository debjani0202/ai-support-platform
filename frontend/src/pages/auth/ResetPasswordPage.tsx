import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useSearchParams } from 'react-router-dom';
import { resetPasswordSchema, type ResetPasswordFormValues } from '@/lib/validation/auth.schema';
import { verifyResetTokenApi, resetPasswordApi } from '@/lib/api/auth';
import { AuthLayout } from '@/components/layout/AuthLayout';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { AlertCircle, CheckCircle2, ArrowLeft } from 'lucide-react';
import { toast } from 'sonner';

type TokenStatus = 'verifying' | 'valid' | 'invalid' | 'missing';

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') || '';

  const [tokenStatus, setTokenStatus] = useState<TokenStatus>('verifying');
  const [tokenErrorMessage, setTokenErrorMessage] = useState<string>('');
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [isSuccess, setIsSuccess] = useState(false);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ResetPasswordFormValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: {
      password: '',
      confirmPassword: '',
    },
  });

  useEffect(() => {
    if (!token.trim()) {
      setTokenStatus('missing');
      setTokenErrorMessage('No password reset token was provided. Please request a new link.');
      return;
    }

    let isMounted = true;

    async function checkToken() {
      try {
        const response = await verifyResetTokenApi(token);
        if (!isMounted) return;

        if (response.valid) {
          setTokenStatus('valid');
        } else {
          setTokenStatus('invalid');
          setTokenErrorMessage(response.message || 'This password reset link is invalid or has expired.');
        }
      } catch {
        if (!isMounted) return;
        setTokenStatus('invalid');
        setTokenErrorMessage('Unable to verify reset link. It may be invalid or expired.');
      }
    }

    checkToken();

    return () => {
      isMounted = false;
    };
  }, [token]);

  const onSubmit = async (data: ResetPasswordFormValues) => {
    setGeneralError(null);
    try {
      await resetPasswordApi({
        token,
        newPassword: data.password,
      });

      setIsSuccess(true);
      toast.success('Password reset successfully. You can now sign in.');
    } catch (err: unknown) {
      const apiErr = err as {
        status?: number;
        message?: string;
        errors?: Record<string, string>;
      };

      if (apiErr.errors?.newPassword) {
        setError('password', { message: apiErr.errors.newPassword });
      } else if (apiErr.message === 'New password must be different from current password') {
        setError('password', { message: 'New password must be different from current password.' });
      } else if (apiErr.message) {
        setGeneralError(apiErr.message);
      } else {
        setGeneralError('Failed to reset password. The link may have expired.');
      }
    }
  };

  return (
    <AuthLayout
      title="Create new password"
      subtitle="Enter a new password for your account"
    >
      {tokenStatus === 'verifying' && (
        <div className="py-8 flex flex-col items-center justify-center space-y-3">
          <div
            className="h-8 w-8 animate-spin rounded-full border-4 border-indigo-600 border-t-transparent"
            role="status"
            aria-label="Verifying reset link"
          />
          <p className="text-xs text-slate-500">Verifying password reset link...</p>
        </div>
      )}

      {(tokenStatus === 'invalid' || tokenStatus === 'missing') && (
        <div className="space-y-5">
          <div className="rounded-lg bg-rose-50 border border-rose-200/80 p-4 flex items-start gap-3 text-rose-700 text-xs">
            <AlertCircle className="w-5 h-5 shrink-0 mt-0.5 text-rose-600" />
            <div className="space-y-1">
              <p className="font-medium text-rose-900">Reset link not available</p>
              <p className="text-rose-700 leading-relaxed">{tokenErrorMessage}</p>
            </div>
          </div>

          <div className="pt-2 flex flex-col sm:flex-row gap-2">
            <Link to="/forgot-password" className="flex-1">
              <Button type="button" variant="primary" fullWidth>
                Request New Link
              </Button>
            </Link>
            <Link to="/login" className="flex-1">
              <Button type="button" variant="outline" fullWidth>
                <ArrowLeft className="w-4 h-4 mr-2" />
                Back to Sign In
              </Button>
            </Link>
          </div>
        </div>
      )}

      {tokenStatus === 'valid' && isSuccess && (
        <div className="space-y-5">
          <div className="rounded-lg bg-emerald-50 border border-emerald-200/80 p-4 flex items-start gap-3 text-emerald-800 text-xs">
            <CheckCircle2 className="w-5 h-5 shrink-0 mt-0.5 text-emerald-600" />
            <div className="space-y-1">
              <p className="font-medium text-emerald-900">Password successfully updated</p>
              <p className="text-emerald-700 leading-relaxed">
                Your password has been changed. You can now use your new password to sign in.
              </p>
            </div>
          </div>

          <div className="pt-2">
            <Link to="/login" className="block w-full">
              <Button type="button" variant="primary" fullWidth>
                Sign In Now &rarr;
              </Button>
            </Link>
          </div>
        </div>
      )}

      {tokenStatus === 'valid' && !isSuccess && (
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          {generalError && (
            <div className="rounded-lg bg-rose-50 border border-rose-200/80 p-3.5 flex items-start gap-2.5 text-rose-700 text-xs">
              <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
              <span>{generalError}</span>
            </div>
          )}

          <Input
            label="New Password"
            type="password"
            autoComplete="new-password"
            placeholder="Enter new password (8-72 characters)"
            {...register('password')}
            error={errors.password?.message}
            disabled={isSubmitting}
          />

          <Input
            label="Confirm New Password"
            type="password"
            autoComplete="new-password"
            placeholder="Re-enter your new password"
            {...register('confirmPassword')}
            error={errors.confirmPassword?.message}
            disabled={isSubmitting}
          />

          <Button
            type="submit"
            fullWidth
            isLoading={isSubmitting}
            className="h-11 rounded-xl bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 text-white font-semibold text-sm shadow-md shadow-blue-500/25 transition-all"
          >
            Reset Password
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
