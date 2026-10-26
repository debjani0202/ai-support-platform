import { describe, expect, it } from 'vitest';
import {
  forgotPasswordSchema,
  resetPasswordSchema,
} from '@/lib/validation/auth.schema';

describe('forgotPasswordSchema', () => {
  it('validates a valid email address', () => {
    const result = forgotPasswordSchema.safeParse({ email: 'user@example.com' });
    expect(result.success).toBe(true);
  });

  it('rejects an empty email address', () => {
    const result = forgotPasswordSchema.safeParse({ email: '' });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues[0].message).toBe('Email is required');
    }
  });

  it('rejects an invalid email format', () => {
    const result = forgotPasswordSchema.safeParse({ email: 'not-an-email' });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues[0].message).toBe('Email must be valid');
    }
  });
});

describe('resetPasswordSchema', () => {
  it('validates matching passwords within valid range', () => {
    const result = resetPasswordSchema.safeParse({
      password: 'StrongPassword123',
      confirmPassword: 'StrongPassword123',
    });
    expect(result.success).toBe(true);
  });

  it('rejects password shorter than 8 characters', () => {
    const result = resetPasswordSchema.safeParse({
      password: 'short',
      confirmPassword: 'short',
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues[0].message).toBe('Password must be between 8 and 72 characters');
    }
  });

  it('rejects password longer than 72 characters', () => {
    const longPassword = 'a'.repeat(73);
    const result = resetPasswordSchema.safeParse({
      password: longPassword,
      confirmPassword: longPassword,
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues[0].message).toBe('Password must not exceed 72 characters');
    }
  });

  it('rejects mismatched passwords', () => {
    const result = resetPasswordSchema.safeParse({
      password: 'Password123',
      confirmPassword: 'DifferentPassword123',
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues[0].message).toBe('Passwords do not match');
    }
  });
});
