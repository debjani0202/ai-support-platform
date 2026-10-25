import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useNavigate } from 'react-router-dom';
import { registerSchema, type RegisterFormValues } from '@/lib/validation/auth.schema';
import { registerApi } from '@/lib/api/auth';
import { AuthLayout } from '@/components/layout/AuthLayout';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { AlertCircle } from 'lucide-react';
import { toast } from 'sonner';

export function RegisterPage() {
  const navigate = useNavigate();
  const [generalError, setGeneralError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      name: '',
      email: '',
      password: '',
      confirmPassword: '',
    },
  });

  const onSubmit = async (data: RegisterFormValues) => {
    setGeneralError(null);
    try {
      await registerApi({
        name: data.name,
        email: data.email,
        password: data.password,
      });

      toast.success('Account created successfully! Please sign in.');
      navigate('/login');
    } catch (err: unknown) {
      const apiErr = err as {
        status?: number;
        message?: string;
        errors?: Record<string, string>;
      };

      // Map backend validation field errors if provided
      if (apiErr.errors) {
        Object.entries(apiErr.errors).forEach(([field, msg]) => {
          if (field === 'name' || field === 'email' || field === 'password') {
            setError(field, { message: msg });
          }
        });
      }

      // Display backend business rule message (e.g., "Email is already registered")
      if (apiErr.message) {
        setGeneralError(apiErr.message);
      } else {
        setGeneralError('Registration failed. Please check your information and try again.');
      }
    }
  };

  return (
    <AuthLayout
      title="Create customer account"
      subtitle="Join the AI Support Ticket Management Platform"
    >
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        {generalError && (
          <div className="rounded-lg bg-rose-50 border border-rose-200/80 p-3.5 flex items-start gap-2.5 text-rose-700 text-xs">
            <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
            <span>{generalError}</span>
          </div>
        )}

        <Input
          label="Full Name"
          type="text"
          autoComplete="name"
          placeholder="Jane Doe"
          {...register('name')}
          error={errors.name?.message}
        />

        <Input
          label="Email address"
          type="email"
          autoComplete="email"
          placeholder="name@example.com"
          {...register('email')}
          error={errors.email?.message}
        />

        <Input
          label="Password"
          type="password"
          autoComplete="new-password"
          placeholder="At least 8 characters"
          {...register('password')}
          error={errors.password?.message}
        />

        <Input
          label="Confirm Password"
          type="password"
          autoComplete="new-password"
          placeholder="Re-enter your password"
          {...register('confirmPassword')}
          error={errors.confirmPassword?.message}
        />

        <p className="text-[11px] text-slate-500 italic">
          * Public registration creates standard customer accounts.
        </p>

        <Button type="submit" fullWidth isLoading={isSubmitting}>
          Create Account
        </Button>

        <div className="pt-2 text-center text-xs text-slate-500">
          Already have an account?{' '}
          <Link
            to="/login"
            className="font-medium text-indigo-600 hover:text-indigo-500 hover:underline"
          >
            Sign in
          </Link>
        </div>
      </form>
    </AuthLayout>
  );
}
