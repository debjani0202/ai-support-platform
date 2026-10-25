import React, { useState, useEffect, useCallback } from 'react';
import { AuthContext } from './auth-context';
import type { AuthUser, LoginResponse, StoredSession } from '@/types/auth.types';
import { SESSION_STORAGE_KEY, clearStoredSession } from '@/lib/api/client';
import { isTokenExpired } from '@/lib/utils/jwt';

export interface AuthContextType {
  isInitializing: boolean;
  user: AuthUser | null;
  token: string | null;
  isAuthenticated: boolean;
  login: (response: LoginResponse) => void;
  logout: () => void;
}


export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [isInitializing, setIsInitializing] = useState<boolean>(true);
  const [user, setUser] = useState<AuthUser | null>(null);
  const [token, setToken] = useState<string | null>(null);

  // Logout clears memory and storage
  const logout = useCallback(() => {
    clearStoredSession();
    setUser(null);
    setToken(null);
  }, []);

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
  }, []);

  // Initial session restoration with defensive expiry checks
  useEffect(() => {
    try {
      const raw = localStorage.getItem(SESSION_STORAGE_KEY);
      if (raw) {
        const session: StoredSession = JSON.parse(raw);
        if (session.accessToken && !isTokenExpired(session.accessToken)) {
          setUser({
            userId: session.userId,
            name: session.name,
            email: session.email,
            role: session.role,
          });
          setToken(session.accessToken);
        } else {
          // Token expired or malformed
          clearStoredSession();
        }
      }
    } catch {
      clearStoredSession();
    } finally {
      setIsInitializing(false);
    }

    // Listen to 401 session-expired event dispatched by client.ts
    const handleSessionExpired = () => {
      logout();
    };

    window.addEventListener('auth:session-expired', handleSessionExpired);
    return () => {
      window.removeEventListener('auth:session-expired', handleSessionExpired);
    };
  }, [logout]);

  return (
    <AuthContext.Provider
      value={{
        isInitializing,
        user,
        token,
        isAuthenticated: !!token && !!user,
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}


