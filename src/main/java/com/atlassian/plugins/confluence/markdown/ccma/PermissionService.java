package com.atlassian.plugins.confluence.markdown.ccma;

import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.user.Group;
import org.apache.commons.collections4.SetUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

abstract class PermissionService<E, K> {

    private final UserAccessor userAccessor;

    PermissionService(UserAccessor userAccessor) {
        this.userAccessor = userAccessor;
    }

    final Map<K, Set<UserKey>> getPermissions(Collection<PageData> pageDataList) {
        final List<E> entities = convert(pageDataList);

        final Set<String> groupNames = getEditGroups(entities);

        final Map<String, Set<UserKey>> editUsersByGroupName = getEditUserByGroup(groupNames);

        return entities.stream().collect(Collectors.toMap(
                this::key,
                entity -> {
                    final Set<UserKey> editUsers = getEditUsers(entity);
                    final Set<String> groups = getEditGroups(entity);
                    final Set<UserKey> editUsersFromGroups = groups.stream()
                            .filter(editUsersByGroupName::containsKey)
                            .map(editUsersByGroupName::get)
                            .flatMap(Set::stream)
                            .collect(Collectors.toSet());
                    return SetUtils.union(editUsers, editUsersFromGroups);
                }
        ));
    }

    /**
     * Mapping from a group to its users who have edit perm.
     */
    private Map<String, Set<UserKey>> getEditUserByGroup(Set<String> groupNames) {
        return groupNames.stream().collect(Collectors.toMap(
                Function.identity(),
                groupName -> {
                    final Group group = userAccessor.getGroup(groupName);
                    final Spliterator<ConfluenceUser> spliterator = Spliterators.spliteratorUnknownSize(
                            userAccessor.getMembers(group).iterator(),
                            Spliterator.ORDERED
                    );
                    return StreamSupport.stream(spliterator, false)
                            .map(ConfluenceUser::getKey)
                            .collect(Collectors.toSet());
                }
        ));
    }

    private Set<String> getEditGroups(List<E> entities) {
        return entities.stream()
                .map(this::getEditGroups)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    abstract K key(E entity);

    abstract List<E> convert(Collection<PageData> pageDataList);

    abstract Set<String> getEditGroups(E entity);

    abstract Set<UserKey> getEditUsers(E entity);
}
