import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import type { ApiErrorResponse } from '@/types/api.types';
import type { LoginResponse, StoredSession } from '@/types/auth.types';
import { isTokenExpired } from '@/lib/utils/jwt';

export const SESSION_STORAGE_KEY = 'ai_support_session';

/**
 * Exact normalized path endpoints that must bypass Authorization header injection.
 * Refresh and logout operate via the HttpOnly cookie and must not transmit an expired Bearer token.
 */
export const EXACT_PUBLIC_PATHS = new Set([
  // Relative to API base
  '/auth/login',
  '/auth/refresh-token',
  '/auth/logout',
  '/auth/register',
  '/auth/forgot-password',
  '/auth/verify-reset-token',
  '/auth/reset-password',
  // Absolute with /api prefix
  '/api/auth/login',
  '/api/auth/refresh-token',
  '/api/auth/logout',
  '/api/auth/register',
  '/api/auth/forgot-password',
  '/api/auth/verify-reset-token',
  '/api/auth/reset-password',
  // Health probes
  '/health',
  '/api/health',
  '/actuator/health',
]);

/**
 * Normalizes an Axios request URL to an exact pathname without query params or hash.
 * Preserves the exact case of the URL pathname.
 */
export function normalizeUrlPath(url?: string, baseURL?: string): string {
  if (!url) return '';
  try {
    const base = baseURL
      ? (baseURL.startsWith('http') ? baseURL : 'http://localhost' + (baseURL.startsWith('/') ? '' : '/') + baseURL)
      : 'http://localhost';
    const parsed = new URL(url, base);
    return parsed.pathname.replace(/\/+$/, '');
  } catch {
    const withoutQuery = url.split('?')[0].split('#')[0];
    return (withoutQuery.startsWith('/') ? withoutQuery : '/' + withoutQuery).replace(/\/+$/, '');
  }
}

/**
 * Checks whether an endpoint is public and should not have Authorization header attached.
 */
export function isPublicEndpoint(url?: string, baseURL?: string): boolean {
  if (!url) return false;
  const path = normalizeUrlPath(url, baseURL);
  return EXACT_PUBLIC_PATHS.has(path);
}

export interface CustomRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
  _sentToken?: string;
}

/**
 * Retrieves the raw JWT access token from the active session storage.
 * Returns null if no session exists or if the token is absent.
 */
export function getStoredToken(): string | null {
  try {
    const raw = localStorage.getItem(SESSION_STORAGE_KEY);
    if (!raw) return null;
    const session: StoredSession = JSON.parse(raw);
    return session.accessToken || null;
  } catch {
    return null;
  }
}

/**
 * Removes the stored session from localStorage.
 */
export function clearStoredSession(): void {
  try {
    localStorage.removeItem(SESSION_STORAGE_KEY);
  } catch {
    // Storage access exception ignored
  }
}

/**
 * Session generation epoch counter.
 * Incremented on explicit logout or session expiry to cancel pending in-flight refresh responses.
 */
let currentAuthEpoch = 0;

export function getAuthEpoch(): number {
  return currentAuthEpoch;
}

export function bumpAuthEpoch(): number {
  currentAuthEpoch += 1;
  return currentAuthEpoch;
}

/**
 * Centralized Axios client configured with base URL, timeout,
 * withCredentials for HttpOnly cookie propagation, and bearer token injection.
 */
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
});

/**
 * Removes default JSON Content-Type headers when sending FormData so Axios and the browser
 * emit a proper multipart/form-data Content-Type header with boundary.
 */
function stripJsonContentTypeForFormData(config: CustomRequestConfig): void {
  if (typeof FormData === 'undefined' || !(config.data instanceof FormData) || !config.headers) {
    return;
  }

  const headers = config.headers as unknown as {
    delete?: (name: string) => void;
    [key: string]: unknown;
  };

  if (typeof headers.delete === 'function') {
    headers.delete('Content-Type');
    headers.delete('content-type');
  }
  delete headers['Content-Type'];
  delete headers['content-type'];
}

