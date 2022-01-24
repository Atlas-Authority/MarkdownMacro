package com.atlassian.plugins.confluence.markdown.ccma;

import com.atlassian.sal.api.user.UserKey;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Get all users who has edit permission on wiki objects.
 * @param <E> The confluence entity (space or page)
 * @param <K> The confluence entity key type
 */
abstract class PermissionService<E, K> {

    PermissionService() {
    }

    /**
     * For each page, get all users who:
     * <li>Belong to groups which has edit permission</li>
     * <li>Are granted edit permission as individual users</li>
     */
    final Map<K, PermData> getPermissions(Collection<PageData> pageDataList) {
        final List<E> entities = convert(pageDataList);
        return entities.stream().collect(Collectors.toMap(
                this::key,
                entity -> {
                    final Set<UserKey> editUsers = getEditUsers(entity);
                    final Set<String> editGroups = getEditGroups(entity);
                    return new PermData(editUsers, editGroups);
                }
        ));
    }

    /**
     * How to retrieve entity key?
     */
    abstract K key(E entity);

    /**
     * Get the list of entities where the permission will be extracted from.
     */
    abstract List<E> convert(Collection<PageData> pageDataList);

    /**
     * Get all groups which have edit permission on an entity.
     */
    abstract Set<String> getEditGroups(E entity);

    /**
     * Get all individual users which have edit permission on an entity.
     */
    abstract Set<UserKey> getEditUsers(E entity);
}
