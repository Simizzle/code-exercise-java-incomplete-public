import type { UrlListItem } from "../types/api";

interface UrlTableProps {
  urls: UrlListItem[];
  onDelete: (alias: string) => void;
  page: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (page: number) => void;
}

export function UrlTable({
  urls,
  onDelete,
  page,
  totalPages,
  totalElements,
  onPageChange,
}: UrlTableProps) {
  if (urls.length === 0) {
    return <p>No shortened URLs yet.</p>;
  }

  return (
    <div className="url-table-wrapper">
      <table className="url-table" aria-label="Shortened URL list">
        <thead>
          <tr>
            <th>Short URL</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {urls.map(({ alias, shortUrl, fullUrl }) => (
            <tr key={alias}>
              <td>
                <a
                  href={shortUrl}
                  className="short-url-link"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {shortUrl}
                </a>
                <span className="full-url" title={fullUrl}>
                  {fullUrl}
                </span>
              </td>
              <td className="actions-cell">
                <button
                  type="button"
                  className="btn-danger"
                  onClick={() => onDelete(alias)}
                  aria-label={`Delete ${alias}`}
                >
                  Delete
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="pagination">
        <button className="btn-primary" onClick={() => onPageChange(page - 1)} disabled={page === 0}>
          Previous
        </button>
        <span>
          Page {page + 1} of {totalPages}
        </span>
        <button
          className="btn-primary"
          onClick={() => onPageChange(page + 1)}
          disabled={page >= totalPages - 1}
        >
          Next
        </button>
      </div>
      <div className="pagination-info">
        Showing {totalElements === 0 ? 0 : page * 10 + 1}
        {" - "}
        {Math.min((page + 1) * 10, totalElements)}
        {" of "}
        {totalElements} URLs
      </div>
    </div>
  );
}
