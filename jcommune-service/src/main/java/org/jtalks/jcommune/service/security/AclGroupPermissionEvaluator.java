/**
 * Copyright (C) 2011  JTalks.org Team
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.jtalks.jcommune.service.security;

import org.jtalks.common.model.permissions.GeneralPermission;
import org.jtalks.common.security.acl.AclUtil;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.acls.model.AccessControlEntry;
import org.springframework.security.acls.model.ObjectIdentity;
import org.springframework.security.acls.model.Permission;
import org.springframework.security.core.Authentication;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.List;

/**
 * This evaluator is used to process the annotations of the Spring Security like {@link
 * org.springframework.security.access.prepost.PreAuthorize}. In order to be able to use it, you need to specify the id
 * of object identity, the class of object identity and one of implementation of {@link
 * org.jtalks.common.model.permissions.JtalksPermission}. So it should look precisely like this:<br/> <code>
 * \@PreAuthorize("hasPermission(#topicId, 'org.jtalks.jcommune.model.entity.Topic',
 * 'GeneralPermission.WRITE')")</code>
 *
 * @author Elena Lepaeva
 * @author stanislav bashkirtsev
 */
public class AclGroupPermissionEvaluator implements PermissionEvaluator {
    private final org.jtalks.common.security.acl.AclManager aclManager;
    private final AclUtil aclUtil;

    public AclGroupPermissionEvaluator(@Nonnull org.jtalks.common.security.acl.AclManager aclManager,
                                       @Nonnull AclUtil aclUtil) {
        this.aclManager = aclManager;
        this.aclUtil = aclUtil;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        /*List<GroupAce> groupAces=
                aclManager.getGroupPermissionsOn(privateMessageDao.get((Long) targetId));
        for(GroupAce groupAce:groupAces){
            groupAce.
        }*/
        ObjectIdentity objectIdentity = aclUtil.createIdentity(targetId, targetType);
        Permission jtalksPermission = getPermission(permission);
        List<AccessControlEntry> aces = aclUtil.getAclFor(objectIdentity).getEntries();
        if (isRestricted(aces, jtalksPermission)) {
            return false;
        } else if (isAllowed(aces, jtalksPermission)) {
            return true;
        }
        return false;
    }

    private boolean isAllowed(List<AccessControlEntry> controlEntries, Permission permission) {
        for (AccessControlEntry ace : controlEntries) {
            if (permission.equals(ace.getPermission()) && ace.isGranting()) {
                return true;
            }
        }
        return false;
    }

    private boolean isRestricted(List<AccessControlEntry> controlEntries, Permission permission) {
        for (AccessControlEntry ace : controlEntries) {
            if (permission.equals(ace.getPermission()) && !ace.isGranting()) {
                return true;
            }
        }
        return false;
    }

    private Permission getPermission(Object permission) {
        String permissionName = (String) permission;
        if ((permissionName).startsWith(GeneralPermission.class.getSimpleName())) {
            String particularPermission = permissionName.replace(GeneralPermission.class.getSimpleName() + ".", "");
            return GeneralPermission.valueOf(particularPermission);
        } else {
            throw new IllegalArgumentException("No other permissions that GeneralPermission are supported now. " +
                    "Was specified: " + permission);
        }
    }
}