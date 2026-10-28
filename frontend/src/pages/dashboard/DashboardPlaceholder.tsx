import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { getUnreadCountApi } from '@/lib/api/notifications';
import { CustomerDashboardView } from '@/components/dashboard/CustomerDashboardView';
import { toast } from 'sonner';

export function DashboardPlaceholder() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [unreadCount, setUnreadCount] = useState<number>(0);

  useEffect(() => {
    getUnreadCountApi()
      .then((data) => setUnreadCount(data.unreadCount))
      .catch(() => {});
  }, []);

  const handleLogout = () => {
    logout();
    toast.info('You have been logged out.');
    navigate('/login');
  };

  const dashboardUser = {
    name: user?.name || 'Customer',
    email: user?.email || '',
    role: user?.role || 'CUSTOMER',
    userId: user?.userId,
  };

  return (
    <CustomerDashboardView
      user={dashboardUser}
      unreadCount={unreadCount}
      isPreview={false}
      onLogout={handleLogout}
      showToast={true}
    />
  );
}
