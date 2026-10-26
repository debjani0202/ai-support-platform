import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link } from 'react-router-dom';
import { changePasswordSchema, type ChangePasswordFormValues } from '@/lib/validation/auth.schema';
import { changePasswordApi } from '@/lib/api/auth';
import { AppNavbar } from '@/components/layout/AppNavbar';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { KeyRound, AlertCircle, CheckCircle2, ArrowLeft } from 'lucide-react';
import { toast } from 'sonner';

export function ChangePasswordPage() {
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ChangePasswordFormValues>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: {
      currentPassword: '',
      newPassword: '',
      confirmNewPassword: '',
    },
  });

  const onSubmit = async (data: ChangePasswordFormValues) => {
    setGeneralError(null);
    setSuccessMessage(null);

    try {
      await changePasswordApi({
        currentPassword: data.currentPassword,
        newPassword: data.newPassword,
      });

      reset();
      setSuccessMessage('Password changed successfully.');
      toast.success('Password changed successfully.');
    } catch (err: unknown) {
      const apiErr = err as {
        status?: number;
        message?: string;
        errors?: Record<string, string>;
      };

      if (apiErr.errors) {
        Object.entries(apiErr.errors).forEach(([field, msg]) => {
          if (field === 'currentPassword' || field === 'newPassword') {
            setError(field as keyof ChangePasswordFormValues, { message: msg });
          }
        });
      }

      if (apiErr.message === 'Current password is incorrect') {
        setError('currentPassword', { message: 'Current password is incorrect.' });
      } else if (apiErr.message === 'New password must be different') {
        setError('newPassword', { message: 'New password must be different from current password.' });
      } else if (apiErr.message) {
        setGeneralError(apiErr.message);
      } else {
        setGeneralError('Unable to update password. Please check your credentials and try again.');
      }
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <AppNavbar />

      <main className="flex-1 max-w-lg w-full mx-auto px-4 sm:px-6 py-8">
        <div className="mb-4">
          <Link
            to="/dashboard"
            className="inline-flex items-center text-xs font-medium text-slate-500 hover:text-slate-800 transition-colors"
          >
            <ArrowLeft className="w-3.5 h-3.5 mr-1" />
            Back to Dashboard
          </Link>
        </div>

        <Card>
          <CardHeader className="space-y-1">
            <div className="flex items-center gap-2.5">
              <div className="p-2 rounded-lg bg-indigo-50 text-indigo-600 border border-indigo-100/60">
                <KeyRound className="w-5 h-5" />
              </div>
              <div>
                <CardTitle>Change Password</CardTitle>
                <CardDescription>
                  Update your account password. Must be between 8 and 72 characters.
                </CardDescription>
              </div>
            </div>
          </CardHeader>

          <CardContent className="pt-2">
            {generalError && (
              <div className="mb-5 rounded-lg bg-rose-50 border border-rose-200/80 p-3.5 flex items-start gap-2.5 text-rose-700 text-xs">
                <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                <span>{generalError}</span>
              </div>
            )}

            {successMessage && (
              <div className="mb-5 rounded-lg bg-emerald-50 border border-emerald-200/80 p-3.5 flex items-start gap-2.5 text-emerald-800 text-xs">
                <CheckCircle2 className="w-4 h-4 shrink-0 mt-0.5 text-emerald-600" />
                <div className="flex-1 flex items-center justify-between">
                  <span>{successMessage}</span>
                  <Link
                    to="/dashboard"
                    className="font-semibold text-emerald-900 hover:underline ml-2"
                  >
                    Go to Dashboard &rarr;
                  </Link>
                </div>
              </div>
            )}

            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
              <Input
                label="Current Password"
                type="password"
                autoComplete="current-password"
                placeholder="Enter current password"
                {...register('currentPassword')}
                error={errors.currentPassword?.message}
                disabled={isSubmitting}
              />

              <Input
                label="New Password"
                type="password"
                autoComplete="new-password"
                placeholder="Enter new password (8-72 characters)"
                {...register('newPassword')}
                error={errors.newPassword?.message}
                disabled={isSubmitting}
              />

              <Input
                label="Confirm New Password"
                type="password"
                autoComplete="new-password"
                placeholder="Re-enter new password"
                {...register('confirmNewPassword')}
                error={errors.confirmNewPassword?.message}
                disabled={isSubmitting}
              />

              <div className="pt-2 flex flex-col sm:flex-row gap-2">
                <Button
                  type="submit"
                  variant="primary"
                  fullWidth
                  isLoading={isSubmitting}
                  disabled={isSubmitting}
                >
                  Update Password
                </Button>

                <Link to="/dashboard" className="w-full sm:w-auto">
                  <Button
                    type="button"
                    variant="outline"
                    fullWidth
                    disabled={isSubmitting}
                  >
                    Cancel
                  </Button>
                </Link>
              </div>
            </form>
          </CardContent>
        </Card>
      </main>
    </div>
  );
}
