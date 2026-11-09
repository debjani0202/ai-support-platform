import React, { useState, useEffect, useCallback } from 'react';
import { AuthContext } from './auth-context';
import type { AuthUser, LoginResponse, StoredSession } from '@/types/auth.types';
import { SESSION_STORAGE_KEY, clearStoredSession, bumpAuthEpoch } from '@/lib/api/client';
import { isTokenExpired } from '@/lib/utils/jwt';
import { refreshTokenApi, logoutApi } from '@/lib/api/auth';

export interface AuthContextType {
  isInitializing: boolean;
  initError?: string | null;
  retryInit?: () => Promise<void>;
  user: AuthUser | null;
  token: string | null;
  isAuthenticated: boolean;
  login: (response: LoginResponse) => void;
  logout: () => Promise<void>;
  refreshSession: () => Promise<void>;
}

/**
 * Validates all required fields of a StoredSession to safely detect malformed data.
 */
function isValidStoredSession(data: any): data is StoredSession {
  return (
    data !== null &&
    typeof data === 'object' &&
    typeof data.userId === 'number' &&
    typeof data.name === 'string' &&
    typeof data.email === 'string' &&
    typeof data.role === 'string' &&
    ['CUSTOMER', 'AGENT', 'ADMIN'].includes(data.role) &&
    typeof data.accessToken === 'string' &&
    typeof data.tokenType === 'string'
  );
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [isInitializing, setIsInitializing] = useState<boolean>(true);
  const [initError, setInitError] = useState<string | null>(null);
  const [user, setUser] = useState<AuthUser | null>(null);
  const [token, setToken] = useState<string | null>(null);

  // Login records session in memory and localStorage
  const login = useCallback((response: LoginResponse) => {
    const session: StoredSession = {
      userId: response.userId,
      name: response.name,
      email: response.email,
      role: response.role,
      accessToken: response.accessToken,
      tokenType: response.tokenType,
    };
    try {
      localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
    } catch {
      // Storage unavailable
    }
    setUser({
      userId: response.userId,
      name: response.name,
      email: response.email,
      role: response.role,
    });
    setToken(response.accessToken);
    setInitError(null);
  }, []);

  // Logout invalidates the backend session family while cookies are active,
  // then clears local storage and memory in finally.
  const logout = useCallback(async () => {
    bumpAuthEpoch();
    try {
      await logoutApi();
    } catch (err) {
      console.warn('Backend logout failed or unreachable; continuing local session cleanup', err);
    } finally {
      clearStoredSession();
      setUser(null);
      setToken(null);
      setInitError(null);
    }
  }, []);

  // Refreshes token with backend to update role/claims immediately
  const refreshSession = useCallback(async () => {
    const response = await refreshTokenApi();
    login(response);
  }, [login]);

  // Initial session restoration with schema validation and silent refresh
  const initializeSession = useCallback(async () => {
    setIsInitializing(true);
    setInitError(null);

    const raw = localStorage.getItem(SESSION_STORAGE_KEY);
    if (!raw) {
      setIsInitializing(false);
      return;
    }

    let session: StoredSession;
    try {
      const parsed = JSON.parse(raw);
      if (!isValidStoredSession(parsed)) {
        throw new Error('Malformed session schema');
      }
      session = parsed;
    } catch (parseErr) {
      console.warn('Malformed session found in localStorage; safely purging', parseErr);
      clearStoredSession();
      setUser(null);
      setToken(null);
      setIsInitializing(false);
      return;
    }

    // Active unexpired access token restored immediately
    if (!isTokenExpired(session.accessToken)) {
      setUser({
        userId: session.userId,
        name: session.name,
        email: session.email,
        role: session.role,
      });
      setToken(session.accessToken);
      setIsInitializing(false);
      return;
    }

    // Stored access token expired: attempt silent refresh using HttpOnly cookie
    try {
      const response = await refreshTokenApi();
      login(response);
    } catch (err: any) {
      const status = err?.status ?? err?.response?.status;
      if (status === 401) {
        // Terminal auth failure: refresh token expired, revoked, or absent
        clearStoredSession();
        setUser(null);
        setToken(null);
      } else {
        // Temporary network or 5xx server error:
        // Retain stored identity so user is not logged out during transient outages
        console.warn('Temporary network/server error during initial token refresh; retaining stored session', err);
        setUser({
          userId: session.userId,
          name: session.name,
          email: session.email,
          role: session.role,
        });
        setToken(session.accessToken);
        setInitError('Unable to connect to authentication server. Please check your network connection.');
      }
    } finally {
      setIsInitializing(false);
    }
  }, [login]);

  // Retries initialization after a transient connection failure
  const retryInit = useCallback(async () => {
    await initializeSession();
  }, [initializeSession]);

  useEffect(() => {
    initializeSession();

    // Listen to 401 session-expired event dispatched by client.ts
    const handleSessionExpired = () => {
      bumpAuthEpoch();
      clearStoredSession();
      setUser(null);
      setToken(null);
    };

    // Multi-tab synchronization via storage events
    const handleStorageChange = (event: StorageEvent) => {
      if (event.key !== SESSION_STORAGE_KEY) return;

      if (!event.newValue) {
        // Session purged in another tab (logout)
        bumpAuthEpoch();
        setUser(null);
        setToken(null);
      } else {
        // Session created or updated in another tab
        try {
          const parsed = JSON.parse(event.newValue);
          if (isValidStoredSession(parsed) && !isTokenExpired(parsed.accessToken)) {
            setUser({
              userId: parsed.userId,
              name: parsed.name,
              email: parsed.email,
              role: parsed.role,
            });
            setToken(parsed.accessToken);
          }
        } catch {
          // Ignore malformed storage updates
        }
      }
    };

    window.addEventListener('auth:session-expired', handleSessionExpired);
    window.addEventListener('storage', handleStorageChange);

    return () => {
      window.removeEventListener('auth:session-expired', handleSessionExpired);
      window.removeEventListener('storage', handleStorageChange);
    };
  }, [initializeSession]);

  return (
    <AuthContext.Provider
      value={{
        isInitializing,
        initError,
        retryInit,
        user,
        token,
        isAuthenticated: !!token && !!user,
        login,
        logout,
        refreshSession,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
