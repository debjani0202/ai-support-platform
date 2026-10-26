import { apiClient } from './client';
import type {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  AuthResponse,
  ChangePasswordRequest,
  ForgotPasswordRequest,
  ForgotPasswordResponse,
  VerifyResetTokenResponse,
  ResetPasswordRequest,
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

/**
 * Requests password reset link via POST /api/auth/forgot-password.
 * Matches com.supportai.auth.controller.AuthController#forgotPassword
 */
export async function forgotPasswordApi(data: ForgotPasswordRequest): Promise<ForgotPasswordResponse> {
  const response = await apiClient.post<ForgotPasswordResponse>('/auth/forgot-password', data);
  return response.data;
}

/**
 * Verifies validity of a password reset token via GET /api/auth/verify-reset-token.
 * Matches com.supportai.auth.controller.AuthController#verifyResetToken
 */
export async function verifyResetTokenApi(token: string): Promise<VerifyResetTokenResponse> {
  const response = await apiClient.get<VerifyResetTokenResponse>('/auth/verify-reset-token', {
    params: { token },
  });
  return response.data;
}

/**
 * Resets user password using reset token via POST /api/auth/reset-password.
 * Matches com.supportai.auth.controller.AuthController#resetPassword
 * Backend returns HTTP 204 No Content on success.
 */
export async function resetPasswordApi(data: ResetPasswordRequest): Promise<void> {
  await apiClient.post<void>('/auth/reset-password', data);
}

