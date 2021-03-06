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

package com.haulmont.cuba.desktop.app.core.entityinspector;

import com.haulmont.cuba.desktop.gui.components.DesktopComponentsHelper;
import com.haulmont.cuba.gui.app.core.entityinspector.EntityInspectorBrowse;
import com.haulmont.cuba.gui.components.Table;
import org.jdesktop.swingx.JXTable;

public class EntityInspectorBrowseCompanion implements EntityInspectorBrowse.Companion {
    @Override
    public void setHorizontalScrollEnabled(Table table, boolean enabled) {
        JXTable jxTable = (JXTable) DesktopComponentsHelper.unwrap(table);
        jxTable.setHorizontalScrollEnabled(enabled);
    }

    @Override
    public void setTextSelectionEnabled(Table table, boolean enabled) {
        // For desktop coping text from cell works by hotkey
    }
}