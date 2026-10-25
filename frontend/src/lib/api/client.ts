import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import type { ApiErrorResponse } from '@/types/api.types';
import type { StoredSession } from '@/types/auth.types';

export const SESSION_STORAGE_KEY = 'ai_support_session';

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
 * Centralized Axios client configured with base URL, timeout,
 * bearer token injection, and structured error unwrapping.
 */
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request Interceptor: Attach Bearer token if present
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getStoredToken();
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response Interceptor: Normalize errors and handle 401 session expiration
apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiErrorResponse>) => {
    // When the backend returns 401, the JWT is expired or invalid
    if (error.response?.status === 401) {
      clearStoredSession();
      // Dispatch custom window event so auth listeners can react cleanly
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('auth:session-expired'));
      }
    }

    // Extract backend error message or structured validation errors
    const errorData = error.response?.data;
    const message =
      errorData?.message ||
      (errorData?.errors ? Object.values(errorData.errors).join(', ') : null) ||
      error.message ||
      'An unexpected network error occurred';

    return Promise.reject({
      status: error.response?.status ?? 500,
      error: errorData?.error ?? 'Error',
      message,
      errors: errorData?.errors,
      raw: error,
    });
  }
);
