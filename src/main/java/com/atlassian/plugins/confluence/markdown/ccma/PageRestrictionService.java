package com.atlassian.plugins.confluence.markdown.ccma;

import com.atlassian.confluence.api.model.content.Content;
import com.atlassian.confluence.api.model.pagination.PageResponse;
import com.atlassian.confluence.api.model.people.Subject;
import com.atlassian.confluence.api.model.people.SubjectType;
import com.atlassian.confluence.api.model.people.User;
import com.atlassian.confluence.api.model.permissions.ContentRestriction;
import com.atlassian.confluence.api.model.permissions.OperationKey;
import com.atlassian.sal.api.user.UserKey;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;
import java.util.stream.Collectors;

@Named
class PageRestrictionService extends PermissionService<Content, String> {

    @Inject
    PageRestrictionService() {
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
        final List<Subject> groupSubject = Optional
                .ofNullable(page.getRestrictions())
                .map(restrictions -> restrictions.get(OperationKey.UPDATE))
                .map(ContentRestriction::getRestrictions)
                .map(restrictions -> restrictions.get(SubjectType.GROUP))
                .map(PageResponse::getResults)
                .orElse(Collections.emptyList());

        return groupSubject.stream()
                .map(com.atlassian.confluence.api.model.people.Group.class::cast)
                .map(com.atlassian.confluence.api.model.people.Group::getName)
                .collect(Collectors.toSet());
    }

    @Override
    Set<UserKey> getEditUsers(Content page) {
        final List<Subject> userSubjects = Optional.ofNullable(page.getRestrictions())
                .map(restrictions -> restrictions.get(OperationKey.UPDATE))
                .map(ContentRestriction::getRestrictions)
                .map(restrictions -> restrictions.get(SubjectType.USER))
                .map(PageResponse::getResults)
                .orElse(Collections.emptyList());
        return userSubjects.stream()
                .map(User.class::cast)
                .map(User::optionalUserKey)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }
}
