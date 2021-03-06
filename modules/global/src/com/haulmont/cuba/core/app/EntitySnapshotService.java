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

package com.haulmont.cuba.core.app;

import com.haulmont.chile.core.model.MetaClass;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.entity.EntitySnapshot;
import com.haulmont.cuba.core.entity.diff.EntityDiff;
import com.haulmont.cuba.core.global.View;
import com.haulmont.cuba.security.entity.User;

import javax.annotation.Nullable;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Provides entity snapshots functionality.
 */
public interface EntitySnapshotService {

    String NAME = "cuba_EntitySnapshotService";

    /**
     * Get snapshots for entity by id
     * @param entity Entity
     * @return Snapshot list
     */
    List<EntitySnapshot> getSnapshots(Entity entity);

    /**
     * Create snapshot for entity and save it to database
     * @param entity Entity
     * @param view View
     * @return Snapshot
     */
    EntitySnapshot createSnapshot(Entity entity, View view);

    /**
     * Create snapshot for Entity with specific date and store it to database
     *
     * @param entity       Entity
     * @param view         View
     * @param snapshotDate Date
     * @return Snapshot
     */
    EntitySnapshot createSnapshot(Entity entity, View view, Date snapshotDate);

    /**
     * Create snapshot for Entity with specific date and author and store it to database
     *
     * @param entity       Entity
     * @param view         View
     * @param snapshotDate Date
     * @param author       Author
     * @return Snapshot
     */
    EntitySnapshot createSnapshot(Entity entity, View view, Date snapshotDate, User author);

    /**
     * Get entity from snapshot
     * @param snapshot Snapshot
     * @return Entity
     */
    Entity extractEntity(EntitySnapshot snapshot);

    /**
     * Get Diff for snapshots
     * @param first First snapshot
     * @param second Second snapshot
     * @return Diff
     */
    EntityDiff getDifference(@Nullable EntitySnapshot first, EntitySnapshot second);

    /**
     * Translate snapshots for archival classes
     *
     * @param entity    Entity
     * @param classMapping Map of [OldClass -&gt; NewClass] for migration
     */
    void migrateSnapshots(Entity entity, Map<Class, Class> classMapping);
}