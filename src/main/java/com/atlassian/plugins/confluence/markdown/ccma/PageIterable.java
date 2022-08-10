package com.atlassian.plugins.confluence.markdown.ccma;

import com.atlassian.confluence.api.model.content.Content;
import com.atlassian.confluence.api.model.content.Space;
import com.atlassian.confluence.api.model.pagination.PageResponse;
import com.atlassian.confluence.api.service.content.SpaceService;
import com.atlassian.confluence.api.service.search.CQLSearchService;

public interface PageIterable<T> extends Iterable<PageResponse<T>> {

    static PageIterable<Content> cqlSearch(CQLSearchService cqlSearchService, String cqlInput, int pageSize) {
        return () -> new PageIterator<>(pageRequest -> cqlSearchService.searchContent(cqlInput, pageRequest), pageSize);
    }

    static PageIterable<Space> spaces(SpaceService spaceService, int pageSize) {
        return () -> new PageIterator<>(spaceService.find()::fetchMany, pageSize);
    }
}
