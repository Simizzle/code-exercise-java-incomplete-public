
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { UrlTable } from '../UrlTable';
import type { UrlListItem } from '../../types/api';

const mockUrls: UrlListItem[] = [
  {
    alias: 'abc',
    fullUrl: 'https://example.com',
    shortUrl: 'http://localhost:8080/abc'
  },
  {
    alias: 'def',
    fullUrl: 'https://another.com/path',
    shortUrl: 'http://localhost:8080/def'
  },
];

const defaultProps = {
  urls: mockUrls,
  onDelete: vi.fn(),
  page: 0,
  totalPages: 3,
  totalElements: 25,
  onPageChange: vi.fn()
};

describe('UrlTable', () => {
  it('shows empty state when there are no URLs', () => {
    render(
      <UrlTable
        {...defaultProps}
        urls={[]}
        totalElements={0}
        totalPages={0}
      />
    );

    expect(
      screen.getByText(/no shortened urls yet/i)
    ).toBeInTheDocument();
  });

  it('renders a row for each URL', () => {
    render(<UrlTable {...defaultProps} />);

    expect(
      screen.getByText('http://localhost:8080/abc')
    ).toBeInTheDocument();

    expect(
      screen.getByText('http://localhost:8080/def')
    ).toBeInTheDocument();
  });

  it('displays the original URLs underneath the shortened URLs', () => {
    render(<UrlTable {...defaultProps} />);

    expect(
      screen.getByText('https://example.com')
    ).toBeInTheDocument();

    expect(
      screen.getByText('https://another.com/path')
    ).toBeInTheDocument();
  });

  it('calls onDelete with the alias when delete is clicked', () => {
    const onDelete = vi.fn();

    render(
      <UrlTable {...defaultProps} onDelete={onDelete} />
    );

    fireEvent.click(
      screen.getAllByRole('button', { name: /delete/i })[0]
    );

    expect(onDelete).toHaveBeenCalledWith('abc');
  });

  it('displays the current page and total pages', () => {
    render(<UrlTable {...defaultProps} />);

    expect(
      screen.getByText('Page 1 of 3')
    ).toBeInTheDocument();
  });

  it('calls onPageChange with the next page when Next is clicked', () => {
    const onPageChange = vi.fn();

    render(
      <UrlTable
        {...defaultProps}
        onPageChange={onPageChange}
      />
    );

    fireEvent.click(
      screen.getByRole('button', { name: /next/i })
    );

    expect(onPageChange).toHaveBeenCalledWith(1);
  });

  it('calls onPageChange with the previous page when Previous is clicked', () => {
    const onPageChange = vi.fn();

    render(
      <UrlTable
        {...defaultProps}
        page={1}
        onPageChange={onPageChange}
      />
    );

    fireEvent.click(
      screen.getByRole('button', { name: /previous/i })
    );

    expect(onPageChange).toHaveBeenCalledWith(0);
  });

  it('disables Previous on the first page', () => {
    render(<UrlTable {...defaultProps} page={0} />);

    expect(
      screen.getByRole('button', { name: /previous/i })
    ).toBeDisabled();
  });

  it('disables Next on the last page', () => {
    render(
      <UrlTable
        {...defaultProps}
        page={2}
        totalPages={3}
      />
    );

    expect(
      screen.getByRole('button', { name: /next/i })
    ).toBeDisabled();
  });

  it('displays the correct range of URLs', () => {
    render(<UrlTable {...defaultProps} />);

    expect(
      screen.getByText('Showing 1 - 10 of 25 URLs')
    ).toBeInTheDocument();
  });

  it('displays the correct range on the last page', () => {
    render(
      <UrlTable
        {...defaultProps}
        page={2}
        totalPages={3}
      />
    );

    expect(
      screen.getByText('Showing 21 - 25 of 25 URLs')
    ).toBeInTheDocument();
  });
});