// Request Interceptor: Attach Bearer token on protected endpoints, exclude public endpoints
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const customConfig = config as CustomRequestConfig;

    stripJsonContentTypeForFormData(customConfig);

    // Exclude all public endpoints from Authorization header
    if (isPublicEndpoint(customConfig.url, customConfig.baseURL)) {
      if (customConfig.headers) {
        delete customConfig.headers.Authorization;
      }
      return customConfig;
    }

    // Attach Bearer token to protected endpoints and stamp _sentToken
    const token = getStoredToken();
    if (token && customConfig.headers) {
      customConfig.headers.Authorization = `Bearer ${token}`;
      customConfig._sentToken = token;
    }
    return customConfig;
  },
  (error) => Promise.reject(error)
);

interface QueuedRequest {
  resolve: (token: string) => void;
  reject: (error: unknown) => void;
}

let isRefreshing = false;
let failedQueue: QueuedRequest[] = [];

function processQueue(error: unknown, token: string | null = null): void {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else if (token) {
      prom.resolve(token);
    }
  });
  failedQueue = [];
}

interface ParsedErrorPayload {
  error?: string;
  errorCode?: string;
  message?: string;
  errors?: Record<string, string>;
}

const MAX_JSON_BLOB_ERROR_BYTES = 1_048_576;

function extractTrustedErrorPayload(rawPayload: unknown): ParsedErrorPayload | undefined {
  if (!rawPayload || typeof rawPayload !== 'object' || Array.isArray(rawPayload)) {
    return undefined;
  }

  const record = rawPayload as Record<string, unknown>;
  const errors =
    record.errors && typeof record.errors === 'object' && !Array.isArray(record.errors)
      ? Object.entries(record.errors as Record<string, unknown>).reduce<Record<string, string>>(
          (acc, [key, value]) => {
            if (typeof value === 'string') {
              acc[key] = value;
            }
            return acc;
          },
          {}
        )
      : undefined;

  return {
    error: typeof record.error === 'string' ? record.error : undefined,
    errorCode: typeof record.errorCode === 'string' ? record.errorCode : undefined,
    message: typeof record.message === 'string' ? record.message : undefined,
    errors: errors && Object.keys(errors).length > 0 ? errors : undefined,
  };
}

async function decodeErrorPayloadAsync(errorData: unknown): Promise<ParsedErrorPayload | undefined> {
  if (typeof Blob !== 'undefined' && errorData instanceof Blob) {
    const mime = (errorData.type || '').toLowerCase();
    const looksPotentiallyJson = mime === '' || mime.includes('json') || mime.startsWith('text/plain');
    if (!looksPotentiallyJson || errorData.size === 0 || errorData.size > MAX_JSON_BLOB_ERROR_BYTES) {
      return undefined;
    }

    try {
      const text =
        typeof errorData.text === 'function'
          ? await errorData.text()
          : await new Response(errorData).text();
      if (!text || !text.trim()) {
        return undefined;
      }
      return extractTrustedErrorPayload(JSON.parse(text));
    } catch {
      return undefined;
    }
  }

  return extractTrustedErrorPayload(errorData);
}

interface ErrorLike {
  status?: number;
  error?: string;
  errorCode?: string;
  message?: string;
  errors?: Record<string, string>;
  raw?: unknown;
  response?: {
    status?: number;
    data?: unknown;
  };
}

function asErrorLike(error: unknown): ErrorLike {
  return error && typeof error === 'object' ? (error as ErrorLike) : {};
}

function formatNormalizedError(error: unknown, errorData?: ParsedErrorPayload) {
  const errObj = asErrorLike(error);
  const message =
    errorData?.message ||
    (errorData?.errors ? Object.values(errorData.errors).join(', ') : null) ||
    errObj.message ||
    'An unexpected network error occurred';

  return {
    status: errObj.response?.status ?? errObj.status ?? 500,
    error: errorData?.error ?? errObj.error ?? 'Error',
    errorCode: errorData?.errorCode ?? errObj.errorCode,
    message,
    errors: errorData?.errors ?? errObj.errors,
    raw: errObj.raw ?? error,
  };
}

