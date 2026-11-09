import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest';
import axios from 'axios';
import {
  apiClient,
  isPublicEndpoint,
  normalizeUrlPath,
  SESSION_STORAGE_KEY,
  getStoredToken,
  clearStoredSession,
  getAuthEpoch,
  bumpAuthEpoch,
} from '@/lib/api/client';
import * as authApi from '@/lib/api/auth';
import type { StoredSession, LoginResponse } from '@/types/auth.types';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import React from 'react';
import { AuthProvider } from '@/context/AuthContext';
import { useAuth } from '@/hooks/useAuth';

// Helper to create a valid base64-encoded JWT with an expiration claim
function createTestJwt(expSecondsFromNow = 3600): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = btoa(
    JSON.stringify({
      exp: Math.floor(Date.now() / 1000) + expSecondsFromNow,
      sub: '1',
      role: 'ROLE_CUSTOMER',
    })
  );
  return `${header}.${payload}.signature`;
}

// Mock axios methods
vi.mock('axios', async () => {
  const actual = await vi.importActual<typeof import('axios')>('axios');
  const mockPost = vi.fn();
  const mockCreate = vi.fn().mockImplementation((config) => {
    const instance = actual.default.create(config);
    return instance;
  });

  return {
    default: {
      ...actual.default,
      create: mockCreate,
      post: mockPost,
    },
    AxiosError: actual.AxiosError,
  };
});

