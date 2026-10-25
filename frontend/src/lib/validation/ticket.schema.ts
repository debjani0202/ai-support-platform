import { z } from 'zod';

export const createTicketSchema = z.object({
  title: z
    .string()
    .trim()
    .min(1, 'Title is required')
    .max(255, 'Title must not exceed 255 characters'),
  description: z
    .string()
    .trim()
    .min(1, 'Description is required'),
  priority: z.enum(['LOW', 'MEDIUM', 'HIGH', 'URGENT'], {
    errorMap: () => ({ message: 'Priority is required' }),
  }),
  category: z
    .enum([
      'ACCOUNT_ACCESS',
      'PAYMENT',
      'TECHNICAL_ISSUE',
      'BUG_REPORT',
      'FEATURE_REQUEST',
      'GENERAL_QUERY',
    ])
    .nullable()
    .optional(),
});

export type CreateTicketFormValues = z.infer<typeof createTicketSchema>;
