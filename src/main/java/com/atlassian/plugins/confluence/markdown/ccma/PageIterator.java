package com.atlassian.plugins.confluence.markdown.ccma;

import com.atlassian.confluence.api.model.pagination.PageRequest;
import com.atlassian.confluence.api.model.pagination.PageResponse;
import com.atlassian.confluence.api.model.pagination.SimplePageRequest;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;

/**
 * Turn Atlassian pagination request to Java iterator.
 * @param <T> the element type
 */
public class PageIterator<T> implements Iterator<PageResponse<T>> {
    private final Function<PageRequest, PageResponse<T>> fetcher;
    private final int pageSize;

    private Boolean hasMore;
    private PageResponse<T> currentPage;

    public PageIterator(Function<PageRequest, PageResponse<T>> fetcher, int pageSize) {
        this.fetcher = fetcher;
        this.pageSize = pageSize;
    }

    @Override
    public boolean hasNext() {
        synchronized (this) {
            hasMore = hasMore(currentPage);
            if (hasMore) {
                final PageRequest pageRequest = nextPage(currentPage);
                currentPage = fetcher.apply(pageRequest);
            }
            return hasMore;
        }
    }

    @Override
    public PageResponse<T> next() {
        synchronized (this) {
            if (hasMore == null) {
                throw new NoSuchElementException("No more element. hasNext() must be called first");
            } else if (!hasMore) {
                throw new NoSuchElementException("No more element");
            }
            // reset current iteration
            hasMore = null;
            return currentPage;
        }
    }

    private PageRequest nextPage(PageResponse<T> pageResponse) {
        final int nextStart = Optional.ofNullable(pageResponse)
                .map(PageResponse::getPageRequest)
                .map(page -> page.getStart() + page.getLimit())
                .orElse(0);
        return new SimplePageRequest(nextStart, pageSize);
    }

    private boolean hasMore(PageResponse<T> pageResponse) {
        return Optional.ofNullable(pageResponse).map(PageResponse::hasMore).orElse(true);
    }
}
