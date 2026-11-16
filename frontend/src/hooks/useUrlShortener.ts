import { useState, useCallback, useEffect } from 'react';
import { api } from '../services/api';
import type { ShortenUrlResponse, UrlListItem } from '../types/api';

interface UseUrlShortenerReturn {
  urls: UrlListItem[];
  loading: boolean;
  error: string | null;
  lastCreated: ShortenUrlResponse | null;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  shorten: (fullUrl: string, customAlias?: string) => Promise<void>;
  deleteUrl: (alias: string) => Promise<void>;
  clearLastCreated: () => void;
  changePage: (newPage: number) => void;
}

export function useUrlShortener(): UseUrlShortenerReturn {
  const [urls, setUrls] = useState<UrlListItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastCreated, setLastCreated] = useState<ShortenUrlResponse | null>(null);

  const [page, setPage] = useState(0);
  const [size] = useState(10);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const fetchPage = useCallback(async (pageNumber: number) => {
    setLoading(true);
    setError(null);

    try {
      const data = await api.listAll(pageNumber, size);

      setUrls(data.content);
      setPage(data.page);
      setTotalElements(data.totalElements);
      setTotalPages(data.totalPages);
    } catch (e) {
      setError(
        e instanceof Error ? e.message : 'Failed to load URLs.'
      );
    } finally {
      setLoading(false);
    }
  }, [size]);

   useEffect(() => {
    fetchPage(0);
  }, [fetchPage]);

  const shorten = useCallback(async (fullUrl: string, customAlias?: string) => {
    setError(null);
    setLastCreated(null);
    try {
      const result = await api.shorten({ fullUrl, customAlias: customAlias || undefined });
      setLastCreated(result);

      await fetchPage(0);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to shorten URL.');
    }
  }, [fetchPage]);

  const deleteUrl = useCallback(async (alias: string) => {
    setError(null);

    try {

      await api.delete(alias);

      const targetPage = urls.length === 1 && page > 0 ? page - 1 : page;

      await fetchPage(targetPage);
    } catch (e) {
      setError(
        e instanceof Error ? e.message : 'Failed to delete URL.'
      );
    }
  }, [fetchPage, urls.length, page]);

  const changePage = useCallback((newPage: number) => {
    if (newPage >= 0 && newPage < totalPages) {
      fetchPage(newPage);
    }
  }, [fetchPage, totalPages]);

  const clearLastCreated = useCallback(
    () => setLastCreated(null),
    []
  );

  return {
    urls,
    loading,
    error,
    lastCreated,
    page,
    size,
    totalElements,
    totalPages,
    shorten,
    deleteUrl,
    changePage,
    clearLastCreated
  };

}
