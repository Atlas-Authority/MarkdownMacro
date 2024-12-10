package com.atlassian.plugins.confluence.markdown.ccma;

import com.atlassian.confluence.security.SpacePermission;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ConfluenceImport;
import com.atlassian.sal.api.user.UserKey;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;
import java.util.stream.Collectors;

@Named
class SpacePermissionService extends PermissionService<Space, Long> {

    private final SpaceManager spaceManager;

    @Inject
    SpacePermissionService(@ConfluenceImport SpaceManager spaceManager) {
        this.spaceManager = spaceManager;
    }

    @Override
    Long key(Space space) {
        return space.getId();
    }

    @Override
    List<Space> convert(Collection<PageData> pageDataList) {
        return pageDataList
                .stream()
                .map(PageData::getSpaceServerId)
                .map(Long::parseLong)
                .collect(Collectors.toSet())
                .stream()
                .map(spaceManager::getSpace)
                .collect(Collectors.toList());
    }

    @Override
    Set<String> getEditGroups(Space space) {
        return space.getPermissions()
                .stream()
                .filter(SpacePermission::isGroupPermission)
                .filter(this::isEditPermission)
                .map(SpacePermission::getGroup)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @Override
    Set<UserKey> getEditUsers(Space space) {
        final Set<UserKey> userKeys = space.getPermissions().stream()
                .filter(SpacePermission::isUserPermission)
                .filter(this::isEditPermission)
                .map(SpacePermission::getUserSubject)
                .filter(Objects::nonNull)
                .map(ConfluenceUser::getKey)
                .collect(Collectors.toSet());
        return Collections.unmodifiableSet(userKeys);
    }

    private boolean isEditPermission(SpacePermission permission) {
        return SpacePermission.CREATEEDIT_PAGE_PERMISSION.equalsIgnoreCase(permission.getType());
    }
}
