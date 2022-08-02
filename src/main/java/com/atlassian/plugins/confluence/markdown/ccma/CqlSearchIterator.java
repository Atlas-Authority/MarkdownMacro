package com.atlassian.plugins.confluence.markdown.ccma;

import com.atlassian.confluence.api.model.content.Content;
import com.atlassian.confluence.api.model.pagination.PageRequest;
import com.atlassian.confluence.api.model.pagination.PageResponse;
import com.atlassian.confluence.api.model.pagination.SimplePageRequest;
import com.atlassian.confluence.api.service.search.CQLSearchService;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

public class CqlSearchIterator implements Iterator<List<Content>> {
    private final CQLSearchService cqlSearchService;
    private final String cqlInput;
    private final int pageSize;

    private Boolean hasMore;
    private PageResponse<Content> currentPage;

    public CqlSearchIterator(CQLSearchService cqlSearchService, String cqlInput, int pageSize) {
        this.cqlSearchService = cqlSearchService;
        this.cqlInput = cqlInput;
        this.pageSize = pageSize;
        hasMore = null;
        this.currentPage = null;
    }

    @Override
    public boolean hasNext() {
        synchronized (this) {
            hasMore = hasMore(currentPage);
            if (hasMore) {
                final PageRequest pageRequest = nextPage(currentPage);
                // https://confluence.atlassian.com/confkb/searching-for-content-with-the-rest-api-and-cql-always-limits-results-to-50-1032258424.html
                currentPage = cqlSearchService.searchContent(cqlInput, pageRequest);
            }
            return hasMore;
        }
    }

    @Override
    public List<Content> next() {
        synchronized (this) {
            if (hasMore == null) {
                throw new NoSuchElementException("No more element. hasNext() must be called first");
            } else if (!hasMore) {
                throw new NoSuchElementException("No more element");
            }
            // reset current iteration
            hasMore = null;
            return currentPage.getResults();
        }
    }

    private PageRequest nextPage(PageResponse<Content> pageResponse) {
        final int nextStart = Optional.ofNullable(pageResponse)
                .map(PageResponse::getPageRequest)
                .map(page -> page.getStart() + page.getLimit())
                .orElse(0);
        return new SimplePageRequest(nextStart, pageSize);
    }

    private boolean hasMore(PageResponse<Content> pageResponse) {
        return Optional.ofNullable(pageResponse).map(PageResponse::hasMore).orElse(true);
    }
}
