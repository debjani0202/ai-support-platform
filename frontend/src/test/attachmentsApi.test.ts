import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import axios, { AxiosError, AxiosHeaders, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios';
import {
  deleteTicketAttachmentApi,
  downloadTicketAttachmentApi,
  getTicketAttachmentsApi,
  parseContentDispositionFilename,
  uploadTicketAttachmentApi,
} from '@/lib/api/attachments';
import { apiClient, clearStoredSession, SESSION_STORAGE_KEY } from '@/lib/api/client';
import type { AttachmentResponse } from '@/types/attachment.types';
import type { PageResponse } from '@/types/api.types';
import type { StoredSession } from '@/types/auth.types';

vi.mock('axios', async () => {
  const actual = await vi.importActual<typeof import('axios')>('axios');
  const mockPost = vi.fn();
  const mockCreate = vi.fn().mockImplementation((config) => actual.default.create(config));

  return {
    default: {
      ...actual.default,
      create: mockCreate,
      post: mockPost,
    },
    AxiosError: actual.AxiosError,
    AxiosHeaders: actual.AxiosHeaders,
  };
});

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

function setStoredAccessToken(accessToken: string): void {
  const session: StoredSession = {
    userId: 1,
    name: 'Test Customer',
    email: 'customer@example.com',
    role: 'CUSTOMER',
    accessToken,
    tokenType: 'Bearer',
  };
  localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
}

function makeAxiosHttpError(
  status: number,
  config: InternalAxiosRequestConfig,
  data: unknown,
  headers: Record<string, string> = {}
): AxiosError {
  return new AxiosError(
    `Request failed with status code ${status}`,
    status >= 500 ? AxiosError.ERR_BAD_RESPONSE : AxiosError.ERR_BAD_REQUEST,
    config,
    undefined,
    {
      status,
      statusText: String(status),
      headers,
      config,
      data,
    }
  );
}

describe('Attachment API integration', () => {
  const originalAdapter = apiClient.defaults.adapter;

  beforeEach(() => {
    localStorage.clear();
    clearStoredSession();
    vi.clearAllMocks();
    delete (navigator as unknown as { locks?: unknown }).locks;
  });

  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter;
    localStorage.clear();
    clearStoredSession();
    vi.restoreAllMocks();
    delete (navigator as unknown as { locks?: unknown }).locks;
  });

  describe('getTicketAttachmentsApi', () => {
    it('sends GET with default and explicit pagination parameters and returns full PageResponse', async () => {
      const token = createTestJwt();
      setStoredAccessToken(token);

      const pagePayload: PageResponse<AttachmentResponse> = {
        content: [
          {
            id: 10,
            ticketId: 42,
            uploadedById: 1,
            uploadedByName: 'Test Customer',
            originalFileName: 'invoice.pdf',
            contentType: 'application/pdf',
            fileSize: 2048,
            createdAt: '2026-11-12T10:00:00',
          },
        ],
        page: 1,
        size: 10,
        totalElements: 11,
        totalPages: 2,
        first: false,
        last: true,
      };

      const capturedConfigs: InternalAxiosRequestConfig[] = [];
      const mockAdapter: AxiosAdapter = async (config) => {
        capturedConfigs.push(config);
        return {
          data: pagePayload,
          status: 200,
          statusText: 'OK',
          headers: {},
          config,
        };
      };
      apiClient.defaults.adapter = mockAdapter;

      const defaultResult = await getTicketAttachmentsApi(42);
      const customResult = await getTicketAttachmentsApi(42, { page: 1, size: 10 });

      expect(capturedConfigs[0].method).toBe('get');
      expect(capturedConfigs[0].url).toBe('/tickets/42/attachments');
      expect(capturedConfigs[0].params).toEqual({ page: 0, size: 20 });
      expect(capturedConfigs[0].headers.get('Authorization')).toBe(`Bearer ${token}`);

      expect(capturedConfigs[1].params).toEqual({ page: 1, size: 10 });
      expect(customResult).toEqual(pagePayload);
      expect(defaultResult.totalElements).toBe(11);
      expect(defaultResult.content).toHaveLength(1);
    });
  });

  describe('uploadTicketAttachmentApi & FormData header handling', () => {
    it('submits FormData with field name "file", strips default JSON Content-Type, and keeps Authorization', async () => {
      const token = createTestJwt();
      setStoredAccessToken(token);

      const createdAttachment: AttachmentResponse = {
        id: 55,
        ticketId: 7,
        uploadedById: 1,
        uploadedByName: 'Test Customer',
        originalFileName: 'screenshot.png',
        contentType: 'image/png',
        fileSize: 4096,
        createdAt: '2026-11-12T10:15:00',
      };

      let capturedConfig: InternalAxiosRequestConfig | undefined;
      apiClient.defaults.adapter = async (config) => {
        capturedConfig = config;
        config.onUploadProgress?.({ loaded: 50, total: 100 } as never);
        return {
          data: createdAttachment,
          status: 201,
          statusText: 'Created',
          headers: {},
          config,
        };
      };

      const progressValues: number[] = [];
      const controller = new AbortController();
      const file = new File(['png-bytes'], 'screenshot.png', { type: 'image/png' });

      const result = await uploadTicketAttachmentApi({
        ticketId: 7,
        file,
        onUploadProgress: (pct) => progressValues.push(pct),
        signal: controller.signal,
      });

      expect(result).toEqual(createdAttachment);
      expect(capturedConfig?.method).toBe('post');
      expect(capturedConfig?.url).toBe('/tickets/7/attachments');
      expect(capturedConfig?.timeout).toBe(60000);
      expect(capturedConfig?.signal).toBe(controller.signal);
      expect(capturedConfig?.data).toBeInstanceOf(FormData);
      expect((capturedConfig?.data as FormData).get('file')).toBe(file);

      // Default application/json Content-Type is stripped before Axios transformRequest runs,
      // keeping data as raw FormData instead of JSON-stringifying it, and never manually setting multipart.
      expect(capturedConfig?.headers.get('Content-Type')).not.toBe('application/json');
      expect(capturedConfig?.headers.get('Content-Type')).not.toContain('multipart/form-data');
      expect(capturedConfig?.headers.get('Authorization')).toBe(`Bearer ${token}`);
      expect(progressValues).toEqual([50]);
    });

    it('lets browser XHR manage Content-Type boundary automatically without sending JSON or manual multipart Content-Type', async () => {
      const token = createTestJwt();
      setStoredAccessToken(token);

      const recordedHeaders: Array<[string, string]> = [];
      let sentBody: unknown;

      class FakeXhr {
        status = 201;
        statusText = 'Created';
        responseText = JSON.stringify({
          id: 77,
          ticketId: 3,
          uploadedById: 1,
          uploadedByName: 'Test Customer',
          originalFileName: 'report.pdf',
          contentType: 'application/pdf',
          fileSize: 512,
          createdAt: '2026-11-12T10:00:00',
        });
        response = this.responseText;
        timeout = 0;
        withCredentials = false;
        upload = { addEventListener: vi.fn() };
        onloadend: (() => void) | null = null;
        open = vi.fn();
        setRequestHeader = vi.fn((key: string, value: string) => {
          recordedHeaders.push([key, value]);
        });
        getAllResponseHeaders = vi.fn(() => 'content-type: application/json\r\n');
        send = vi.fn((body: unknown) => {
          sentBody = body;
          this.onloadend?.();
        });
      }

      const originalXhr = globalThis.XMLHttpRequest;
      vi.stubGlobal('XMLHttpRequest', FakeXhr);
      apiClient.defaults.adapter = originalAdapter;

      try {
        const file = new File(['%PDF-1.7'], 'report.pdf', { type: 'application/pdf' });
        const result = await uploadTicketAttachmentApi({ ticketId: 3, file });

        expect(result.id).toBe(77);
        expect(sentBody).toBeInstanceOf(FormData);
        const contentTypeHeaders = recordedHeaders.filter(
          ([key]) => key.toLowerCase() === 'content-type'
        );
        expect(contentTypeHeaders).toHaveLength(0);
        const authHeaders = recordedHeaders.filter(([key]) => key.toLowerCase() === 'authorization');
        expect(authHeaders).toEqual([['Authorization', `Bearer ${token}`]]);
      } finally {
        vi.stubGlobal('XMLHttpRequest', originalXhr);
      }
    });

    it('removes both Content-Type and content-type on plain-object and AxiosHeaders headers for FormData requests', () => {
      const token = createTestJwt();
      setStoredAccessToken(token);

      const formData = new FormData();
      formData.append('file', new File(['hello'], 'notes.txt', { type: 'text/plain' }));

      const plainHeadersConfig = {
        url: '/tickets/9/attachments',
        data: formData,
        headers: {
          'Content-Type': 'application/json',
          'content-type': 'application/json',
        },
      };

      const interceptor = (apiClient.interceptors.request as unknown as {
        handlers: Array<{
          fulfilled: (cfg: unknown) => {
            headers: Record<string, unknown> & { get?: (name: string) => unknown };
          };
        }>;
      }).handlers[0].fulfilled;

      const processedPlain = interceptor(plainHeadersConfig);
      expect(processedPlain.headers['Content-Type']).toBeUndefined();
      expect(processedPlain.headers['content-type']).toBeUndefined();
      expect(processedPlain.headers.Authorization).toBe(`Bearer ${token}`);

      const axiosHeadersConfig = {
        url: '/tickets/9/attachments',
        data: formData,
        headers: new AxiosHeaders({
          'Content-Type': 'application/json',
          'content-type': 'application/json',
        }),
      };

      const processedAxiosHeaders = interceptor(axiosHeadersConfig);
      expect(processedAxiosHeaders.headers.get?.('Content-Type')).toBeUndefined();
      expect(processedAxiosHeaders.headers.get?.('content-type')).toBeUndefined();
      expect(processedAxiosHeaders.headers.get?.('Authorization')).toBe(`Bearer ${token}`);
    });

    it('preserves application/json Content-Type for standard JSON POST and PUT requests', async () => {
      const token = createTestJwt();
      setStoredAccessToken(token);

      const capturedHeaders: Array<{ contentType: unknown; data: unknown }> = [];
      apiClient.defaults.adapter = async (config) => {
        capturedHeaders.push({
          contentType: config.headers.get('Content-Type'),
          data: config.data,
        });
        return {
          data: { ok: true },
          status: 200,
          statusText: 'OK',
          headers: {},
          config,
        };
      };

      await apiClient.post('/tickets/1/comments', { content: 'Checking JSON POST' });
      await apiClient.put('/tickets/1', { title: 'Updated' });

      expect(capturedHeaders[0].contentType).toBe('application/json');
      expect(capturedHeaders[0].data).toBe(JSON.stringify({ content: 'Checking JSON POST' }));
      expect(capturedHeaders[1].contentType).toBe('application/json');
      expect(capturedHeaders[1].data).toBe(JSON.stringify({ title: 'Updated' }));
    });
  });

  describe('downloadTicketAttachmentApi & parseContentDispositionFilename', () => {
    it('requests Blob response and returns blob, parsed UTF-8 filename, and contentType', async () => {
      const token = createTestJwt();
      setStoredAccessToken(token);

      const expectedBlob = new Blob(['%PDF-1.7 test'], { type: 'application/pdf' });
      let capturedConfig: InternalAxiosRequestConfig | undefined;

      apiClient.defaults.adapter = async (config) => {
        capturedConfig = config;
        return {
          data: expectedBlob,
          status: 200,
          statusText: 'OK',
          headers: new AxiosHeaders({
            'content-disposition':
              "attachment; filename=\"fallback.pdf\"; filename*=UTF-8''%E2%9C%93%20Q4%20Report.pdf",
            'content-type': 'application/pdf',
          }),
          config,
        };
      };

      const controller = new AbortController();
      const result = await downloadTicketAttachmentApi(15, 99, 'fallback.pdf', controller.signal);

      expect(capturedConfig?.method).toBe('get');
      expect(capturedConfig?.url).toBe('/tickets/15/attachments/99/download');
      expect(capturedConfig?.responseType).toBe('blob');
      expect(capturedConfig?.timeout).toBe(60000);
      expect(capturedConfig?.signal).toBe(controller.signal);
      expect(capturedConfig?.headers.get('Authorization')).toBe(`Bearer ${token}`);

      expect(result.blob).toBe(expectedBlob);
      expect(result.fileName).toBe('✓ Q4 Report.pdf');
      expect(result.contentType).toBe('application/pdf');
    });

    it('parses RFC 5987, quoted, unquoted, malformed, missing, and unsafe filenames safely', () => {
      // RFC 5987 UTF-8 filename takes precedence
      expect(
        parseContentDispositionFilename(
          "attachment; filename=\"ascii.txt\"; filename*=UTF-8''caf%C3%A9%20notes.txt",
          'fallback.txt'
        )
      ).toBe('café notes.txt');

      // Quoted and unquoted ASCII filenames
      expect(parseContentDispositionFilename('attachment; filename="invoice-2026.pdf"')).toBe(
        'invoice-2026.pdf'
      );
      expect(parseContentDispositionFilename('attachment; filename=plain_log.txt')).toBe(
        'plain_log.txt'
      );

      // Malformed percent encoding falls back safely to standard filename or fallback
      expect(
        parseContentDispositionFilename(
          "attachment; filename=\"safe.txt\"; filename*=UTF-8''bad%ZZencoding.txt",
          'fallback.txt'
        )
      ).toBe('safe.txt');
      expect(
        parseContentDispositionFilename("attachment; filename*=UTF-8''bad%ZZencoding.txt", 'fallback.txt')
      ).toBe('fallback.txt');

      // Missing header / missing filename falls back safely
      expect(parseContentDispositionFilename(undefined, 'ticket-doc.pdf')).toBe('ticket-doc.pdf');
      expect(parseContentDispositionFilename('attachment')).toBe('attachment');

      // Path traversal, separators, NUL/control characters, and bidi overrides are stripped
      expect(
        parseContentDispositionFilename('attachment; filename="../../etc/passwd"', 'fallback.txt')
      ).toBe('passwd');
      expect(
        parseContentDispositionFilename('attachment; filename="C:\\temp\\..\\evil.pdf"', 'fallback.pdf')
      ).toBe('evil.pdf');
      expect(
        parseContentDispositionFilename('attachment; filename="..\\.."', 'safe-default.pdf')
      ).toBe('safe-default.pdf');
      expect(
        parseContentDispositionFilename('attachment; filename="bad\u0000\u001F\u202Ename.txt"')
      ).toBe('badname.txt');
    });
  });

  describe('deleteTicketAttachmentApi', () => {
    it('sends DELETE and resolves void on HTTP 204', async () => {
      const token = createTestJwt();
      setStoredAccessToken(token);

      let capturedConfig: InternalAxiosRequestConfig | undefined;
      apiClient.defaults.adapter = async (config) => {
        capturedConfig = config;
        return {
          data: '',
          status: 204,
          statusText: 'No Content',
          headers: {},
          config,
        };
      };

      await expect(deleteTicketAttachmentApi(12, 34)).resolves.toBeUndefined();
      expect(capturedConfig?.method).toBe('delete');
      expect(capturedConfig?.url).toBe('/tickets/12/attachments/34');
      expect(capturedConfig?.headers.get('Authorization')).toBe(`Bearer ${token}`);
    });
  });

  describe('Error decoding & status preservation', () => {
    it('decodes JSON Blob errors cleanly for download failures', async () => {
      setStoredAccessToken(createTestJwt());

      apiClient.defaults.adapter = async (config) => {
        const errorBlob = new Blob(
          [
            JSON.stringify({
              status: 404,
              error: 'Not Found',
              message: 'Attachment not found with ID: 999',
            }),
          ],
          { type: 'application/json' }
        );
        throw makeAxiosHttpError(404, config, errorBlob, { 'content-type': 'application/json' });
      };

      await expect(downloadTicketAttachmentApi(1, 999)).rejects.toMatchObject({
        status: 404,
        error: 'Not Found',
        message: 'Attachment not found with ID: 999',
      });
    });

    it('falls back safely without exposing raw HTML when Blob error payload is non-JSON', async () => {
      setStoredAccessToken(createTestJwt());

      apiClient.defaults.adapter = async (config) => {
        const htmlBlob = new Blob(['<html><body>Internal Proxy Error</body></html>'], {
          type: 'text/html',
        });
        throw makeAxiosHttpError(502, config, htmlBlob, { 'content-type': 'text/html' });
      };

      await expect(downloadTicketAttachmentApi(1, 2)).rejects.toMatchObject({
        status: 502,
        error: 'Error',
        message: 'Request failed with status code 502',
      });
    });

    it('falls back safely when Blob JSON is malformed', async () => {
      setStoredAccessToken(createTestJwt());

      apiClient.defaults.adapter = async (config) => {
        const badJsonBlob = new Blob(['{not-valid-json'], { type: 'application/json' });
        throw makeAxiosHttpError(403, config, badJsonBlob, { 'content-type': 'application/json' });
      };

      await expect(downloadTicketAttachmentApi(1, 2)).rejects.toMatchObject({
        status: 403,
        error: 'Error',
        message: 'Request failed with status code 403',
      });
    });

    it.each([
      {
        status: 400,
        error: 'Bad Request',
        message: 'Unsupported file extension: .exe',
      },
      {
        status: 403,
        error: 'Forbidden',
        message: 'You are not allowed to access this ticket',
      },
      {
        status: 404,
        error: 'Not Found',
        message: 'Ticket not found with ID: 404',
      },
      {
        status: 409,
        error: 'Conflict',
        message: 'Ticket has reached the maximum limit of 20 attachments',
      },
      {
        status: 409,
        error: 'Conflict',
        message: 'Cannot upload attachments to a closed ticket',
      },
      {
        status: 413,
        error: 'Payload Too Large',
        message: 'Uploaded file exceeds the maximum allowed size of 10 MB.',
      },
    ])('preserves HTTP $status error contract ($message)', async ({ status, error, message }) => {
      setStoredAccessToken(createTestJwt());

      apiClient.defaults.adapter = async (config) => {
        throw makeAxiosHttpError(status, config, { status, error, message });
      };

      const file = new File(['abc'], 'doc.pdf', { type: 'application/pdf' });
      await expect(uploadTicketAttachmentApi({ ticketId: 5, file })).rejects.toMatchObject({
        status,
        error,
        message,
      });
    });
  });

  describe('JWT refresh & replay compatibility', () => {
    it('refreshes expired access token and replays FormData upload preserving multipart handling', async () => {
      const staleToken = createTestJwt(60);
      const freshToken = createTestJwt(3600);
      setStoredAccessToken(staleToken);

      (axios.post as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
        data: {
          userId: 1,
          name: 'Test Customer',
          email: 'customer@example.com',
          role: 'CUSTOMER',
          accessToken: freshToken,
          tokenType: 'Bearer',
        },
      });

      const uploadedResponse: AttachmentResponse = {
        id: 88,
        ticketId: 11,
        uploadedById: 1,
        uploadedByName: 'Test Customer',
        originalFileName: 'log.txt',
        contentType: 'text/plain',
        fileSize: 128,
        createdAt: '2026-11-12T10:30:00',
      };

      const attempts: Array<{
        authHeader: unknown;
        isJsonContentType: boolean;
        isFormData: boolean;
      }> = [];
      apiClient.defaults.adapter = async (config) => {
        const contentType = String(config.headers.get('Content-Type') ?? '');
        attempts.push({
          authHeader: config.headers.get('Authorization'),
          isJsonContentType: contentType.includes('application/json'),
          isFormData: config.data instanceof FormData,
        });

        if (attempts.length === 1) {
          throw makeAxiosHttpError(401, config, { message: 'Token expired' });
        }

        return {
          data: uploadedResponse,
          status: 201,
          statusText: 'Created',
          headers: {},
          config,
        };
      };

      const file = new File(['log entry'], 'log.txt', { type: 'text/plain' });
      const result = await uploadTicketAttachmentApi({ ticketId: 11, file });

      expect(result).toEqual(uploadedResponse);
      expect(axios.post).toHaveBeenCalledTimes(1);
      expect(attempts).toHaveLength(2);
      expect(attempts[0]).toEqual({
        authHeader: `Bearer ${staleToken}`,
        isJsonContentType: false,
        isFormData: true,
      });
      expect(attempts[1]).toEqual({
        authHeader: `Bearer ${freshToken}`,
        isJsonContentType: false,
        isFormData: true,
      });
    });

    it('refreshes expired access token and replays Blob download preserving responseType', async () => {
      const staleToken = createTestJwt(60);
      const freshToken = createTestJwt(3600);
      setStoredAccessToken(staleToken);

      (axios.post as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
        data: {
          userId: 1,
          name: 'Test Customer',
          email: 'customer@example.com',
          role: 'CUSTOMER',
          accessToken: freshToken,
          tokenType: 'Bearer',
        },
      });

      const downloadedBlob = new Blob(['binary-image'], { type: 'image/png' });
      let callCount = 0;

      apiClient.defaults.adapter = async (config) => {
        callCount += 1;
        if (callCount === 1) {
          const errorBlob = new Blob([JSON.stringify({ message: 'Expired' })], {
            type: 'application/json',
          });
          throw makeAxiosHttpError(401, config, errorBlob);
        }

        return {
          data: downloadedBlob,
          status: 200,
          statusText: 'OK',
          headers: new AxiosHeaders({
            'content-disposition': 'attachment; filename="evidence.png"',
            'content-type': 'image/png',
          }),
          config,
        };
      };

      const result = await downloadTicketAttachmentApi(11, 88);

      expect(axios.post).toHaveBeenCalledTimes(1);
      expect(callCount).toBe(2);
      expect(result.blob).toBe(downloadedBlob);
      expect(result.fileName).toBe('evidence.png');
      expect(result.contentType).toBe('image/png');
    });
  });
});