async function normalizeErrorAsync(error: unknown) {
  const rawData = asErrorLike(error).response?.data;
  if (typeof Blob !== 'undefined' && rawData instanceof Blob) {
    const parsedBlobData = await decodeErrorPayloadAsync(rawData);
    return formatNormalizedError(error, parsedBlobData);
  }
  return formatNormalizedError(error, extractTrustedErrorPayload(rawData));
}

/**
 * Polls localStorage for a newly written token (used during 409 recovery or fallback waiting).
 */
async function pollStorageForNewToken(
  originalToken: string,
  maxWaitMs: number,
  capturedEpoch: number
): Promise<string | null> {
  const start = Date.now();
  while (Date.now() - start < maxWaitMs) {
    if (capturedEpoch !== currentAuthEpoch) {
      return null;
    }
    const token = getStoredToken();
    if (token && token !== originalToken && !isTokenExpired(token)) {
      return token;
    }
    await new Promise((resolve) => setTimeout(resolve, 50));
  }
  return null;
}

/**
 * Performs the refresh network call using raw axios to avoid interceptor recursion.
 */
async function performRefreshOperation(
  capturedEpoch: number,
  originalSentToken: string
): Promise<string> {
  if (capturedEpoch !== currentAuthEpoch) {
    throw new Error('Refresh aborted: session ended');
  }

  // Check if another tab refreshed the token while we waited for the lock
  const latestStoredToken = getStoredToken();
  if (latestStoredToken && latestStoredToken !== originalSentToken && !isTokenExpired(latestStoredToken)) {
    return latestStoredToken;
  }

  try {
    const refreshBaseURL = apiClient.defaults.baseURL || '/api';
    const refreshUrl = `${refreshBaseURL.replace(/\/+$/, '')}/auth/refresh-token`;

    const response = await axios.post<LoginResponse>(
      refreshUrl,
      {},
      {
        withCredentials: true,
        headers: { 'Content-Type': 'application/json' },
      }
    );

    // Validate session generation before committing to storage
    if (capturedEpoch !== currentAuthEpoch) {
      throw new Error('Refresh aborted: session ended');
    }

    const data = response.data;
    const newSession: StoredSession = {
      userId: data.userId,
      name: data.name,
      email: data.email,
      role: data.role,
      accessToken: data.accessToken,
      tokenType: data.tokenType,
    };

    try {
      localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(newSession));
    } catch {
      // Storage unavailable
    }

    return data.accessToken;
  } catch (err: any) {
    const status = err?.response?.status;
    const errorCode = err?.response?.data?.errorCode;

    // Handle 409 TOKEN_ALREADY_ROTATED from backend grace period
    if (status === 409 && errorCode === 'TOKEN_ALREADY_ROTATED') {
      const recoveredToken = await pollStorageForNewToken(originalSentToken, 1000, capturedEpoch);
      if (recoveredToken) {
        return recoveredToken;
      }
    }

    throw err;
  }
}

/**
 * Fallback coordinator when Web Locks API is unavailable.
 * Note: localStorage does not have atomic CAS; races may still occur,
 * but backend 409 grace handling acts as defense-in-depth arbiter.
 */
async function executeFallbackRefresh(
  capturedEpoch: number,
  originalSentToken: string
): Promise<string> {
  const tabId = Math.random().toString(36).substring(2);
  const nonce = Math.random().toString(36).substring(2);
  const claimKey = 'auth_refresh_claim';

  try {
    localStorage.setItem(claimKey, JSON.stringify({ tabId, nonce, timestamp: Date.now() }));
  } catch {
    // Storage access exception ignored
  }

  // Small randomized jitter (20-50ms) to detect simultaneous writes
  const jitter = 20 + Math.floor(Math.random() * 30);
  await new Promise((resolve) => setTimeout(resolve, jitter));

  let isLeader = true;
  try {
    const claimRaw = localStorage.getItem(claimKey);
    if (claimRaw) {
      const parsed = JSON.parse(claimRaw);
      if (parsed.nonce !== nonce) {
        isLeader = false;
      }
    }
  } catch {
    isLeader = false;
  }

  if (!isLeader) {
    const recoveredToken = await pollStorageForNewToken(originalSentToken, 1500, capturedEpoch);
    if (recoveredToken) {
      return recoveredToken;
    }
  }

  return await performRefreshOperation(capturedEpoch, originalSentToken);
}

