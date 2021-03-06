/*
 * Copyright (c) 2008-2016 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.haulmont.cuba.security.app;

import com.haulmont.cuba.security.entity.PermissionType;
import com.haulmont.cuba.security.entity.User;
import com.haulmont.cuba.security.entity.UserSessionEntity;
import com.haulmont.cuba.security.global.UserSession;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.*;

/**
 * Service interface to active {@link UserSession}s management.
 *
 */
public interface UserSessionService {

    String NAME = "cuba_UserSessionService";

    /**
     * @param sessionId a session identifier
     * @return an active user session instance if exists
     * @throws com.haulmont.cuba.security.global.NoUserSessionException in case of a session with the specified ID
     * doesn't exist
     */
    UserSession getUserSession(UUID sessionId);

    /**
     * Set a session attribute value, propagating changes to the cluster.
     * @param sessionId an active session identifier
     * @param name      attribute name
     * @param value     attribute value
     * @throws com.haulmont.cuba.security.global.NoUserSessionException in case of a session with the specified ID
     * doesn't exist
     */
    void setSessionAttribute(UUID sessionId, String name, Serializable value);

    /**
     * Set user locale into the session, propagating changes to the cluster.
     * @param sessionId an active session identifier
     * @param locale    user locale
     * @throws com.haulmont.cuba.security.global.NoUserSessionException in case of a session with the specified ID
     * doesn't exist
     */
    void setSessionLocale(UUID sessionId, Locale locale);

    /**
     * Set user time zone into the session, propagating changes to the cluster.
     * @param sessionId an active session identifier
     * @param timeZone  user time zone
     * @throws com.haulmont.cuba.security.global.NoUserSessionException in case of a session with the specified ID
     * doesn't exist
     */
    void setSessionTimeZone(UUID sessionId, TimeZone timeZone);

    /**
     * Set client's address into the session, propagating changes to the cluster.
     * @param sessionId an active session identifier
     * @param address   client's address
     * @throws com.haulmont.cuba.security.global.NoUserSessionException in case of a session with the specified ID
     * doesn't exist
     */
    void setSessionAddress(UUID sessionId, String address);

    /**
     * Set client's information into the session, propagating changes to the cluster.
     * @param sessionId     an active session identifier
     * @param clientInfo    client's info
     * @throws com.haulmont.cuba.security.global.NoUserSessionException in case of a session with the specified ID
     * doesn't exist
     */
    void setSessionClientInfo(UUID sessionId, String clientInfo);

    /**
     * @return the list of active user sessions
     */
    Collection<UserSessionEntity> getUserSessionInfo();

    /**
     * Disconnect a session. Returns silently if there is no active session with the specified ID.
     * @param id    an active session identifier
     */
    void killSession(UUID id);

    /**
     * Post a message to the list of active user sessions. If a session is not found, it is ignored.
     * @param sessionIds    list of session identifiers
     * @param message       the message text
     */
    void postMessage(List<UUID> sessionIds, String message);

    /**
     * Poll for messages left for the current user session. Can also be used for session ping to prevent expiring on
     * user idle time.
     * @return  all messages sent to the current session in one string separated by carriage returns
     */
    @Nullable
    String getMessages();

    /**
     * Get effective user permission.
     * @param user              user
     * @param permissionType    type of permission
     * @param target            permission target
     * @return effective permission value
     */
    Integer getPermissionValue(User user, PermissionType permissionType, String target);
}
