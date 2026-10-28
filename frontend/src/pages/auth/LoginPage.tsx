import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { loginSchema, type LoginFormValues } from '@/lib/validation/auth.schema';
import { loginApi } from '@/lib/api/auth';
import { useAuth } from '@/hooks/useAuth';
import { AuthLayout } from '@/components/layout/AuthLayout';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { AlertCircle } from 'lucide-react';
import { toast } from 'sonner';

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();
  const [generalError, setGeneralError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: '',
      password: '',
    },
  });

  const onSubmit = async (data: LoginFormValues) => {
    setGeneralError(null);
    try {
      const response = await loginApi(data);
      login(response);
      toast.success(`Welcome back, ${response.name}!`);

      // Redirect to attempted destination or fallback to /dashboard
      const from = (location.state as { from?: Location })?.from?.pathname || '/dashboard';
      navigate(from, { replace: true });
    } catch (err: unknown) {
      const apiErr = err as {
        status?: number;
        message?: string;
        errors?: Record<string, string>;
      };

      if (apiErr.errors) {
        Object.entries(apiErr.errors).forEach(([field, msg]) => {
          if (field === 'email' || field === 'password') {
            setError(field, { message: msg });
          }
        });
      }

      if (apiErr.status === 401) {
        setGeneralError('Invalid email or password.');
      } else if (apiErr.message) {
        setGeneralError(apiErr.message);
      } else {
        setGeneralError('Unable to connect to the server. Please try again.');
      }
    }
  };

  return (
    <AuthLayout
      title="Sign in to your account"
      subtitle="AI Support Ticket Management Platform"
    >
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        {generalError && (
          <div className="rounded-xl bg-rose-50 border border-rose-200/80 p-3 flex items-start gap-2.5 text-rose-700 text-xs">
            <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
            <span>{generalError}</span>
          </div>
        )}

        <Input
          label="EMAIL ADDRESS"
          type="email"
          autoComplete="email"
          placeholder="name@example.com"
          {...register('email')}
          error={errors.email?.message}
          disabled={isSubmitting}
        />

        <Input
          label="PASSWORD"
          type="password"
          autoComplete="current-password"
          placeholder="••••••••"
          {...register('password')}
          error={errors.password?.message}
          disabled={isSubmitting}
        />

        <div className="flex items-center justify-end -mt-1">
          <Link
            to="/forgot-password"
            className="text-xs font-semibold text-blue-600 hover:text-blue-500 hover:underline"
          >
            Forgot password?
          </Link>
        </div>

        <div className="pt-2">
          <Button
            type="submit"
            fullWidth
            isLoading={isSubmitting}
            className="h-11 rounded-xl bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 text-white font-semibold text-sm shadow-md shadow-blue-500/25 transition-all"
          >
            Sign In
          </Button>
        </div>

        <div className="pt-3 text-center">
          <Link
            to="/register"
            className="text-xs font-medium text-slate-500 hover:text-slate-800 transition-colors"
          >
            Create customer account
          </Link>
        </div>
      </form>
    </AuthLayout>
  );
}
