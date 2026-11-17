import { describe, it, expect, vi, beforeEach } from 'vitest';
import { api } from '../api';

describe('api service', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('shorten sends POST to /shorten with correct body', async () => {
    const mockResponse = { alias: 'abc', fullUrl: 'https://ex.com', shortUrl: 'http://host/abc' };
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 201,
      json: async () => mockResponse,
    } as Response);

    const result = await api.shorten({ fullUrl: 'https://ex.com', customAlias: 'abc' });

    expect(fetch).toHaveBeenCalledWith('/shorten', expect.objectContaining({
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ fullUrl: 'https://ex.com', customAlias: 'abc' }),
    }));
    expect(result).toEqual(mockResponse);
  });

  it('shorten throws with error message from API on failure', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({ error: 'Alias already taken.' }),
    } as Response);

    await expect(api.shorten({ fullUrl: 'https://ex.com', customAlias: 'taken' }))
      .rejects.toThrow('Alias already taken.');
  });

  it('listAll sends GET to /urls', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => [],
    } as Response);

    await api.listAll();

    expect(fetch).toHaveBeenCalledWith('/urls?page=0&size=10');
  });

  it('delete sends DELETE to /{alias}', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 204,
      json: async () => undefined,
    } as Response);

    await api.delete('my-alias');

    expect(fetch).toHaveBeenCalledWith('/my-alias', expect.objectContaining({ method: 'DELETE' }));
  });

  it('delete throws on 404', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
      json: async () => ({ error: "No URL found for alias 'ghost'." }),
    } as Response);

    await expect(api.delete('ghost'))
      .rejects.toThrow("No URL found for alias 'ghost'.");
  });

  it('listAll passes custom page and size in the query string', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({}),
      } as Response);

      await api.listAll(3, 25);

      expect(fetch).toHaveBeenCalledWith('/urls?page=3&size=25');
    });

    it('listAll returns the parsed response body', async () => {
      const pageBody = {
        items: [{ alias: 'abc', fullUrl: 'https://example.com', shortUrl: 'http://localhost/abc' }],
        page: 0,
        size: 10,
        totalItems: 1,
        totalPages: 1,
      };
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => pageBody,
      } as Response);

      await expect(api.listAll()).resolves.toEqual(pageBody);
    });

    it('listAll throws a generic error when the response is not ok', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
        json: async () => ({ error: 'boom' }),
      } as Response);

      await expect(api.listAll()).rejects.toThrow('Failed to fetch URLs.');
    });

    it('listAll throws on 404', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 404,
        json: async () => ({}),
      } as Response);

      await expect(api.listAll()).rejects.toThrow('Failed to fetch URLs.');
    });

    it('listAll propagates network failures', async () => {
      globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

      await expect(api.listAll()).rejects.toThrow('Failed to fetch');
    });

    it('delete URL-encodes the alias', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 204,
        json: async () => undefined,
      } as Response);

      await api.delete('my alias/with?odd#chars');

      expect(fetch).toHaveBeenCalledWith(
        `/${encodeURIComponent('my alias/with?odd#chars')}`,
        expect.objectContaining({ method: 'DELETE' })
      );
    });

    it('delete resolves to undefined on 204 No Content', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 204,
        json: async () => { throw new Error('204 has no body'); },
      } as unknown as Response);

      await expect(api.delete('my-alias')).resolves.toBeUndefined();
    });

    it('delete falls back to a status-based message when the body has no error field', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 403,
        json: async () => ({ message: 'nope' }),
      } as Response);

      await expect(api.delete('my-alias')).rejects.toThrow('Request failed (403)');
    });

    it('delete propagates network failures', async () => {
      globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

      await expect(api.delete('my-alias')).rejects.toThrow('Failed to fetch');
    });
});
