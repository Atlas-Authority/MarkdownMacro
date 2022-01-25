package com.atlassian.plugins.confluence.markdown.ccma;

import com.atlassian.confluence.api.model.content.Content;
import com.atlassian.confluence.api.model.content.id.ContentId;
import com.atlassian.confluence.api.model.pagination.PageRequest;
import com.atlassian.confluence.api.model.pagination.PageResponse;
import com.atlassian.confluence.api.model.pagination.SimplePageRequest;
import com.atlassian.confluence.api.model.people.Subject;
import com.atlassian.confluence.api.model.people.SubjectType;
import com.atlassian.confluence.api.model.people.User;
import com.atlassian.confluence.api.model.permissions.ContentRestriction;
import com.atlassian.confluence.api.model.permissions.OperationKey;
import com.atlassian.confluence.api.service.permissions.ContentRestrictionService;
import com.atlassian.confluence.rest.api.model.ExpansionsParser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ConfluenceImport;
import com.atlassian.sal.api.user.UserKey;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;
import java.util.stream.Collectors;

@Named
class PageRestrictionService extends PermissionService<Content, String> {

    // REST API default value
    private static final int BATCH_SIZE = 100;

    private final ContentRestrictionService restrictionService;

    @Inject
    PageRestrictionService(@ConfluenceImport ContentRestrictionService restrictionService) {
        this.restrictionService = restrictionService;
    }

    @Override
    String key(Content page) {
        return page.getId().serialise();
    }

    @Override
    List<Content> convert(Collection<PageData> pageDataList) {
        return pageDataList
                .stream()
                .map(PageData::getPage)
                .collect(Collectors.toList());
    }

    @Override
    Set<String> getEditGroups(Content page) {
        final List<Subject> subjects = getRestrictions(
                page.getId(),
                SubjectType.GROUP,
                "restrictions.group"
        );
        return subjects.stream()
                .map(com.atlassian.confluence.api.model.people.Group.class::cast)
                .map(com.atlassian.confluence.api.model.people.Group::getName)
                .collect(Collectors.toSet());
    }

    @Override
    Set<UserKey> getEditUsers(Content page) {
        final List<Subject> subjects = getRestrictions(
                page.getId(),
                SubjectType.USER,
                "restrictions.user"
        );
        return subjects.stream()
                .map(User.class::cast)
                .map(User::optionalUserKey)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }

    private List<Subject> getRestrictions(ContentId contentId, SubjectType subjectType, String expansion) {
        final List<Subject> results = new ArrayList<>();

        Optional<PageResponse<Subject>> pageResponse = Optional.empty();
        do {
            final ContentRestriction contentRestriction = restrictionService.getRestrictionsForOperation(
                    contentId,
                    OperationKey.UPDATE,
                    nextPage(pageResponse),
                    ExpansionsParser.parse(expansion)
            );

            pageResponse = Optional.ofNullable(contentRestriction)
                    .map(ContentRestriction::getRestrictions)
                    .map(restrictions -> restrictions.get(subjectType));

            final List<Subject> subjects = pageResponse
                    .map(PageResponse::getResults)
                    .orElse(Collections.emptyList());

            results.addAll(subjects);
        } while (pageResponse.map(PageResponse::hasMore).orElse(false));

        return results;
    }

    private PageRequest nextPage(Optional<PageResponse<Subject>> pageResponse) {
        final int nextStart = pageResponse
                .map(PageResponse::getPageRequest)
                .map(page -> page.getStart() + page.getLimit())
                .orElse(0);
        return new SimplePageRequest(nextStart, BATCH_SIZE);
    }
}