/**
 * Coordinates multi-tab token refresh via Web Locks API with fallback.
 */
async function executeCrossTabRefresh(
  capturedEpoch: number,
  originalSentToken: string
): Promise<string> {
  if (typeof navigator !== 'undefined' && 'locks' in navigator && navigator.locks) {
    return await navigator.locks.request('auth_refresh_lock', async () => {
      return await performRefreshOperation(capturedEpoch, originalSentToken);
    });
  } else {
    return await executeFallbackRefresh(capturedEpoch, originalSentToken);
  }
}

// Response Interceptor: Token refresh, mutex, 401 replay, error normalization
apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiErrorResponse>) => {
    const originalRequest = error.config as CustomRequestConfig;
    const status = error.response?.status;

    // A. Ignore non-401 errors, or requests without config
    if (!originalRequest || status !== 401) {
      return Promise.reject(await normalizeErrorAsync(error));
    }

    // B. Check exact public endpoints
    if (isPublicEndpoint(originalRequest.url, originalRequest.baseURL)) {
      if (originalRequest.url?.includes('/auth/refresh-token')) {
        bumpAuthEpoch();
        clearStoredSession();
        if (typeof window !== 'undefined') {
          window.dispatchEvent(new CustomEvent('auth:session-expired'));
        }
      }
      return Promise.reject(await normalizeErrorAsync(error));
    }

    // C. Protected-request refresh guard: only refresh if request sent an access token
    if (!originalRequest._sentToken) {
      return Promise.reject(await normalizeErrorAsync(error));
    }

    // D. Single-Retry Guard: prevent infinite retry loops
    if (originalRequest._retry) {
      bumpAuthEpoch();
      clearStoredSession();
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('auth:session-expired'));
      }
      return Promise.reject(await normalizeErrorAsync(error));
    }
    originalRequest._retry = true;

    // E. In-tab Single-Flight Mutex
    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        failedQueue.push({
          resolve: (newToken: string) => {
            if (originalRequest.headers) {
              originalRequest.headers.Authorization = `Bearer ${newToken}`;
            }
            originalRequest._sentToken = newToken;
            resolve(apiClient(originalRequest));
          },
          reject: (err: unknown) => {
            void normalizeErrorAsync(err).then(reject);
          },
        });
      });
    }

    isRefreshing = true;
    const capturedEpoch = currentAuthEpoch;
    const originalSentToken = originalRequest._sentToken;

    try {
      const newAccessToken = await executeCrossTabRefresh(capturedEpoch, originalSentToken);

      // Validate epoch before draining queue and replaying
      if (capturedEpoch !== currentAuthEpoch) {
        processQueue(new Error('Refresh aborted: session ended'), null);
        return Promise.reject(await normalizeErrorAsync(error));
      }

      processQueue(null, newAccessToken);

      if (originalRequest.headers) {
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
      }
      originalRequest._sentToken = newAccessToken;

      return apiClient(originalRequest);
    } catch (refreshErr: any) {
      const refreshStatus = refreshErr?.response?.status ?? refreshErr?.status;

      // Distinguish terminal auth failure (401) from transient network/5xx
      if (refreshStatus === 401) {
        bumpAuthEpoch();
        clearStoredSession();
        if (typeof window !== 'undefined') {
          window.dispatchEvent(new CustomEvent('auth:session-expired'));
        }
      }

      processQueue(refreshErr, null);
      return Promise.reject(await normalizeErrorAsync(refreshErr || error));
    } finally {
      isRefreshing = false;
    }
  }
);