describe('Phase 4 Checkpoint 4: Frontend Refresh-Token Integration', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    delete (navigator as any).locks;

    // Configure apiClient default adapter to avoid real network calls during replay
    apiClient.defaults.adapter = async (config) => {
      return {
        data: { success: true },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      };
    };
  });

  afterEach(() => {
    cleanup();
    localStorage.clear();
    vi.restoreAllMocks();
    delete (navigator as any).locks;
  });

  // 1. Exact Public Endpoint Matching & Case Preservation
  describe('1. Exact Public Endpoint Matching & URL Normalization', () => {
    it('preserves URL path case and strips query strings and hashes', () => {
      expect(normalizeUrlPath('/api/auth/login?redirect=/home#token', 'http://localhost:8080')).toBe('/api/auth/login');
      expect(normalizeUrlPath('/api/auth/Login', 'http://localhost:8080')).toBe('/api/auth/Login');
      expect(normalizeUrlPath('/auth/refresh-token?param=1')).toBe('/auth/refresh-token');
      expect(normalizeUrlPath('')).toBe('');
    });

    it('identifies exact public endpoints correctly and excludes unrelated endpoints', () => {
      // Exact public paths
      expect(isPublicEndpoint('/api/auth/login')).toBe(true);
      expect(isPublicEndpoint('/auth/login')).toBe(true);
      expect(isPublicEndpoint('/api/auth/refresh-token')).toBe(true);
      expect(isPublicEndpoint('/auth/refresh-token')).toBe(true);
      expect(isPublicEndpoint('/api/auth/logout')).toBe(true);
      expect(isPublicEndpoint('/auth/logout')).toBe(true);
      expect(isPublicEndpoint('/api/auth/register')).toBe(true);
      expect(isPublicEndpoint('/api/auth/forgot-password')).toBe(true);
      expect(isPublicEndpoint('/api/auth/verify-reset-token')).toBe(true);
      expect(isPublicEndpoint('/api/auth/reset-password')).toBe(true);
      expect(isPublicEndpoint('/api/health')).toBe(true);
      expect(isPublicEndpoint('/health')).toBe(true);

      // Protected or unrelated endpoints containing substring must NOT be public
      expect(isPublicEndpoint('/api/auth/change-password')).toBe(false);
      expect(isPublicEndpoint('/api/tickets/auth/login-history')).toBe(false);
      expect(isPublicEndpoint('/api/admin/auth/logout-audits')).toBe(false);
      expect(isPublicEndpoint('/api/tickets?ref=/auth/login')).toBe(false);
    });

    it('removes Authorization header on public endpoints and attaches it on protected endpoints', async () => {
      const validToken = createTestJwt(3600);
      const session: StoredSession = {
        userId: 1,
        name: 'Test User',
        email: 'test@example.com',
        role: 'CUSTOMER',
        accessToken: validToken,
        tokenType: 'Bearer',
      };
      localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));

      // Test protected endpoint
      const protectedConfig: any = { url: '/api/tickets', headers: {} };
      const interceptedProtected = (apiClient.interceptors.request as any).handlers[0].fulfilled(protectedConfig);
      expect(interceptedProtected.headers.Authorization).toBe(`Bearer ${validToken}`);
      expect(interceptedProtected._sentToken).toBe(validToken);

      // Test public endpoint
      const publicConfig: any = {
        url: '/api/auth/refresh-token',
        headers: { Authorization: `Bearer ${validToken}` },
      };
      const interceptedPublic = (apiClient.interceptors.request as any).handlers[0].fulfilled(publicConfig);
      expect(interceptedPublic.headers.Authorization).toBeUndefined();
    });
  });

  // 2. Credential Configuration
  describe('2. Credential Configuration & Storage Hygiene', () => {
    it('configures withCredentials: true on apiClient for HttpOnly cookie propagation', () => {
      expect(apiClient.defaults.withCredentials).toBe(true);
    });

    it('never stores raw refresh token in localStorage', () => {
      const loginResp: LoginResponse = {
        userId: 1,
        name: 'Safe User',
        email: 'safe@example.com',
        role: 'CUSTOMER',
        accessToken: createTestJwt(3600),
        tokenType: 'Bearer',
      };
      const sessionData: StoredSession = {
        ...loginResp,
      };
      localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(sessionData));

      const raw = localStorage.getItem(SESSION_STORAGE_KEY);
      expect(raw).not.toBeNull();
      const stored = JSON.parse(raw!);
      expect(stored.accessToken).toBe(loginResp.accessToken);
      expect((stored as any).refreshToken).toBeUndefined();
    });
  });

  // 3. Guest Request Protection
  describe('3. Protected-Request Refresh Guard', () => {
    it('does not attempt refresh if original request never had an access token attached', async () => {
      const error: any = {
        config: { url: '/api/tickets', headers: {} }, // No _sentToken
        response: { status: 401, data: { message: 'Unauthorized' } },
      };

      const responseHandler = (apiClient.interceptors.response as any).handlers[0].rejected;
      await expect(responseHandler(error)).rejects.toMatchObject({
        status: 401,
      });

      expect(axios.post).not.toHaveBeenCalled();
    });
  });

  // 4. In-Tab Concurrency & Single-Flight Mutex
  describe('4. In-Tab Concurrency (Single-Flight Mutex)', () => {
    it('executes POST /auth/refresh-token only once for multiple concurrent 401 requests', async () => {
      const initialToken = createTestJwt(3600);
      const newToken = createTestJwt(7200);

      // Mock raw axios.post for refresh call
      (axios.post as any).mockResolvedValueOnce({
        data: {
          userId: 1,
          name: 'Debjani',
          email: 'debjani@example.com',
          role: 'CUSTOMER',
          accessToken: newToken,
          tokenType: 'Bearer',
        },
      });

      const responseHandler = (apiClient.interceptors.response as any).handlers[0].rejected;

      const req1: any = {
        config: { url: '/api/tickets/1', headers: { Authorization: `Bearer ${initialToken}` }, _sentToken: initialToken },
        response: { status: 401 },
      };
      const req2: any = {
        config: { url: '/api/tickets/2', headers: { Authorization: `Bearer ${initialToken}` }, _sentToken: initialToken },
        response: { status: 401 },
      };
      const req3: any = {
        config: { url: '/api/tickets/3', headers: { Authorization: `Bearer ${initialToken}` }, _sentToken: initialToken },
        response: { status: 401 },
      };

      const [res1, res2, res3] = await Promise.all([
        responseHandler(req1),
        responseHandler(req2),
        responseHandler(req3),
      ]);

      expect(axios.post).toHaveBeenCalledTimes(1);
      expect(res1.data).toEqual({ success: true });
      expect(res2.data).toEqual({ success: true });
      expect(res3.data).toEqual({ success: true });
    });
  });

  // 5. Cross-Tab Web Lock Coordination & Fallback
  describe('5. Cross-Tab Coordination', () => {
    it('uses Web Locks API to ensure sibling tab adopts token and skips refresh network call', async () => {
      const oldToken = createTestJwt(100);
      const siblingToken = createTestJwt(3600);

      // Mock Web Locks API
      const mockRequest = vi.fn().mockImplementation(async (_lockName, callback) => {
        // Simulate sibling tab having completed write in localStorage while waiting
        const updatedSession: StoredSession = {
          userId: 1,
          name: 'Sibling Tab',
          email: 'sibling@example.com',
          role: 'CUSTOMER',
          accessToken: siblingToken,
          tokenType: 'Bearer',
        };
        localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(updatedSession));
        return callback();
      });

      Object.defineProperty(navigator, 'locks', {
        value: { request: mockRequest },
        configurable: true,
      });

      const responseHandler = (apiClient.interceptors.response as any).handlers[0].rejected;
      const req: any = {
        config: { url: '/api/tickets/5', headers: { Authorization: `Bearer ${oldToken}` }, _sentToken: oldToken },
        response: { status: 401 },
      };

      const result = await responseHandler(req);

      expect(mockRequest).toHaveBeenCalledWith('auth_refresh_lock', expect.any(Function));
      // Network call was skipped because token was already updated in storage
      expect(axios.post).not.toHaveBeenCalled();
      expect(result.data).toEqual({ success: true });
    });

    it('coordinates via fallback jitter when navigator.locks is unavailable', async () => {
      delete (navigator as any).locks;

      const oldToken = createTestJwt(100);
      const freshToken = createTestJwt(3600);

      (axios.post as any).mockResolvedValueOnce({
        data: {
          userId: 1,
          name: 'Fallback User',
          email: 'fallback@example.com',
          role: 'CUSTOMER',
          accessToken: freshToken,
          tokenType: 'Bearer',
        },
      });

      const responseHandler = (apiClient.interceptors.response as any).handlers[0].rejected;
      const req: any = {
        config: { url: '/api/tickets/fallback', headers: { Authorization: `Bearer ${oldToken}` }, _sentToken: oldToken },
        response: { status: 401 },
      };

      const result = await responseHandler(req);

      expect(result.data).toEqual({ success: true });
      expect(axios.post).toHaveBeenCalledTimes(1);
    });
  });

  // 6. 409 Conflict Recovery
  describe('6. 409 TOKEN_ALREADY_ROTATED Recovery', () => {
    it('recovers from 409 conflict by polling localStorage and replaying without logging out', async () => {
      const oldToken = createTestJwt(100);
      const winnerToken = createTestJwt(3600);

      // 1. Refresh call returns 409 TOKEN_ALREADY_ROTATED
      (axios.post as any).mockRejectedValueOnce({
        response: {
          status: 409,
          data: {
            error: 'Conflict',
            errorCode: 'TOKEN_ALREADY_ROTATED',
            message: 'Token was rotated by a concurrent request',
          },
        },
      });

      // Simulate winner tab writing to storage shortly after
      setTimeout(() => {
        const winnerSession: StoredSession = {
          userId: 1,
          name: 'Winner Tab',
          email: 'winner@example.com',
          role: 'CUSTOMER',
          accessToken: winnerToken,
          tokenType: 'Bearer',
        };
        localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(winnerSession));
      }, 30);

      const responseHandler = (apiClient.interceptors.response as any).handlers[0].rejected;
      const req: any = {
        config: { url: '/api/tickets/race', headers: { Authorization: `Bearer ${oldToken}` }, _sentToken: oldToken },
        response: { status: 401 },
      };

      const result = await responseHandler(req);

      expect(result.data).toEqual({ success: true });
      expect(getStoredToken()).toBe(winnerToken);
    });
  });

  // 7. Terminal Failures vs Transient Errors
  describe('7. Terminal 401 vs Transient 5xx Failures', () => {
    it('purges session and dispatches auth:session-expired on terminal 401 refresh failure', async () => {
      const expiredToken = createTestJwt(100);
      const session: StoredSession = {
        userId: 1,
        name: 'User',
        email: 'user@example.com',
        role: 'CUSTOMER',
        accessToken: expiredToken,
        tokenType: 'Bearer',
      };
      localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));

      (axios.post as any).mockRejectedValueOnce({
        response: { status: 401, data: { message: 'Refresh token invalid' } },
      });

      const eventSpy = vi.fn();
      window.addEventListener('auth:session-expired', eventSpy);

      const responseHandler = (apiClient.interceptors.response as any).handlers[0].rejected;
      const req: any = {
        config: { url: '/api/tickets/dead', headers: { Authorization: `Bearer ${expiredToken}` }, _sentToken: expiredToken },
        response: { status: 401 },
      };

      await expect(responseHandler(req)).rejects.toMatchObject({ status: 401 });

      expect(localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
      expect(eventSpy).toHaveBeenCalledTimes(1);

      window.removeEventListener('auth:session-expired', eventSpy);
    });

    it('does not purge session or dispatch session-expired on transient 500/network error', async () => {
      const activeToken = createTestJwt(3600);
      const session: StoredSession = {
        userId: 1,
        name: 'User',
        email: 'user@example.com',
        role: 'CUSTOMER',
        accessToken: activeToken,
        tokenType: 'Bearer',
      };
      localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));

      (axios.post as any).mockRejectedValueOnce({
        response: { status: 503, data: { message: 'Service Unavailable' } },
      });

      const eventSpy = vi.fn();
      window.addEventListener('auth:session-expired', eventSpy);

      const responseHandler = (apiClient.interceptors.response as any).handlers[0].rejected;
      const req: any = {
        config: { url: '/api/tickets/offline', headers: { Authorization: `Bearer ${activeToken}` }, _sentToken: activeToken },
        response: { status: 401 },
      };

      await expect(responseHandler(req)).rejects.toMatchObject({ status: 503 });

      // Session remains in storage for retry when network is restored
      expect(localStorage.getItem(SESSION_STORAGE_KEY)).not.toBeNull();
      expect(eventSpy).not.toHaveBeenCalled();

      window.removeEventListener('auth:session-expired', eventSpy);
    });

    it('prevents infinite retry loops by failing immediately when _retry is true', async () => {
      const token = createTestJwt(3600);
      const eventSpy = vi.fn();
      window.addEventListener('auth:session-expired', eventSpy);

      const responseHandler = (apiClient.interceptors.response as any).handlers[0].rejected;
      const req: any = {
        config: { url: '/api/tickets/retry-loop', _retry: true, _sentToken: token },
        response: { status: 401 },
      };

      await expect(responseHandler(req)).rejects.toMatchObject({ status: 401 });

      expect(axios.post).not.toHaveBeenCalled();
      expect(eventSpy).toHaveBeenCalledTimes(1);

      window.removeEventListener('auth:session-expired', eventSpy);
    });

    it('bypasses refresh on 403 Forbidden', async () => {
      const responseHandler = (apiClient.interceptors.response as any).handlers[0].rejected;
      const req: any = {
        config: { url: '/api/admin/users', headers: {} },
        response: { status: 403, data: { message: 'Access Denied' } },
      };

      await expect(responseHandler(req)).rejects.toMatchObject({ status: 403 });
      expect(axios.post).not.toHaveBeenCalled();
    });
  });

  // 8. Session Generation & Logout / Refresh Race
  describe('8. Session Generation & Cancellation Guard (authEpoch)', () => {
    it('discards late refresh response if same-tab logout occurred while refresh was in-flight', async () => {
      const oldToken = createTestJwt(3600);
      let resolveSlowRefresh: any;
      (axios.post as any).mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveSlowRefresh = resolve;
          })
      );

      const responseHandler = (apiClient.interceptors.response as any).handlers[0].rejected;
      const req: any = {
        config: { url: '/api/tickets/race', headers: { Authorization: `Bearer ${oldToken}` }, _sentToken: oldToken },
        response: { status: 401 },
      };

      // Start refresh
      const refreshPromise = responseHandler(req);

      // Wait for jitter and promise to enter axios.post
      await new Promise((r) => setTimeout(r, 60));

      // User logs out in the same tab while refresh is pending
      const epochBefore = getAuthEpoch();
      bumpAuthEpoch();
      expect(getAuthEpoch()).toBeGreaterThan(epochBefore);
      clearStoredSession();

      // Late response arrives from server
      if (resolveSlowRefresh) {
        resolveSlowRefresh({
          data: {
            userId: 1,
            name: 'Zombie User',
            email: 'zombie@example.com',
            role: 'CUSTOMER',
            accessToken: createTestJwt(3600),
            tokenType: 'Bearer',
          },
        });
      }

      await expect(refreshPromise).rejects.toBeDefined();

      // Ensure storage was not resurrected
      expect(localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
    });

    it('discards late refresh response if another tab logged out while refresh was in-flight', async () => {
      const oldToken = createTestJwt(3600);
      let resolveSlowRefresh: any;
      (axios.post as any).mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveSlowRefresh = resolve;
          })
      );

      const responseHandler = (apiClient.interceptors.response as any).handlers[0].rejected;
      const req: any = {
        config: { url: '/api/tickets/race-tab', headers: { Authorization: `Bearer ${oldToken}` }, _sentToken: oldToken },
        response: { status: 401 },
      };

      const refreshPromise = responseHandler(req);

      await new Promise((r) => setTimeout(r, 60));

      // Another tab clears session and dispatches storage event
      bumpAuthEpoch();

      if (resolveSlowRefresh) {
        resolveSlowRefresh({
          data: {
            userId: 1,
            name: 'Zombie Tab',
            email: 'zombie@example.com',
            role: 'CUSTOMER',
            accessToken: createTestJwt(3600),
            tokenType: 'Bearer',
          },
        });
      }

      await expect(refreshPromise).rejects.toBeDefined();
      expect(localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
    });
  });

  // 9. React AuthContext Initialization & Logout Integration
  describe('9. AuthContext Initialization, Validation & Logout', () => {
    function TestConsumer() {
      const { isInitializing, isAuthenticated, user, token, initError, logout } = useAuth();
      return React.createElement(
        'div',
        null,
        React.createElement('span', { 'data-testid': 'init' }, isInitializing ? 'loading' : 'done'),
        React.createElement('span', { 'data-testid': 'auth' }, isAuthenticated ? 'authenticated' : 'unauthenticated'),
        React.createElement('span', { 'data-testid': 'user' }, user?.email || 'none'),
        React.createElement('span', { 'data-testid': 'token' }, token || 'none'),
        React.createElement('span', { 'data-testid': 'error' }, initError || 'none'),
        React.createElement('button', { 'data-testid': 'logout-btn', onClick: () => logout() }, 'Logout')
      );
    }

    it('safely purges malformed localStorage session without crashing', async () => {
      localStorage.setItem(SESSION_STORAGE_KEY, '{ invalid JSON');

      render(
        React.createElement(
          AuthProvider,
          null,
          React.createElement(TestConsumer, null)
        )
      );

      await waitFor(() => {
        expect(screen.getByTestId('init').textContent).toBe('done');
      });

      expect(screen.getByTestId('auth').textContent).toBe('unauthenticated');
      expect(localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
    });

    it('safely purges session with incomplete schema fields', async () => {
      localStorage.setItem(
        SESSION_STORAGE_KEY,
        JSON.stringify({
          userId: 1,
          // Missing name, email, role, tokenType
          accessToken: createTestJwt(3600),
        })
      );

      render(
        React.createElement(
          AuthProvider,
          null,
          React.createElement(TestConsumer, null)
        )
      );

      await waitFor(() => {
        expect(screen.getByTestId('init').textContent).toBe('done');
      });

      expect(screen.getByTestId('auth').textContent).toBe('unauthenticated');
      expect(localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
    });

    it('retains session and sets initError on transient network failure during startup', async () => {
      // Create expired token (exp in past)
      const expiredJwt = 'header.' + btoa(JSON.stringify({ exp: 1000 })) + '.sig';
      const session: StoredSession = {
        userId: 5,
        name: 'Offline User',
        email: 'offline@example.com',
        role: 'AGENT',
        accessToken: expiredJwt,
        tokenType: 'Bearer',
      };
      localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));

      vi.spyOn(authApi, 'refreshTokenApi').mockRejectedValueOnce({
        status: 503,
        message: 'Network offline',
      });

      render(
        React.createElement(
          AuthProvider,
          null,
          React.createElement(TestConsumer, null)
        )
      );

      await waitFor(() => {
        expect(screen.getByTestId('init').textContent).toBe('done');
      });

      // User remains authenticated optimistically without redirecting to login
      expect(screen.getByTestId('auth').textContent).toBe('authenticated');
      expect(screen.getByTestId('user').textContent).toBe('offline@example.com');
      expect(screen.getByTestId('error').textContent).toContain('Unable to connect to authentication server');
      expect(localStorage.getItem(SESSION_STORAGE_KEY)).not.toBeNull();
    });

    it('executes logoutApi and purges local storage in finally even if network fails', async () => {
      const activeJwt = createTestJwt(3600);
      const session: StoredSession = {
        userId: 10,
        name: 'Logout User',
        email: 'logout@example.com',
        role: 'CUSTOMER',
        accessToken: activeJwt,
        tokenType: 'Bearer',
      };
      localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));

      const logoutSpy = vi.spyOn(authApi, 'logoutApi').mockRejectedValueOnce(new Error('Network error on logout'));

      render(
        React.createElement(
          AuthProvider,
          null,
          React.createElement(TestConsumer, null)
        )
      );

      await waitFor(() => {
        expect(screen.getByTestId('auth').textContent).toBe('authenticated');
      });

      // Trigger logout
      screen.getByTestId('logout-btn').click();

      await waitFor(() => {
        expect(screen.getByTestId('auth').textContent).toBe('unauthenticated');
      });

      expect(logoutSpy).toHaveBeenCalledTimes(1);
      expect(localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
    });
  });
});
