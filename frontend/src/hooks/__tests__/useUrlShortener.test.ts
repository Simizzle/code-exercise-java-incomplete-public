import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useUrlShortener } from '../useUrlShortener'; // adjust path if needed
import { api } from '../../services/api';
import type { PaginatedResponse, UrlListItem } from '../../types/api';

vi.mock('../../services/api', () => ({
  api: {
    listAll: vi.fn(),
    shorten: vi.fn(),
    delete: vi.fn(),
  },
}));

const mockedApi = vi.mocked(api);

function makeItem(alias: string): UrlListItem {
  return { alias, fullUrl: `https://${alias}.example.com`, shortUrl: `http://host/${alias}` };
}

function makePage(
  content: UrlListItem[],
  overrides: { page?: number; totalElements?: number; totalPages?: number } = {}
): PaginatedResponse<UrlListItem> {
  return {
    content,
    page: 0,
    size: 10,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    ...overrides,
  } as PaginatedResponse<UrlListItem>;
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

async function renderLoaded() {
  const hook = renderHook(() => useUrlShortener());
  await waitFor(() => expect(hook.result.current.loading).toBe(false));
  return hook;
}

describe('useUrlShortener', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockedApi.listAll.mockResolvedValue(makePage([]));
  });

  describe('initial load', () => {
    it('fetches page 0 with size 10 on mount', async () => {
      await renderLoaded();

      expect(mockedApi.listAll).toHaveBeenCalledTimes(1);
      expect(mockedApi.listAll).toHaveBeenCalledWith(0, 10);
    });

    it('is loading while the request is in flight', async () => {
      const d = deferred<PaginatedResponse<UrlListItem>>();
      mockedApi.listAll.mockReturnValue(d.promise);

      const { result } = renderHook(() => useUrlShortener());

      await waitFor(() => expect(result.current.loading).toBe(true));

      await act(async () => {
        d.resolve(makePage([]));
      });

      expect(result.current.loading).toBe(false);
    });

    it('populates urls and pagination state from the response', async () => {
      const items = [makeItem('a'), makeItem('b')];
      mockedApi.listAll.mockResolvedValue(
        makePage(items, { page: 0, totalElements: 25, totalPages: 3 })
      );

      const { result } = await renderLoaded();

      expect(result.current.urls).toEqual(items);
      expect(result.current.page).toBe(0);
      expect(result.current.size).toBe(10);
      expect(result.current.totalElements).toBe(25);
      expect(result.current.totalPages).toBe(3);
      expect(result.current.error).toBeNull();
      expect(result.current.lastCreated).toBeNull();
    });

    it('sets the error message when loading fails', async () => {
      mockedApi.listAll.mockRejectedValue(new Error('Failed to fetch URLs.'));

      const { result } = await renderLoaded();

      expect(result.current.error).toBe('Failed to fetch URLs.');
      expect(result.current.urls).toEqual([]);
    });

    it('uses a fallback message when a non-Error is thrown', async () => {
      mockedApi.listAll.mockRejectedValue('boom');

      const { result } = await renderLoaded();

      expect(result.current.error).toBe('Failed to load URLs.');
    });
  });

  describe('shorten', () => {
    const created = { alias: 'abc', fullUrl: 'https://ex.com', shortUrl: 'http://host/abc' };

    it('calls api.shorten with the url and alias', async () => {
      mockedApi.shorten.mockResolvedValue(created);
      const { result } = await renderLoaded();

      await act(async () => {
        await result.current.shorten('https://ex.com', 'abc');
      });

      expect(mockedApi.shorten).toHaveBeenCalledWith({ fullUrl: 'https://ex.com', customAlias: 'abc' });
    });

    it('sends customAlias as undefined when omitted or empty', async () => {
      mockedApi.shorten.mockResolvedValue(created);
      const { result } = await renderLoaded();

      await act(async () => {
        await result.current.shorten('https://ex.com');
      });
      await act(async () => {
        await result.current.shorten('https://ex.com', '');
      });

      expect(mockedApi.shorten).toHaveBeenNthCalledWith(1, { fullUrl: 'https://ex.com', customAlias: undefined });
      expect(mockedApi.shorten).toHaveBeenNthCalledWith(2, { fullUrl: 'https://ex.com', customAlias: undefined });
    });

    it('stores the result in lastCreated', async () => {
      mockedApi.shorten.mockResolvedValue(created);
      const { result } = await renderLoaded();

      await act(async () => {
        await result.current.shorten('https://ex.com', 'abc');
      });

      expect(result.current.lastCreated).toEqual(created);
      expect(result.current.error).toBeNull();
    });

    it('refreshes the list from page 0 afterwards', async () => {
      mockedApi.shorten.mockResolvedValue(created);
      mockedApi.listAll
        .mockResolvedValueOnce(makePage([], { page: 2, totalPages: 3 })) // initial load
        .mockResolvedValueOnce(makePage([makeItem('abc')], { page: 0, totalElements: 1, totalPages: 1 }));
      const { result } = await renderLoaded();

      await act(async () => {
        await result.current.shorten('https://ex.com', 'abc');
      });

      expect(mockedApi.listAll).toHaveBeenCalledTimes(2);
      expect(mockedApi.listAll).toHaveBeenLastCalledWith(0, 10);
      expect(result.current.urls).toEqual([makeItem('abc')]);
      expect(result.current.page).toBe(0);
    });

    it('sets the error and does not refresh when api.shorten fails', async () => {
      mockedApi.shorten.mockRejectedValue(new Error('Alias already taken.'));
      const { result } = await renderLoaded();

      await act(async () => {
        await result.current.shorten('https://ex.com', 'taken');
      });

      expect(result.current.error).toBe('Alias already taken.');
      expect(result.current.lastCreated).toBeNull();
      expect(mockedApi.listAll).toHaveBeenCalledTimes(1); // initial load only
    });

    it('uses a fallback message when a non-Error is thrown', async () => {
      mockedApi.shorten.mockRejectedValue('boom');
      const { result } = await renderLoaded();

      await act(async () => {
        await result.current.shorten('https://ex.com');
      });

      expect(result.current.error).toBe('Failed to shorten URL.');
    });

    it('clears a previous error and lastCreated when starting a new request', async () => {
      mockedApi.shorten.mockResolvedValueOnce(created);
      const { result } = await renderLoaded();
      await act(async () => {
        await result.current.shorten('https://ex.com', 'abc');
      });
      expect(result.current.lastCreated).toEqual(created);

      mockedApi.shorten.mockRejectedValueOnce(new Error('nope'));
      await act(async () => {
        await result.current.shorten('https://other.com');
      });

      expect(result.current.lastCreated).toBeNull();
      expect(result.current.error).toBe('nope');
    });

    it('keeps lastCreated when the follow-up list refresh fails', async () => {
      mockedApi.shorten.mockResolvedValue(created);
      const { result } = await renderLoaded();
      mockedApi.listAll.mockRejectedValueOnce(new Error('Failed to fetch URLs.'));

      await act(async () => {
        await result.current.shorten('https://ex.com', 'abc');
      });

      expect(result.current.lastCreated).toEqual(created);
      expect(result.current.error).toBe('Failed to fetch URLs.');
    });
  });

  describe('deleteUrl', () => {
    it('calls api.delete and refreshes the current page', async () => {
      mockedApi.listAll.mockResolvedValue(
        makePage([makeItem('a'), makeItem('b')], { page: 0, totalElements: 2, totalPages: 1 })
      );
      mockedApi.delete.mockResolvedValue(undefined);
      const { result } = await renderLoaded();

      await act(async () => {
        await result.current.deleteUrl('a');
      });

      expect(mockedApi.delete).toHaveBeenCalledWith('a');
      expect(mockedApi.listAll).toHaveBeenCalledTimes(2);
      expect(mockedApi.listAll).toHaveBeenLastCalledWith(0, 10);
    });

    it('stays on the same page when other items remain on it', async () => {
      mockedApi.listAll.mockResolvedValue(
        makePage([makeItem('a'), makeItem('b')], { page: 1, totalElements: 12, totalPages: 2 })
      );
      mockedApi.delete.mockResolvedValue(undefined);
      const { result } = await renderLoaded();

      await act(async () => {
        await result.current.deleteUrl('a');
      });

      expect(mockedApi.listAll).toHaveBeenLastCalledWith(1, 10);
    });

    it('goes back one page when deleting the last item on a page after page 0', async () => {
      mockedApi.listAll.mockResolvedValue(
        makePage([makeItem('only')], { page: 2, totalElements: 21, totalPages: 3 })
      );
      mockedApi.delete.mockResolvedValue(undefined);
      const { result } = await renderLoaded();

      await act(async () => {
        await result.current.deleteUrl('only');
      });

      expect(mockedApi.listAll).toHaveBeenLastCalledWith(1, 10);
    });

    it('stays on page 0 when deleting the last remaining item', async () => {
      mockedApi.listAll.mockResolvedValue(
        makePage([makeItem('only')], { page: 0, totalElements: 1, totalPages: 1 })
      );
      mockedApi.delete.mockResolvedValue(undefined);
      const { result } = await renderLoaded();

      await act(async () => {
        await result.current.deleteUrl('only');
      });

      expect(mockedApi.listAll).toHaveBeenLastCalledWith(0, 10);
    });

    it('sets the error and does not refresh when api.delete fails', async () => {
      mockedApi.listAll.mockResolvedValue(makePage([makeItem('a')]));
      mockedApi.delete.mockRejectedValue(new Error("No URL found for alias 'a'."));
      const { result } = await renderLoaded();

      await act(async () => {
        await result.current.deleteUrl('a');
      });

      expect(result.current.error).toBe("No URL found for alias 'a'.");
      expect(mockedApi.listAll).toHaveBeenCalledTimes(1); // initial load only
    });

    it('uses a fallback message when a non-Error is thrown', async () => {
      mockedApi.delete.mockRejectedValue('boom');
      const { result } = await renderLoaded();

      await act(async () => {
        await result.current.deleteUrl('a');
      });

      expect(result.current.error).toBe('Failed to delete URL.');
    });

    it('clears a previous error on a successful delete', async () => {
      mockedApi.listAll.mockResolvedValue(makePage([makeItem('a'), makeItem('b')]));
      mockedApi.delete.mockRejectedValueOnce(new Error('first failure'));
      const { result } = await renderLoaded();
      await act(async () => {
        await result.current.deleteUrl('a');
      });
      expect(result.current.error).toBe('first failure');

      mockedApi.delete.mockResolvedValueOnce(undefined);
      await act(async () => {
        await result.current.deleteUrl('a');
      });

      expect(result.current.error).toBeNull();
    });
  });

  describe('changePage', () => {
    beforeEach(() => {
      mockedApi.listAll.mockResolvedValue(
        makePage([makeItem('a')], { page: 0, totalElements: 30, totalPages: 3 })
      );
    });

    it('fetches the requested page and updates state from the response', async () => {
      const { result } = await renderLoaded();
      mockedApi.listAll.mockResolvedValueOnce(
        makePage([makeItem('b')], { page: 2, totalElements: 30, totalPages: 3 })
      );

      act(() => {
        result.current.changePage(2);
      });

      await waitFor(() => expect(result.current.page).toBe(2));
      expect(mockedApi.listAll).toHaveBeenLastCalledWith(2, 10);
      expect(result.current.urls).toEqual([makeItem('b')]);
    });

    it('ignores negative pages', async () => {
      const { result } = await renderLoaded();

      act(() => {
        result.current.changePage(-1);
      });

      expect(mockedApi.listAll).toHaveBeenCalledTimes(1);
    });

    it('ignores pages at or beyond totalPages', async () => {
      const { result } = await renderLoaded();

      act(() => {
        result.current.changePage(3); // totalPages is 3, so the last valid index is 2
        result.current.changePage(99);
      });

      expect(mockedApi.listAll).toHaveBeenCalledTimes(1);
    });

    it('ignores every page when there are no results (totalPages is 0)', async () => {
      mockedApi.listAll.mockResolvedValue(makePage([]));
      const { result } = await renderLoaded();

      act(() => {
        result.current.changePage(0);
      });

      expect(mockedApi.listAll).toHaveBeenCalledTimes(1);
    });
  });

  describe('clearLastCreated', () => {
    it('resets lastCreated to null', async () => {
      const created = { alias: 'abc', fullUrl: 'https://ex.com', shortUrl: 'http://host/abc' };
      mockedApi.shorten.mockResolvedValue(created);
      const { result } = await renderLoaded();
      await act(async () => {
        await result.current.shorten('https://ex.com', 'abc');
      });
      expect(result.current.lastCreated).toEqual(created);

      act(() => {
        result.current.clearLastCreated();
      });

      expect(result.current.lastCreated).toBeNull();
    });
  });
});
