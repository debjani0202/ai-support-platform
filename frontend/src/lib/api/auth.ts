import { apiClient } from './client';
import type {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  AuthResponse,
  ChangePasswordRequest,
} from '@/types/auth.types';

/**
 * Authenticates user credentials via POST /api/auth/login.
 * Matches com.supportai.auth.controller.AuthController#login
 */
export async function loginApi(credentials: LoginRequest): Promise<LoginResponse> {
  const response = await apiClient.post<LoginResponse>('/auth/login', credentials);
  return response.data;
}

/**
 * Registers a new customer account via POST /api/auth/register.
 * Matches com.supportai.auth.controller.AuthController#register
 */
export async function registerApi(data: RegisterRequest): Promise<AuthResponse> {
  const response = await apiClient.post<AuthResponse>('/auth/register', data);
  return response.data;
}

/**
 * Changes password of authenticated user via POST /api/auth/change-password.
 * Matches com.supportai.auth.controller.AuthController#changePassword
 * Backend returns HTTP 204 No Content on success.
 */
export async function changePasswordApi(data: ChangePasswordRequest): Promise<void> {
  await apiClient.post<void>('/auth/change-password', data);
}
