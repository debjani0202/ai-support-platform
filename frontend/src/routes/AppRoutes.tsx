
import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { ProtectedRoute } from '@/routes/ProtectedRoute';

// Lazy-loaded pages
const LandingPage = lazy(() =>
  import('@/pages/public/LandingPage').then((module) => ({
    default: module.LandingPage,
  })),
);

const LoginPage = lazy(() =>
  import('@/pages/auth/LoginPage').then((module) => ({
    default: module.LoginPage,
  })),
);

const RegisterPage = lazy(() =>
  import('@/pages/auth/RegisterPage').then((module) => ({
    default: module.RegisterPage,
  })),
);

const ForgotPasswordPage = lazy(() =>
  import('@/pages/auth/ForgotPasswordPage').then((module) => ({
    default: module.ForgotPasswordPage,
  })),
);

const ResetPasswordPage = lazy(() =>
  import('@/pages/auth/ResetPasswordPage').then((module) => ({
    default: module.ResetPasswordPage,
  })),
);

const DashboardPlaceholder = lazy(() =>
  import('@/pages/dashboard/DashboardPlaceholder').then((module) => ({
    default: module.DashboardPlaceholder,
  })),
);

const TicketListPage = lazy(() =>
  import('@/pages/tickets/TicketListPage').then((module) => ({
    default: module.TicketListPage,
  })),
);

const CreateTicketPage = lazy(() =>
  import('@/pages/tickets/CreateTicketPage').then((module) => ({
    default: module.CreateTicketPage,
  })),
);

const TicketDetailPage = lazy(() =>
  import('@/pages/tickets/TicketDetailPage').then((module) => ({
    default: module.TicketDetailPage,
  })),
);

const NotificationsPage = lazy(() =>
  import('@/pages/notifications/NotificationsPage').then((module) => ({
    default: module.NotificationsPage,
  })),
);

const ChangePasswordPage = lazy(() =>
  import('@/pages/auth/ChangePasswordPage').then((module) => ({
    default: module.ChangePasswordPage,
  })),
);

const ForbiddenPage = lazy(() =>
  import('@/pages/error/ForbiddenPage').then((module) => ({
    default: module.ForbiddenPage,
  })),
);

const NotFoundPage = lazy(() =>
  import('@/pages/error/NotFoundPage').then((module) => ({
    default: module.NotFoundPage,
  })),
);

function PublicRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isInitializing } = useAuth();

  if (isInitializing) {
    return null;
  }

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }

  return <>{children}</>;
}

export function AppRoutes() {
  return (
    <Suspense
      fallback={
        <div className="flex min-h-screen items-center justify-center">
          <div
            className="h-8 w-8 animate-spin rounded-full border-4 border-indigo-600 border-t-transparent"
            role="status"
            aria-label="Loading page"
          />
        </div>
      }
    >
      <Routes>
        {/* Public Landing Page */}
        <Route path="/" element={<LandingPage />} />

        {/* Public Guest Routes */}
        <Route
          path="/login"
          element={
            <PublicRoute>
              <LoginPage />
            </PublicRoute>
          }
        />
        <Route
          path="/register"
          element={
            <PublicRoute>
              <RegisterPage />
            </PublicRoute>
          }
        />
        <Route
          path="/forgot-password"
          element={
            <PublicRoute>
              <ForgotPasswordPage />
            </PublicRoute>
          }
        />
        <Route
          path="/reset-password"
          element={
            <PublicRoute>
              <ResetPasswordPage />
            </PublicRoute>
          }
        />

        {/* Protected Routes */}
        <Route element={<ProtectedRoute />}>
          <Route path="/dashboard" element={<DashboardPlaceholder />} />
          <Route path="/tickets" element={<TicketListPage />} />
          <Route path="/tickets/new" element={<CreateTicketPage />} />
          <Route path="/tickets/:id" element={<TicketDetailPage />} />
          <Route path="/notifications" element={<NotificationsPage />} />
          <Route path="/change-password" element={<ChangePasswordPage />} />
        </Route>

        {/* Error Routes */}
        <Route path="/403" element={<ForbiddenPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  );
}
