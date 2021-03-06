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
 */

package com.haulmont.cuba.web.gui.components;

import com.haulmont.bali.util.Dom4j;
import com.haulmont.bali.util.Preconditions;
import com.haulmont.chile.core.model.MetaClass;
import com.haulmont.chile.core.model.MetaProperty;
import com.haulmont.chile.core.model.MetaPropertyPath;
import com.haulmont.cuba.client.ClientConfig;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.*;
import com.haulmont.cuba.gui.ComponentsHelper;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.cuba.gui.components.Formatter;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.data.Datasource;
import com.haulmont.cuba.gui.data.PropertyDatasource;
import com.haulmont.cuba.gui.data.impl.CollectionDsActionsNotifier;
import com.haulmont.cuba.gui.data.impl.WeakCollectionChangeListener;
import com.haulmont.cuba.gui.theme.ThemeConstants;
import com.haulmont.cuba.web.App;
import com.haulmont.cuba.web.AppUI;
import com.haulmont.cuba.web.gui.components.renderers.*;
import com.haulmont.cuba.web.gui.data.IndexedCollectionDsWrapper;
import com.haulmont.cuba.web.gui.data.SortableIndexedCollectionDsWrapper;
import com.haulmont.cuba.web.toolkit.data.DataGridContainer;
import com.haulmont.cuba.web.toolkit.ui.CubaGrid;
import com.haulmont.cuba.web.toolkit.ui.CubaGridContextMenu;
import com.haulmont.cuba.web.toolkit.ui.CubaMultiSelectionModel;
import com.haulmont.cuba.web.toolkit.ui.CubaSingleSelectionModel;
import com.haulmont.cuba.web.toolkit.ui.converters.FormatterBasedConverter;
import com.haulmont.cuba.web.toolkit.ui.converters.StringToObjectConverter;
import com.haulmont.cuba.web.toolkit.ui.converters.YesNoIconConverter;
import com.vaadin.addon.contextmenu.GridContextMenu.GridContextMenuOpenListener;
import com.vaadin.addon.contextmenu.Menu;
import com.vaadin.addon.contextmenu.MenuItem;
import com.vaadin.data.Item;
import com.vaadin.data.util.GeneratedPropertyContainer;
import com.vaadin.data.util.PropertyValueGenerator;
import com.vaadin.event.ShortcutAction.KeyCode;
import com.vaadin.event.ShortcutListener;
import com.vaadin.shared.ui.grid.HeightMode;
import com.vaadin.ui.Component;
import com.vaadin.ui.CssLayout;
import com.vaadin.ui.Grid;
import com.vaadin.ui.HorizontalLayout;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.dom4j.Element;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.stream.Collectors;

import static com.haulmont.bali.util.Preconditions.checkNotNullArgument;
import static com.haulmont.cuba.gui.ComponentsHelper.findActionById;

public class WebDataGrid<E extends Entity> extends WebAbstractComponent<CubaGrid>
        implements DataGrid<E>, com.haulmont.cuba.gui.components.Component.SecuredActionsHolder {

    protected static final String HAS_TOP_PANEL_STYLE_NAME = "has-top-panel";
    protected static final String TEXT_SELECTION_ENABLED_STYLE = "text-selection-enabled";

    // Style names used by grid itself
    protected final List<String> internalStyles = new ArrayList<>();

    protected CollectionDatasource datasource;
    protected GeneratedPropertyContainer containerWrapper;

    protected final Map<String, Column> columns = new HashMap<>();
    protected List<Column> columnsOrder = new ArrayList<>();
    protected final Map<String, ColumnGenerator<E, ?>> columnGenerators = new HashMap<>();

    protected final List<Action> actionList = new ArrayList<>();
    protected final ShortcutsDelegate<ShortcutListener> shortcutsDelegate;
    protected final ActionsPermissions actionsPermissions = new ActionsPermissions(this);

    protected CubaGridContextMenu contextMenu;
    protected final List<ActionMenuItemWrapper> contextMenuItems = new ArrayList<>();

    protected boolean settingsEnabled = true;
    protected boolean sortable = true;
    protected boolean columnsCollapsingAllowed = true;
    protected boolean textSelectionEnabled = false;

    protected Action itemClickAction;
    protected Action enterPressAction;

    protected SelectionMode selectionMode;

    protected GridComposition componentComposition;
    protected HorizontalLayout topPanel;
    protected ButtonsPanel buttonsPanel;
    protected RowsCount rowsCount;

    protected List<RowStyleProvider> rowStyleProviders;
    protected List<CellStyleProvider> cellStyleProviders;

    protected RowDescriptionProvider<E> rowDescriptionProvider;
    protected CellDescriptionProvider<E> cellDescriptionProvider;

    protected Security security = AppBeans.get(Security.NAME);
    protected MetadataTools metadataTools = AppBeans.get(MetadataTools.NAME);

    protected CollectionDatasource.CollectionChangeListener collectionChangeSelectionListener;
    protected CollectionDsActionsNotifier collectionDsActionsNotifier;

    protected Grid.ColumnVisibilityChangeListener columnCollapsingChangeListener;
    protected Grid.ColumnResizeListener columnResizeListener;
    protected com.vaadin.event.SelectionEvent.SelectionListener selectionListener;
    protected com.vaadin.event.SortEvent.SortListener sortListener;
    protected com.vaadin.event.ContextClickEvent.ContextClickListener contextClickListener;

    protected final List<HeaderRow> headerRows = new ArrayList<>();
    protected final List<FooterRow> footerRows = new ArrayList<>();

    protected boolean showIconsForPopupMenuActions;

    public WebDataGrid() {
        Configuration configuration = AppBeans.get(Configuration.NAME);
        ClientConfig clientConfig = configuration.getConfig(ClientConfig.class);
        showIconsForPopupMenuActions = clientConfig.getShowIconsForPopupMenuActions();

        shortcutsDelegate = new ShortcutsDelegate<ShortcutListener>() {
            @Override
            protected ShortcutListener attachShortcut(String actionId, KeyCombination keyCombination) {
                ShortcutListener shortcut = new ShortcutListener(actionId, keyCombination.getKey().getCode(),
                        KeyCombination.Modifier.codes(keyCombination.getModifiers())) {

                    @Override
                    public void handleAction(Object sender, Object target) {
                        if (target == component) {
                            Action action = getAction(actionId);
                            if (action != null && action.isEnabled() && action.isVisible()) {
                                action.actionPerform(WebDataGrid.this);
                            }
                        }
                    }
                };
                component.addShortcutListener(shortcut);
                return shortcut;
            }

            @Override
            protected void detachShortcut(Action action, ShortcutListener shortcutDescriptor) {
                component.removeShortcutListener(shortcutDescriptor);
            }

            @Override
            protected Collection<Action> getActions() {
                return WebDataGrid.this.getActions();
            }
        };

        component = new CubaGrid();
        initComponent(component);

        initContextMenu();

        initHeaderRows(component);
        initFooterRows(component);
    }

    protected void initComponent(Grid component) {
        setSelectionMode(SelectionMode.SINGLE);
        component.setImmediate(true);

        component.setColumnReorderingAllowed(true);

        containerWrapper = new GeneratedPropertyContainer(component.getContainerDataSource());
        component.setContainerDataSource(containerWrapper);

        component.addSelectionListener((com.vaadin.event.SelectionEvent.SelectionListener) event -> {
            if (datasource == null) {
                return;
            }

            final Set<? extends Entity> selected = getSelected();
            if (selected.isEmpty()) {
                Entity dsItem = datasource.getItemIfValid();
                //noinspection unchecked
                datasource.setItem(null);
                if (dsItem == null) {
                    // in this case item change event will not be generated
                    refreshActionsState();
                }
            } else {
                // reset selection and select new item
                if (isMultiSelect()) {
                    //noinspection unchecked
                    datasource.setItem(null);
                }

                Entity newItem = selected.iterator().next();
                Entity dsItem = datasource.getItemIfValid();
                //noinspection unchecked
                datasource.setItem(newItem);

                if (ObjectUtils.equals(dsItem, newItem)) {
                    // in this case item change event will not be generated
                    refreshActionsState();
                }
            }
        });

        component.addShortcutListener(new ShortcutListener("datadGridEnter", KeyCode.ENTER, null) {
            @Override
            public void handleAction(Object sender, Object target) {
                if (target == WebDataGrid.this.component) {
                    if (enterPressAction != null) {
                        enterPressAction.actionPerform(WebDataGrid.this);
                    } else {
                        handleDoubleClickAction();
                    }
                }
            }
        });

        component.addItemClickListener(e -> {
            if (e.isDoubleClick() && e.getItem() != null) {
                handleDoubleClickAction();
            }

            if (getEventRouter().hasListeners(ItemClickListener.class)) {
                MouseEventDetails mouseEventDetails = WebWrapperUtils.toMouseEventDetails(e);

                //noinspection unchecked
                E item = (E) datasource.getItem(e.getItemId());
                Column column = getColumnByPropertyId(e.getPropertyId());

                ItemClickEvent<E> event = new ItemClickEvent<>(WebDataGrid.this,
                        mouseEventDetails, item, e.getItemId(), column != null ? column.getId() : null);
                //noinspection unchecked
                getEventRouter().fireEvent(ItemClickListener.class, ItemClickListener::onItemClick, event);
            }
        });
        component.addColumnReorderListener(e -> {
            if (e.isUserOriginated()) {
                // Grid doesn't know about columns hidden by security permissions,
                // so we need to return them back to they previous positions
                columnsOrder = restoreColumnsOrder(getColumnsOrderInternal());

                if (getEventRouter().hasListeners(ColumnReorderListener.class)) {
                    ColumnReorderEvent event = new ColumnReorderEvent(WebDataGrid.this);
                    getEventRouter().fireEvent(ColumnReorderListener.class,
                            ColumnReorderListener::columnReordered, event);
                }
            }
        });

        componentComposition = new GridComposition();
        componentComposition.setPrimaryStyleName("c-data-grid-composition");
        componentComposition.setGrid(component);
        componentComposition.addComponent(component);
        componentComposition.setWidthUndefined();

        component.setSizeUndefined();
        component.setHeightMode(HeightMode.UNDEFINED);

        component.setRowStyleGenerator(createRowStyleGenerator());
        component.setCellStyleGenerator(createCellStyleGenerator());
    }

    protected void initHeaderRows(Grid component) {
        for (int i = 0; i < component.getHeaderRowCount(); i++) {
            Grid.HeaderRow gridRow = component.getHeaderRow(i);
            addHeaderRowInternal(gridRow);
        }
    }

    protected void initFooterRows(Grid component) {
        for (int i = 0; i < component.getFooterRowCount(); i++) {
            Grid.FooterRow gridRow = component.getFooterRow(i);
            addFooterRowInternal(gridRow);
        }
    }

    protected List<Column> getColumnsOrderInternal() {
        List<Grid.Column> columnsOrder = component.getColumns();
        return columnsOrder.stream()
                .map(this::getColumnByGridColumn)
                .collect(Collectors.toList());
    }

    /**
     * Inserts columns hidden by security permissions (or with visible = false,
     * which means that where is no Grid.Column associated with DatGrid.Column)
     * into a list of visible columns, passed as a parameter, in they original positions.
     *
     * @param visibleColumns the list of DataGrid columns,
     *                       not hidden by security permissions
     * @return a list of all columns in DataGrid
     */
    protected List<Column> restoreColumnsOrder(List<Column> visibleColumns) {
        List<Column> newColumnsOrder = new ArrayList<>(visibleColumns);
        columnsOrder.stream()
                .filter(column -> !visibleColumns.contains(column))
                .forEach(column -> newColumnsOrder.add(columnsOrder.indexOf(column), column));
        return newColumnsOrder;
    }

    protected void initContextMenu() {
        contextMenu = new CubaGridContextMenu(component);

        contextMenu.addGridBodyContextMenuListener((GridContextMenuOpenListener) event -> {
            if (!component.getSelectedRows().contains(event.getItemId())) {
                // In multi select model setSelected adds item to selected set,
                // but, in case of context click, we want to have single selected item,
                // if it isn't in a set of already selected items
                if (isMultiSelect()) {
                    component.deselectAll();
                }
                //noinspection unchecked
                setSelected((E) datasource.getItem(event.getItemId()));
            }
        });
    }

    protected void refreshActionsState() {
        getActions().forEach(Action::refreshState);
    }

    protected void handleDoubleClickAction() {
        Action action = getItemClickAction();
        if (action == null) {
            action = getEnterAction();
            if (action == null) {
                action = getAction("edit");
                if (action == null) {
                    action = getAction("view");
                }
            }
        }

        if (action != null && action.isEnabled()) {
            Window window = ComponentsHelper.getWindowImplementation(WebDataGrid.this);
            if (window instanceof Window.Wrapper) {
                window = ((Window.Wrapper) window).getWrappedWindow();
            }

            if (!(window instanceof Window.Lookup)) {
                action.actionPerform(WebDataGrid.this);
            } else {
                Window.Lookup lookup = (Window.Lookup) window;

                com.haulmont.cuba.gui.components.Component lookupComponent = lookup.getLookupComponent();
                if (lookupComponent != this)
                    action.actionPerform(WebDataGrid.this);
                else if (action.getId().equals(WindowDelegate.LOOKUP_ITEM_CLICK_ACTION_ID)) {
                    action.actionPerform(WebDataGrid.this);
                }
            }
        }
    }

    @Override
    public void setLookupSelectHandler(Runnable selectHandler) {
        setEnterPressAction(new AbstractAction(WindowDelegate.LOOKUP_ENTER_PRESSED_ACTION_ID) {
            @Override
            public void actionPerform(com.haulmont.cuba.gui.components.Component component) {
                selectHandler.run();
            }
        });

        setItemClickAction(new AbstractAction(WindowDelegate.LOOKUP_ITEM_CLICK_ACTION_ID) {
            @Override
            public void actionPerform(com.haulmont.cuba.gui.components.Component component) {
                selectHandler.run();
            }
        });
    }

    @Override
    public Collection getLookupSelectedItems() {
        return getSelected();
    }

    protected Action getEnterAction() {
        for (Action action : getActions()) {
            KeyCombination kc = action.getShortcutCombination();
            if (kc != null) {
                if ((kc.getModifiers() == null || kc.getModifiers().length == 0)
                        && kc.getKey() == KeyCombination.Key.ENTER) {
                    return action;
                }
            }
        }
        return null;
    }

    protected RowStyleGeneratorAdapter createRowStyleGenerator() {
        return new RowStyleGeneratorAdapter();
    }

    protected CellStyleGeneratorAdapter createCellStyleGenerator() {
        return new CellStyleGeneratorAdapter();
    }

    @Override
    public List<Column> getColumns() {
        return Collections.unmodifiableList(columnsOrder);
    }

    @Override
    public List<Column> getVisibleColumns() {
        return columnsOrder.stream()
                .filter(Column::isVisible)
                .collect(Collectors.toList());
    }

    @Nullable
    @Override
    public Column getColumn(String id) {
        return columns.get(id);
    }

    @Override
    public Column getColumnNN(String id) {
        Column column = getColumn(id);
        if (column == null) {
            throw new IllegalStateException("Unable to find column with id " + id);
        }
        return column;
    }

    @Override
    public void addColumn(Column column) {
        addColumn(column, columnsOrder.size());
    }

    @Override
    public void addColumn(Column column, int index) {
        checkNotNullArgument(column, "Column must be non null");
        if (column.getOwner() != null && column.getOwner() != this) {
            throw new IllegalArgumentException("Can't add column owned by another DataGrid");
        }
        addColumnInternal((ColumnImpl) column, index);
    }

    @Override
    public Column addColumn(String id, MetaPropertyPath propertyPath) {
        return addColumn(id, propertyPath, columnsOrder.size());
    }

    @Override
    public Column addColumn(String id, MetaPropertyPath propertyPath, int index) {
        ColumnImpl column = new ColumnImpl(id, propertyPath, this);
        addColumnInternal(column, index);
        return column;
    }

    protected void addColumnInternal(ColumnImpl column, int index) {
        addContainerProperty(column);

        columns.put(column.getId(), column);
        columnsOrder.add(index, column);

        final String caption = StringUtils.capitalize(column.getCaption() != null
                ? column.getCaption()
                : generateColumnCaption(column));
        column.setCaption(caption);

        if (column.getOwner() == null) {
            column.setOwner(this);
        }

        Grid.Column gridColumn = component.getColumn(column.getColumnPropertyId());
        if (gridColumn != null) {
            setupGridColumnProperties(gridColumn, column);
        }

        component.setColumnOrder(getColumnPropertyIds());
    }

    protected void addContainerProperty(ColumnImpl column) {
        if (column.getPropertyPath() != null) {
            getContainerDataSource().addContainerProperty(column.getPropertyPath(), column.getType(), null);
        } else {
            containerWrapper.addGeneratedProperty(column.getId(), createDefaultPropertyValueGenerator());
        }
    }

    protected PropertyValueGenerator createDefaultPropertyValueGenerator() {
        return new PropertyValueGenerator() {
            @Override
            public Object getValue(Item item, Object itemId, Object propertyId) {
                return null;
            }

            @Override
            public Class getType() {
                return String.class;
            }
        };
    }

    protected com.vaadin.data.Container.Indexed getContainerDataSource() {
        com.vaadin.data.Container.Indexed containerDataSource = component.getContainerDataSource();
        return (containerDataSource instanceof GeneratedPropertyContainer)
                ? ((GeneratedPropertyContainer) containerDataSource).getWrappedContainer()
                : containerDataSource;
    }

    protected String generateColumnCaption(Column column) {
        return column.getPropertyPath() != null
                ? column.getPropertyPath().getMetaProperty().getName()
                : column.getId();
    }

    protected void setupGridColumnProperties(Grid.Column gridColumn, Column column) {
        gridColumn.setHeaderCaption(column.getCaption());
        gridColumn.setHidingToggleCaption(column.getCollapsingToggleCaption());
        if (column.isWidthAuto()) {
            gridColumn.setWidthUndefined();
        } else {
            gridColumn.setWidth(column.getWidth());
        }
        gridColumn.setExpandRatio(column.getExpandRatio());
        gridColumn.setMinimumWidth(column.getMinimumWidth());
        gridColumn.setMaximumWidth(column.getMaximumWidth());
        gridColumn.setHidden(column.isCollapsed());
        gridColumn.setHidable(column.isCollapsible() && column.getOwner().isColumnsCollapsingAllowed());
        gridColumn.setResizable(column.isResizable());

        // FIXME: gg, workaround to prevent exception from GridColumn while Grid is using default IndexedContainer
        if (getContainerDataSource() instanceof SortableIndexedCollectionDsWrapper) {
            gridColumn.setSortable(column.isSortable() && column.getOwner().isSortable());
        }

        ((ColumnImpl) column).setGridColumn(gridColumn);

        if (column.getFormatter() != null) {
            FormatterBasedConverter converter = new FormatterBasedConverter(column.getFormatter());
            gridColumn.setConverter(converter);
        } else {
            MetaProperty metaProperty = column.getPropertyPath() != null
                    ? column.getPropertyPath().getMetaProperty()
                    : null;

            setDefaultConverter(gridColumn, metaProperty, column.getType());
            setDefaultRenderer(gridColumn, metaProperty, column.getType());
        }
    }

    protected void setDefaultRenderer(Grid.Column gridColumn, @Nullable MetaProperty metaProperty, Class type) {
        gridColumn.setRenderer(type == Boolean.class && metaProperty != null
                ? new com.vaadin.ui.renderers.HtmlRenderer()
                : new com.vaadin.ui.renderers.TextRenderer());
    }

    protected void setDefaultConverter(Grid.Column gridColumn, @Nullable MetaProperty metaProperty, Class type) {
        gridColumn.setConverter(type == Boolean.class && metaProperty != null
                ? new YesNoIconConverter()
                : new StringToObjectConverter(metaProperty));
    }

    @Override
    public void removeColumn(Column column) {
        if (column == null) {
            return;
        }

        removeContainerProperty(column);

        columns.remove(column.getId());
        columnsOrder.remove(column);
        columnGenerators.remove(column.getId());

        column.setOwner(null);
        ((ColumnImpl) column).setGridColumn(null);
    }

    protected void removeContainerProperty(Column column) {
        if (column.getPropertyPath() != null) {
            getContainerDataSource().removeContainerProperty(column.getPropertyPath());
        } else {
            containerWrapper.removeGeneratedProperty(column.getId());
        }
    }

    @Override
    public void removeColumn(String id) {
        removeColumn(getColumn(id));
    }

    @Override
    public CollectionDatasource getDatasource() {
        return datasource;
    }

    @Override
    public void setDatasource(CollectionDatasource datasource) {
        checkNotNullArgument(datasource, "datasource is null");

        if (!(datasource instanceof CollectionDatasource.Indexed)) {
            throw new IllegalArgumentException("Datasource must implement " +
                    "com.haulmont.cuba.gui.data.CollectionDatasource.Indexed");
        }

        addInitialColumns(datasource);

        this.datasource = datasource;

        List<Column> visibleColumnsOrder = getInitialVisibleColumns();

        component.removeAllColumns();
        final IndexedCollectionDsWrapper containerDatasource =
                createContainerDatasource((CollectionDatasource.Indexed) datasource, getPropertyColumns());
        containerWrapper = new GeneratedPropertyContainer(containerDatasource);
        component.setContainerDataSource(containerWrapper);

        createStubsForGeneratedColumns();

        // mark columns hidden by security permissions as visible = false
        // and remove Grid.Column to prevent its property changing
        columnsOrder.stream()
                .filter(column -> !visibleColumnsOrder.contains(column))
                .forEach(column -> {
                    ColumnImpl columnImpl = (ColumnImpl) column;
                    columnImpl.setVisible(false);
                    columnImpl.setGridColumn(null);
                });

        for (Column column : visibleColumnsOrder) {
            Grid.Column gridColumn = component.getColumn(((ColumnImpl) column).getColumnPropertyId());
            setupGridColumnProperties(gridColumn, column);
        }

        component.setColumnOrder(getColumnPropertyIds());

        initShowInfoAction(datasource);

        if (rowsCount != null) {
            rowsCount.setDatasource(datasource);
        }

        initCollectionChangeSelectionListener(datasource);

        //noinspection unchecked
        collectionDsActionsNotifier = new CollectionDsActionsNotifier(this);
        collectionDsActionsNotifier.bind(datasource);

        refreshActionsState();

        if (!canBeSorted(datasource)) {
            setSortable(false);
        }

        assignAutoDebugId();
    }

    protected void addInitialColumns(CollectionDatasource datasource) {
        if (this.columns.isEmpty()) {
            MessageTools messageTools = AppBeans.get(MessageTools.NAME);
            MetaClass metaClass = datasource.getMetaClass();
            Collection<MetaPropertyPath> paths = datasource.getView() != null ?
                    // if a view is specified - use view properties
                    metadataTools.getViewPropertyPaths(datasource.getView(), metaClass) :
                    // otherwise use all properties from meta-class
                    metadataTools.getPropertyPaths(metaClass);
            for (MetaPropertyPath metaPropertyPath : paths) {
                MetaProperty property = metaPropertyPath.getMetaProperty();
                if (!property.getRange().getCardinality().isMany() && !metadataTools.isSystem(property)) {
                    String propertyName = property.getName();
                    ColumnImpl column = new ColumnImpl(propertyName, metaPropertyPath, this);
                    MetaClass propertyMetaClass = metadataTools.getPropertyEnclosingMetaClass(metaPropertyPath);
                    column.setCaption(messageTools.getPropertyCaption(propertyMetaClass, propertyName));

                    addColumn(column);
                }
            }
        }
    }

    protected Object[] getColumnPropertyIds() {
        return columnsOrder.stream()
                .filter(Column::isVisible)
                .map(column -> ((ColumnImpl) column).getColumnPropertyId())
                .collect(Collectors.toList())
                .toArray();
    }

    protected void createStubsForGeneratedColumns() {
        PropertyValueGenerator generator = createDefaultPropertyValueGenerator();
        for (Column column : columnsOrder) {
            if (column.getPropertyPath() == null) {
                containerWrapper.addGeneratedProperty(column.getId(), generator);
            }
        }
    }

    protected void initShowInfoAction(CollectionDatasource datasource) {
        if (security.isSpecificPermitted(ShowInfoAction.ACTION_PERMISSION)) {
            ShowInfoAction action = (ShowInfoAction) getAction(ShowInfoAction.ACTION_ID);
            if (action == null) {
                action = new ShowInfoAction();
                addAction(action);
            }
            action.setDatasource(datasource);
        }
    }

    protected void initCollectionChangeSelectionListener(CollectionDatasource datasource) {
        collectionChangeSelectionListener = e -> {
            // #PL-2035, reload selection from ds
            Collection<Object> selectedItemIds = component.getSelectedRows();
            if (selectedItemIds == null) {
                selectedItemIds = Collections.emptySet();
            }

            //noinspection unchecked
            Set<Object> newSelection = selectedItemIds.stream()
                    .filter(entityId -> e.getDs().containsItem(entityId))
                    .collect(Collectors.toSet());

            if (e.getDs().getState() == Datasource.State.VALID && e.getDs().getItem() != null) {
                newSelection.add(e.getDs().getItem().getId());
            }

            if (newSelection.isEmpty()) {
                setSelected((E) null);
            } else {
                setSelectedIds(newSelection);
            }
        };

        //noinspection unchecked
        datasource.addCollectionChangeListener(new WeakCollectionChangeListener(datasource,
                collectionChangeSelectionListener));
    }

    @Override
    public String getCaption() {
        return getComposition().getCaption();
    }

    @Override
    public void setCaption(String caption) {
        getComposition().setCaption(caption);
    }

    @Override
    public String getDescription() {
        return component.getDescription();
    }

    @Override
    public void setDescription(String description) {
        component.setDescription(description);
    }

    @Override
    public boolean isTextSelectionEnabled() {
        return textSelectionEnabled;
    }

    @Override
    public void setTextSelectionEnabled(boolean textSelectionEnabled) {
        if (this.textSelectionEnabled != textSelectionEnabled) {
            this.textSelectionEnabled = textSelectionEnabled;

            if (textSelectionEnabled) {
                if (!internalStyles.contains(TEXT_SELECTION_ENABLED_STYLE)) {
                    internalStyles.add(TEXT_SELECTION_ENABLED_STYLE);
                }

                componentComposition.addStyleName(TEXT_SELECTION_ENABLED_STYLE);
            } else {
                internalStyles.remove(TEXT_SELECTION_ENABLED_STYLE);
                componentComposition.removeStyleName(TEXT_SELECTION_ENABLED_STYLE);
            }
        }
    }

    @Override
    public boolean isColumnReorderingAllowed() {
        return component.isColumnReorderingAllowed();
    }

    @Override
    public void setColumnReorderingAllowed(boolean columnReorderingAllowed) {
        component.setColumnReorderingAllowed(columnReorderingAllowed);
    }

    @Override
    public boolean isSortable() {
        return sortable;
    }

    @Override
    public void setSortable(boolean sortable) {
        this.sortable = sortable && (datasource == null || canBeSorted(datasource));
        for (Column column : getColumns()) {
            ((ColumnImpl) column).updateSortable();
        }
    }

    @Override
    public boolean isColumnsCollapsingAllowed() {
        return columnsCollapsingAllowed;
    }

    @Override
    public void setColumnsCollapsingAllowed(boolean columnsCollapsingAllowed) {
        this.columnsCollapsingAllowed = columnsCollapsingAllowed;
        for (Column column : getColumns()) {
            ((ColumnImpl) column).updateCollapsible();
        }
    }

    @Override
    public boolean isHeaderVisible() {
        return component.isHeaderVisible();
    }

    @Override
    public void setHeaderVisible(boolean headerVisible) {
        component.setHeaderVisible(headerVisible);
    }

    @Override
    public boolean isContextMenuEnabled() {
        return contextMenu.isEnabled();
    }

    @Override
    public void setContextMenuEnabled(boolean contextMenuEnabled) {
        contextMenu.setEnabled(contextMenuEnabled);
    }

    @Override
    public ColumnResizeMode getColumnResizeMode() {
        return convertToDataGridColumnResizeMode(component.getColumnResizeMode());
    }

    @Nullable
    protected ColumnResizeMode convertToDataGridColumnResizeMode(com.vaadin.shared.ui.grid.ColumnResizeMode mode) {
        switch (mode) {
            case ANIMATED:
                return ColumnResizeMode.ANIMATED;
            case SIMPLE:
                return ColumnResizeMode.SIMPLE;
        }
        return null;
    }

    @Override
    public void setColumnResizeMode(ColumnResizeMode mode) {
        component.setColumnResizeMode(convertToGridColumnResizeMode(mode));
    }

    @Nullable
    protected com.vaadin.shared.ui.grid.ColumnResizeMode convertToGridColumnResizeMode(ColumnResizeMode mode) {
        switch (mode) {
            case ANIMATED:
                return com.vaadin.shared.ui.grid.ColumnResizeMode.ANIMATED;
            case SIMPLE:
                return com.vaadin.shared.ui.grid.ColumnResizeMode.SIMPLE;
        }
        return null;
    }

    @Override
    public SelectionMode getSelectionMode() {
        return selectionMode;
    }

    @Override
    public void setSelectionMode(SelectionMode selectionMode) {
        this.selectionMode = selectionMode;
        switch (selectionMode) {
            case SINGLE:
                component.setSelectionModel(new CubaSingleSelectionModel());
                break;
            case MULTI:
                component.setSelectionModel(new CubaMultiSelectionModel());
                break;
            case MULTI_CHECK:
                component.setSelectionMode(Grid.SelectionMode.MULTI);
                break;
            case NONE:
                component.setSelectionMode(Grid.SelectionMode.NONE);
                break;
        }
    }

    @Override
    public boolean isMultiSelect() {
        return SelectionMode.MULTI.equals(selectionMode);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public E getSingleSelected() {
        final Collection selected = component.getSelectedRows();
        return selected == null || selected.isEmpty() ?
                null : (E) datasource.getItem(selected.iterator().next());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<E> getSelected() {
        Collection itemIds = component.getSelectedRows();

        if (itemIds != null) {
            Set res = new LinkedHashSet<>();
            for (Object id : itemIds) {
                Entity item = datasource.getItem(id);
                if (item != null) {
                    res.add(item);
                }
            }
            return res;
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public void setSelected(@Nullable E item) {
        if (getSelectionMode().equals(SelectionMode.NONE)) {
            return;
        }

        if (item == null) {
            component.deselectAll();
        } else {
            setSelected(Collections.singletonList(item));
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setSelected(Collection<E> items) {
        List itemIds = new ArrayList();
        for (Entity item : items) {
            if (!datasource.containsItem(item.getId())) {
                throw new IllegalStateException("Datasource doesn't contain items");
            }
            itemIds.add(item.getId());
        }
        setSelectedIds(itemIds);
    }

    protected void setSelectedIds(Collection<Object> itemIds) {
        switch (selectionMode) {
            case SINGLE:
                if (itemIds.size() > 0) {
                    Object itemId = itemIds.iterator().next();
                    ((Grid.SelectionModel.Single) component.getSelectionModel()).select(itemId);
                } else {
                    component.deselectAll();
                }
                break;
            case MULTI:
            case MULTI_CHECK:
                component.deselectAll();
                ((Grid.SelectionModel.Multi) component.getSelectionModel()).select(itemIds);
                break;
        }
    }

    @Override
    public void selectAll() {
        if (isMultiSelect()) {
            ((Grid.SelectionModel.Multi) component.getSelectionModel()).selectAll();
        }
    }

    @Override
    public void sort(String columnId, SortDirection direction) {
        ColumnImpl column = (ColumnImpl) getColumnNN(columnId);
        component.sort(column.getColumnPropertyId(), convertToGridSortDirection(direction));
    }

    @Override
    public void addAction(Action action) {
        int index = findActionById(actionList, action.getId());
        if (index < 0) {
            index = actionList.size();
        }

        addAction(action, index);
    }

    @Override
    public void addAction(Action action, int index) {
        checkNotNullArgument(action, "Action must be non null");

        int oldIndex = findActionById(actionList, action.getId());
        if (oldIndex >= 0) {
            removeAction(actionList.get(oldIndex));
            if (index > oldIndex) {
                index--;
            }
        }

        if (StringUtils.isNotEmpty(action.getCaption())) {
            ActionMenuItemWrapper menuItemWrapper = createContextMenuItem(action);
            menuItemWrapper.setAction(action);
            contextMenuItems.add(menuItemWrapper);
        }

        actionList.add(index, action);
        shortcutsDelegate.addAction(null, action);
        attachAction(action);
        actionsPermissions.apply(action);
    }

    protected ActionMenuItemWrapper createContextMenuItem(Action action) {
        MenuItem menuItem = contextMenu.addItem(action.getCaption(), null);
        menuItem.setStyleName("c-cm-item");

        return new ActionMenuItemWrapper(menuItem, showIconsForPopupMenuActions) {
            @Override
            public void performAction(Action action) {
                action.actionPerform(WebDataGrid.this);
            }
        };
    }

    protected void attachAction(Action action) {
        if (action instanceof Action.HasTarget) {
            ((Action.HasTarget) action).setTarget(this);
        }

        action.refreshState();
    }

    @Override
    public void removeAction(@Nullable Action action) {
        if (actionList.remove(action)) {
            ActionMenuItemWrapper menuItemWrapper = null;
            for (ActionMenuItemWrapper menuItem : contextMenuItems) {
                if (menuItem.getAction() == action) {
                    menuItemWrapper = menuItem;
                    break;
                }
            }

            if (menuItemWrapper != null) {
                menuItemWrapper.setAction(null);
                contextMenu.removeItem(menuItemWrapper.getMenuItem());
            }

            shortcutsDelegate.removeAction(action);
        }
    }

    @Override
    public void removeAction(@Nullable String id) {
        Action action = getAction(id);
        if (action != null) {
            removeAction(action);
        }
    }

    @Override
    public void removeAllActions() {
        for (Action action : actionList.toArray(new Action[actionList.size()])) {
            removeAction(action);
        }
    }

    @Override
    public Collection<Action> getActions() {
        return Collections.unmodifiableCollection(actionList);
    }

    @Nullable
    @Override
    public Action getAction(String id) {
        for (Action action : getActions()) {
            if (ObjectUtils.equals(action.getId(), id)) {
                return action;
            }
        }
        return null;
    }

    @Nonnull
    @Override
    public Action getActionNN(String id) {
        Action action = getAction(id);
        if (action == null) {
            throw new IllegalStateException("Unable to find action with id " + id);
        }
        return action;
    }

    @Override
    public ActionsPermissions getActionsPermissions() {
        return actionsPermissions;
    }

    protected IndexedCollectionDsWrapper createContainerDatasource(CollectionDatasource.Indexed datasource,
                                                                   Collection<MetaPropertyPath> columns) {
        return datasource instanceof CollectionDatasource.Sortable
                ? new SortableDataGridDsWrapper(datasource, columns)
                : new DataGridDsWrapper(datasource, columns);
    }

    protected List<Column> getInitialVisibleColumns() {
        MetaClass metaClass = datasource.getMetaClass();
        return columnsOrder.stream()
                .filter(column -> {
                    MetaPropertyPath propertyPath = column.getPropertyPath();
                    return propertyPath == null
                            || security.isEntityAttrReadPermitted(metaClass, propertyPath.toString());
                })
                .collect(Collectors.toList());
    }

    protected List<MetaPropertyPath> getPropertyColumns() {
        MetaClass metaClass = datasource.getMetaClass();
        return columnsOrder.stream()
                .filter(column -> {
                    MetaPropertyPath propertyPath = column.getPropertyPath();
                    return propertyPath != null
                            && security.isEntityAttrReadPermitted(metaClass, propertyPath.toString());
                })
                .map(Column::getPropertyPath)
                .collect(Collectors.toList());
    }

    @Override
    public Component getComposition() {
        return componentComposition;
    }

    @Override
    public int getFrozenColumnCount() {
        return component.getFrozenColumnCount();
    }

    @Override
    public void setFrozenColumnCount(int numberOfColumns) {
        component.setFrozenColumnCount(numberOfColumns);
    }

    @Override
    public void scrollTo(E item) {
        scrollTo(item, ScrollDestination.ANY);
    }

    @Override
    public void scrollTo(E item, ScrollDestination destination) {
        Preconditions.checkNotNullArgument(item);
        if (!getContainerDataSource().getItemIds().contains(item.getId())) {
            throw new IllegalArgumentException("Unable to find item in DataGrid");
        }

        component.scrollTo(item.getId(), convertToGridScrollDestination(destination));
    }

    @Nullable
    protected com.vaadin.shared.ui.grid.ScrollDestination convertToGridScrollDestination(
            ScrollDestination destination) {
        switch (destination) {
            case ANY:
                return com.vaadin.shared.ui.grid.ScrollDestination.ANY;
            case START:
                return com.vaadin.shared.ui.grid.ScrollDestination.START;
            case MIDDLE:
                return com.vaadin.shared.ui.grid.ScrollDestination.MIDDLE;
            case END:
                return com.vaadin.shared.ui.grid.ScrollDestination.END;
        }
        return null;
    }

    @Override
    public void scrollToStart() {
        component.scrollToStart();
    }

    @Override
    public void scrollToEnd() {
        component.scrollToEnd();
    }

    @Override
    public void repaint() {
        component.repaint();
    }

    protected boolean canBeSorted(CollectionDatasource datasource) {
        return datasource instanceof CollectionDatasource.Sortable && (!(datasource instanceof PropertyDatasource)
                || ((PropertyDatasource) datasource).getProperty().getRange().isOrdered());
    }

    @Override
    public void setDebugId(String id) {
        super.setDebugId(id);

        if (id != null) {
            componentComposition.setId(AppUI.getCurrent().getTestIdManager().getTestId(id + "_composition"));
        }
    }

    @Override
    public void assignAutoDebugId() {
        super.assignAutoDebugId();

        if (buttonsPanel != null) {
            for (com.haulmont.cuba.gui.components.Component subComponent : buttonsPanel.getComponents()) {
                if (subComponent instanceof WebAbstractComponent) {
                    ((WebAbstractComponent) subComponent).assignAutoDebugId();
                }
            }
        }
    }

    @Override
    public void setId(String id) {
        super.setId(id);

        if (id != null && AppUI.getCurrent().isTestMode()) {
            componentComposition.setCubaId(id + "_composition");
        }
    }

    @Override
    public void setStyleName(String name) {
        super.setStyleName(name);

        internalStyles.forEach(internalStyle ->
                componentComposition.addStyleName(internalStyle));
    }

    @Override
    public ButtonsPanel getButtonsPanel() {
        return buttonsPanel;
    }

    @Override
    public void setButtonsPanel(ButtonsPanel panel) {
        if (buttonsPanel != null && topPanel != null) {
            topPanel.removeComponent(WebComponentsHelper.unwrap(buttonsPanel));
            buttonsPanel.setParent(null);
        }
        buttonsPanel = panel;
        if (panel != null) {
            if (panel.getParent() != null && panel.getParent() != this) {
                throw new IllegalStateException("Component already has parent");
            }

            if (topPanel == null) {
                topPanel = createTopPanel();
                topPanel.setWidth("100%");
                componentComposition.addComponentAsFirst(topPanel);
            }
            topPanel.addComponent(WebComponentsHelper.unwrap(panel));
            if (panel instanceof VisibilityChangeNotifier) {
                ((VisibilityChangeNotifier) panel).addVisibilityChangeListener(event ->
                        updateCompositionStylesTopPanelVisible()
                );
            }
            panel.setParent(this);
        }

        updateCompositionStylesTopPanelVisible();
    }

    @Override
    public RowsCount getRowsCount() {
        return rowsCount;
    }

    @Override
    public void setRowsCount(RowsCount rowsCount) {
        if (this.rowsCount != null && topPanel != null) {
            topPanel.removeComponent(WebComponentsHelper.unwrap(this.rowsCount));
            this.rowsCount.setParent(null);
        }
        this.rowsCount = rowsCount;
        if (rowsCount != null) {
            if (rowsCount.getParent() != null && rowsCount.getParent() != this) {
                throw new IllegalStateException("Component already has parent");
            }

            if (topPanel == null) {
                topPanel = createTopPanel();
                topPanel.setWidth("100%");
                componentComposition.addComponentAsFirst(topPanel);
            }
            com.vaadin.ui.Component rc = WebComponentsHelper.unwrap(rowsCount);
            topPanel.addComponent(rc);
            topPanel.setExpandRatio(rc, 1);
            topPanel.setComponentAlignment(rc, com.vaadin.ui.Alignment.BOTTOM_RIGHT);

            if (rowsCount instanceof VisibilityChangeNotifier) {
                ((VisibilityChangeNotifier) rowsCount).addVisibilityChangeListener(event ->
                        updateCompositionStylesTopPanelVisible()
                );
            }
        }

        updateCompositionStylesTopPanelVisible();
    }

    protected HorizontalLayout createTopPanel() {
        HorizontalLayout topPanel = new HorizontalLayout();
        topPanel.setStyleName("c-data-grid-top");
        return topPanel;
    }

    // if buttons panel becomes hidden we need to set top panel height to 0
    protected void updateCompositionStylesTopPanelVisible() {
        if (topPanel != null) {
            boolean hasChildren = topPanel.getComponentCount() > 0;
            boolean anyChildVisible = false;
            for (Component childComponent : topPanel) {
                if (childComponent.isVisible()) {
                    anyChildVisible = true;
                    break;
                }
            }
            boolean topPanelVisible = hasChildren && anyChildVisible;

            if (!topPanelVisible) {
                componentComposition.removeStyleName(HAS_TOP_PANEL_STYLE_NAME);

                internalStyles.remove(HAS_TOP_PANEL_STYLE_NAME);
            } else {
                componentComposition.addStyleName(HAS_TOP_PANEL_STYLE_NAME);

                if (!internalStyles.contains(HAS_TOP_PANEL_STYLE_NAME)) {
                    internalStyles.add(HAS_TOP_PANEL_STYLE_NAME);
                }
            }
        }
    }

    @Override
    public Action getEnterPressAction() {
        return enterPressAction;
    }

    @Override
    public void setEnterPressAction(Action action) {
        enterPressAction = action;
    }

    @Override
    public Action getItemClickAction() {
        return itemClickAction;
    }

    @Override
    public void setItemClickAction(Action action) {
        if (itemClickAction != null) {
            removeAction(itemClickAction);
        }
        itemClickAction = action;
        if (!getActions().contains(action)) {
            addAction(action);
        }
    }

    @Override
    public void applySettings(Element element) {
        if (!isSettingsEnabled()) {
            return;
        }

        final Element columnsElem = element.element("columns");
        if (columnsElem != null) {
            List<Column> modelColumns = getVisibleColumns();
            List<String> modelIds = modelColumns.stream()
                    .map(String::valueOf)
                    .collect(Collectors.toList());

            List<String> loadedIds = Dom4j.elements(columnsElem, "columns").stream()
                    .map(colElem -> colElem.attributeValue("id"))
                    .collect(Collectors.toList());

            if (CollectionUtils.isEqualCollection(modelIds, loadedIds)) {
                applyColumnSettings(element, modelColumns);
            }
        }
    }

    protected void applyColumnSettings(Element element, Collection<Column> oldColumns) {
        final Element columnsElem = element.element("columns");

        List<Column> newColumns = new ArrayList<>();

        // add columns from saved settings
        for (Element colElem : Dom4j.elements(columnsElem, "columns")) {
            for (Column column : oldColumns) {
                if (column.getId().equals(colElem.attributeValue("id"))) {
                    newColumns.add(column);

                    String width = colElem.attributeValue("width");
                    if (width != null) {
                        column.setWidth(Double.parseDouble(width));
                    } else {
                        column.setWidthAuto();
                    }

                    String collapsed = colElem.attributeValue("collapsed");
                    if (collapsed != null && component.isColumnReorderingAllowed()) {
                        column.setCollapsed(Boolean.parseBoolean(collapsed));
                    }

                    break;
                }
            }
        }

        // add columns not saved in settings (perhaps new)
        for (Column column : oldColumns) {
            if (!newColumns.contains(column)) {
                newColumns.add(column);
            }
        }

        // if the data grid contains only one column, always show it
        if (newColumns.size() == 1) {
            newColumns.get(0).setCollapsed(false);
        }

        List<Object> properties = newColumns.stream()
                .map(column -> ((ColumnImpl) column).getColumnPropertyId())
                .collect(Collectors.toList());
        // We don't save settings for columns hidden by security permissions,
        // so we need to return them back to they initial positions
        columnsOrder = restoreColumnsOrder(newColumns);
        component.setColumnOrder(properties.toArray());

        if (isSortable()) {
            // apply sorting
            component.clearSortOrder();
            String sortPropertyName = columnsElem.attributeValue("sortProperty");
            if (!StringUtils.isEmpty(sortPropertyName)) {
                MetaPropertyPath sortProperty = datasource.getMetaClass().getPropertyPath(sortPropertyName);
                if (properties.contains(sortProperty) && sortProperty != null) {

                    String sortDirection = columnsElem.attributeValue("sortDirection");
                    if (StringUtils.isNotEmpty(sortDirection)) {
                        List<com.vaadin.data.sort.SortOrder> sortOrders = Collections.singletonList(
                                new com.vaadin.data.sort.SortOrder(sortProperty,
                                        com.vaadin.shared.data.sort.SortDirection.valueOf(sortDirection)));
                        component.setSortOrder(sortOrders);
                    }
                }
            }
        }
    }

    @Override
    public boolean saveSettings(Element element) {
        if (!isSettingsEnabled()) {
            return false;
        }

        Element columnsElem = element.element("columns");
        if (columnsElem != null) {
            element.remove(columnsElem);
        }
        columnsElem = element.addElement("columns");

        List<Column> visibleColumns = getVisibleColumns();
        for (Column column : visibleColumns) {
            Element colElem = columnsElem.addElement("columns");
            colElem.addAttribute("id", column.toString());

            double width = column.getWidth();
            if (width > -1) {
                colElem.addAttribute("width", String.valueOf(width));
            }

            colElem.addAttribute("collapsed", Boolean.toString(column.isCollapsed()));
        }

        List<com.vaadin.data.sort.SortOrder> sortOrders = component.getSortOrder();
        if (!sortOrders.isEmpty()) {
            com.vaadin.data.sort.SortOrder sortOrder = sortOrders.get(0);
            columnsElem.addAttribute("sortProperty", sortOrder.getPropertyId().toString());
            columnsElem.addAttribute("sortDirection", sortOrder.getDirection().toString());
        }

        return true;
    }

    @Nullable
    protected Column getColumnByGridColumn(Grid.Column gridColumn) {
        for (Column column : getColumns()) {
            if (((ColumnImpl) column).getGridColumn() == gridColumn) {
                return column;
            }
        }
        return null;
    }

    @Nullable
    protected Column getColumnByPropertyId(Object propertyId) {
        for (Column column : getColumns()) {
            if (((ColumnImpl) column).getColumnPropertyId() == propertyId) {
                return column;
            }
        }
        return null;
    }

    @Override
    public boolean isSettingsEnabled() {
        return settingsEnabled;
    }

    @Override
    public void setSettingsEnabled(boolean settingsEnabled) {
        this.settingsEnabled = settingsEnabled;
    }

    @Override
    public void addRowStyleProvider(RowStyleProvider<? super E> styleProvider) {
        if (this.rowStyleProviders == null) {
            this.rowStyleProviders = new LinkedList<>();
        }

        if (!this.rowStyleProviders.contains(styleProvider)) {
            this.rowStyleProviders.add(styleProvider);

            component.repaint();
        }
    }

    @Override
    public void removeRowStyleProvider(RowStyleProvider<? super E> styleProvider) {
        if (this.rowStyleProviders != null) {
            if (this.rowStyleProviders.remove(styleProvider)) {
                component.repaint();
            }
        }
    }

    @Override
    public void addCellStyleProvider(CellStyleProvider<? super E> styleProvider) {
        if (this.cellStyleProviders == null) {
            this.cellStyleProviders = new LinkedList<>();
        }

        if (!this.cellStyleProviders.contains(styleProvider)) {
            this.cellStyleProviders.add(styleProvider);

            component.repaint();
        }
    }

    @Override
    public void removeCellStyleProvider(CellStyleProvider<? super E> styleProvider) {
        if (this.cellStyleProviders != null) {
            if (this.cellStyleProviders.remove(styleProvider)) {
                component.repaint();
            }
        }
    }

    @Override
    public CellDescriptionProvider getCellDescriptionProvider() {
        return cellDescriptionProvider;
    }

    @Override
    public void setCellDescriptionProvider(CellDescriptionProvider<E> provider) {
        this.cellDescriptionProvider = provider;

        if (provider != null) {
            component.setCellDescriptionGenerator(createCellDescriptionGenerator());
        } else {
            component.setCellDescriptionGenerator(null);
        }
    }

    protected Grid.CellDescriptionGenerator createCellDescriptionGenerator() {
        return cell -> {
            //noinspection unchecked
            E item = (E) datasource.getItem(cell.getItemId());
            Column column = getColumnByPropertyId(cell.getPropertyId());
            if (column == null) {
                throw new RuntimeException("Column not found for propertyId: " + cell.getPropertyId());
            }
            return cellDescriptionProvider.getDescription(item, column.getId());
        };
    }

    @Override
    public RowDescriptionProvider getRowDescriptionProvider() {
        return rowDescriptionProvider;
    }

    @Override
    public void setRowDescriptionProvider(RowDescriptionProvider<E> provider) {
        this.rowDescriptionProvider = provider;

        if (provider != null) {
            component.setRowDescriptionGenerator(createRowDescriptionGenerator());
        } else {
            component.setRowDescriptionGenerator(null);
        }
    }

    protected Grid.RowDescriptionGenerator createRowDescriptionGenerator() {
        return row -> {
            //noinspection unchecked
            E item = (E) datasource.getItem(row.getItemId());

            return rowDescriptionProvider.getDescription(item);
        };
    }

    @Override
    public Column addGeneratedColumn(String columnId, ColumnGenerator<E, ?> generator) {
        return addGeneratedColumn(columnId, generator, columnsOrder.size());
    }

    @Override
    public Column addGeneratedColumn(String columnId, ColumnGenerator<E, ?> generator, int index) {
        checkNotNullArgument(columnId, "columnId is null");
        checkNotNullArgument(generator, "generator is null for column id '%s'", columnId);

        Column existingColumn = getColumn(columnId);
        if (existingColumn != null) {
            index = columnsOrder.indexOf(existingColumn);
            removeColumn(existingColumn);
        }

        containerWrapper.addGeneratedProperty(columnId, new PropertyValueGenerator<Object>() {
            @Override
            public Object getValue(Item item, Object itemId, Object propertyId) {
                //noinspection unchecked
                ColumnGeneratorEvent<E> event = new ColumnGeneratorEvent<>(WebDataGrid.this,
                        (E) datasource.getItem(itemId), propertyId.toString());

                return generator.getValue(event);
            }

            @Override
            public Class<Object> getType() {
                //noinspection unchecked
                return (Class<Object>) generator.getType();
            }
        });

        Column column = new ColumnImpl(columnId, generator.getType(), this);
        if (existingColumn != null) {
            copyColumnProperties(column, existingColumn);
        } else {
            column.setCaption(columnId);
        }

        columns.put(column.getId(), column);
        columnsOrder.add(index, column);
        columnGenerators.put(column.getId(), generator);

        Grid.Column gridColumn = component.getColumn(((ColumnImpl) column).getColumnPropertyId());
        if (gridColumn != null) {
            setupGridColumnProperties(gridColumn, column);
        }

        component.setColumnOrder(getColumnPropertyIds());

        return column;
    }

    @Override
    public ColumnGenerator<E, ?> getColumnGenerator(String columnId) {
        return columnGenerators.get(columnId);
    }

    protected void copyColumnProperties(Column column, Column existingColumn) {
        column.setCaption(existingColumn.getCaption());
        column.setVisible(existingColumn.isVisible());
        column.setCollapsed(existingColumn.isCollapsed());
        column.setCollapsible(existingColumn.isCollapsible());
        column.setCollapsingToggleCaption(existingColumn.getCollapsingToggleCaption());
        column.setMinimumWidth(existingColumn.getMinimumWidth());
        column.setMaximumWidth(existingColumn.getMaximumWidth());
        column.setWidth(existingColumn.getWidth());
        column.setExpandRatio(existingColumn.getExpandRatio());
        column.setResizable(existingColumn.isResizable());
        column.setFormatter(existingColumn.getFormatter());
    }

    @Override
    public <T extends Renderer> T createRenderer(Class<T> type) {
        DataGrid.Renderer renderer = null;
        if (type == DataGrid.TextRenderer.class) {
            renderer = new WebTextRenderer();
        }
        if (type == DataGrid.HtmlRenderer.class) {
            renderer = new WebHtmlRenderer();
        }
        if (type == DataGrid.ProgressBarRenderer.class) {
            renderer = new WebProgressBarRenderer();
        }
        if (type == DataGrid.DateRenderer.class) {
            renderer = new WebDateRenderer();
        }
        if (type == DataGrid.NumberRenderer.class) {
            renderer = new WebNumberRenderer();
        }
        if (type == DataGrid.ButtonRenderer.class) {
            renderer = new WebButtonRenderer();
        }
        if (type == DataGrid.ImageRenderer.class) {
            renderer = new WebImageRenderer();
        }
        if (type == DataGrid.CheckBoxRenderer.class) {
            renderer = new WebCheckBoxRenderer();
        }
        return type.cast(renderer);
    }

    @Override
    public void addColumnCollapsingChangeListener(ColumnCollapsingChangeListener listener) {
        getEventRouter().addListener(ColumnCollapsingChangeListener.class, listener);

        if (columnCollapsingChangeListener == null) {
            columnCollapsingChangeListener = (Grid.ColumnVisibilityChangeListener) e -> {
                if (e.isUserOriginated()) {
                    ColumnCollapsingChangeEvent event = new ColumnCollapsingChangeEvent(WebDataGrid.this,
                            getColumnByGridColumn(e.getColumn()), e.isHidden());
                    getEventRouter().fireEvent(ColumnCollapsingChangeListener.class,
                            ColumnCollapsingChangeListener::columnCollapsingChanged, event);
                }
            };
            component.addColumnVisibilityChangeListener(columnCollapsingChangeListener);
        }
    }

    @Override
    public void removeColumnCollapsingChangeListener(ColumnCollapsingChangeListener listener) {
        getEventRouter().removeListener(ColumnCollapsingChangeListener.class, listener);

        if (!getEventRouter().hasListeners(ColumnCollapsingChangeListener.class)) {
            component.removeColumnVisibilityChangeListener(columnCollapsingChangeListener);
            columnCollapsingChangeListener = null;
        }
    }

    @Override
    public void addColumnReorderListener(ColumnReorderListener listener) {
        getEventRouter().addListener(ColumnReorderListener.class, listener);
    }

    @Override
    public void removeColumnReorderListener(ColumnReorderListener listener) {
        getEventRouter().removeListener(ColumnReorderListener.class, listener);
    }

    @Override
    public void addColumnResizeListener(ColumnResizeListener listener) {
        getEventRouter().addListener(ColumnResizeListener.class, listener);

        if (columnResizeListener == null) {
            columnResizeListener = (Grid.ColumnResizeListener) e -> {
                if (e.isUserOriginated()) {
                    ColumnResizeEvent event =
                            new ColumnResizeEvent(WebDataGrid.this, getColumnByGridColumn(e.getColumn()));
                    getEventRouter().fireEvent(ColumnResizeListener.class, ColumnResizeListener::columnResized, event);
                }
            };
            component.addColumnResizeListener(columnResizeListener);
        }
    }

    @Override
    public void removeColumnResizeListener(ColumnResizeListener listener) {
        getEventRouter().removeListener(ColumnResizeListener.class, listener);

        if (!getEventRouter().hasListeners(ColumnResizeListener.class)) {
            component.removeColumnResizeListener(columnResizeListener);
            columnResizeListener = null;
        }
    }

    @Override
    public void addSortListener(SortListener listener) {
        getEventRouter().addListener(SortListener.class, listener);

        if (sortListener == null) {
            sortListener = (com.vaadin.event.SortEvent.SortListener) e -> {
                if (e.isUserOriginated()) {
                    List<SortOrder> sortOrders = new ArrayList<>();
                    for (com.vaadin.data.sort.SortOrder sortOrder : e.getSortOrder()) {
                        Column column = getColumnByPropertyId(sortOrder.getPropertyId());
                        sortOrders.add(new SortOrder(column != null ? column.getId() : null,
                                convertToDataGridSortDirection(sortOrder.getDirection())));
                    }

                    SortEvent event = new SortEvent(WebDataGrid.this, sortOrders);
                    getEventRouter().fireEvent(SortListener.class, SortListener::sorted, event);
                }
            };
            component.addSortListener(sortListener);
        }
    }

    protected SortDirection convertToDataGridSortDirection(com.vaadin.shared.data.sort.SortDirection sortDirection) {
        switch (sortDirection) {
            case ASCENDING:
                return SortDirection.ASCENDING;
            case DESCENDING:
                return SortDirection.DESCENDING;
            default:
                throw new UnsupportedOperationException("Unsupported SortDirection");
        }
    }

    protected com.vaadin.shared.data.sort.SortDirection convertToGridSortDirection(SortDirection sortDirection) {
        switch (sortDirection) {
            case ASCENDING:
                return com.vaadin.shared.data.sort.SortDirection.ASCENDING;
            case DESCENDING:
                return com.vaadin.shared.data.sort.SortDirection.DESCENDING;
            default:
                throw new UnsupportedOperationException("Unsupported SortDirection");
        }
    }

    @Override
    public void removeSortListener(SortListener listener) {
        getEventRouter().removeListener(SortListener.class, listener);

        if (!getEventRouter().hasListeners(SortListener.class)) {
            component.removeSortListener(sortListener);
            sortListener = null;
        }
    }

    @Override
    public void addContextClickListener(ContextClickListener listener) {
        getEventRouter().addListener(ContextClickListener.class, listener);

        if (contextClickListener == null) {
            contextClickListener = e -> {
                MouseEventDetails mouseEventDetails = WebWrapperUtils.toMouseEventDetails(e);

                ContextClickEvent event = new ContextClickEvent(WebDataGrid.this, mouseEventDetails);
                getEventRouter().fireEvent(ContextClickListener.class, ContextClickListener::onContextClick, event);
            };
            component.addContextClickListener(contextClickListener);
        }
    }

    @Override
    public void removeContextClickListener(ContextClickListener listener) {
        getEventRouter().removeListener(ContextClickListener.class, listener);

        if (!getEventRouter().hasListeners(ContextClickListener.class)) {
            component.removeContextClickListener(contextClickListener);
            contextClickListener = null;
        }
    }

    @Override
    public void addItemClickListener(ItemClickListener<E> listener) {
        getEventRouter().addListener(ItemClickListener.class, listener);
    }

    @Override
    public void removeItemClickListener(ItemClickListener<E> listener) {
        getEventRouter().removeListener(ItemClickListener.class, listener);
    }

    @Override
    public HeaderRow getHeaderRow(int index) {
        return getHeaderRowByGridRow(component.getHeaderRow(index));
    }

    @Nullable
    protected HeaderRow getHeaderRowByGridRow(Grid.HeaderRow gridRow) {
        for (HeaderRow headerRow : headerRows) {
            if (((HeaderRowImpl) headerRow).getGridRow() == gridRow) {
                return headerRow;
            }
        }
        return null;
    }

    @Override
    public HeaderRow appendHeaderRow() {
        Grid.HeaderRow gridRow = component.appendHeaderRow();
        return addHeaderRowInternal(gridRow);
    }

    @Override
    public HeaderRow prependHeaderRow() {
        Grid.HeaderRow gridRow = component.prependHeaderRow();
        return addHeaderRowInternal(gridRow);
    }

    @Override
    public HeaderRow addHeaderRowAt(int index) {
        Grid.HeaderRow gridRow = component.addHeaderRowAt(index);
        return addHeaderRowInternal(gridRow);
    }

    protected HeaderRow addHeaderRowInternal(Grid.HeaderRow gridRow) {
        HeaderRowImpl rowImpl = new HeaderRowImpl(this, gridRow);
        headerRows.add(rowImpl);
        return rowImpl;
    }

    @Override
    public void removeHeaderRow(HeaderRow headerRow) {
        if (headerRow == null || !headerRows.contains(headerRow)) {
            return;
        }

        component.removeHeaderRow(((HeaderRowImpl) headerRow).getGridRow());
        headerRows.remove(headerRow);
    }

    @Override
    public void removeHeaderRow(int index) {
        HeaderRow headerRow = getHeaderRow(index);
        removeHeaderRow(headerRow);
    }

    @Override
    public HeaderRow getDefaultHeaderRow() {
        return getHeaderRowByGridRow(component.getDefaultHeaderRow());
    }

    @Override
    public void setDefaultHeaderRow(HeaderRow headerRow) {
        component.setDefaultHeaderRow(((HeaderRowImpl) headerRow).getGridRow());
    }

    @Override
    public int getHeaderRowCount() {
        return component.getHeaderRowCount();
    }

    @Override
    public FooterRow getFooterRow(int index) {
        return getFooterRowByGridRow(component.getFooterRow(index));
    }

    @Nullable
    protected FooterRow getFooterRowByGridRow(Grid.FooterRow gridRow) {
        for (FooterRow footerRow : footerRows) {
            if (((FooterRowImpl) footerRow).getGridRow() == gridRow) {
                return footerRow;
            }
        }
        return null;
    }

    @Override
    public FooterRow appendFooterRow() {
        Grid.FooterRow gridRow = component.appendFooterRow();
        return addFooterRowInternal(gridRow);
    }

    @Override
    public FooterRow prependFooterRow() {
        Grid.FooterRow gridRow = component.prependFooterRow();
        return addFooterRowInternal(gridRow);
    }

    @Override
    public FooterRow addFooterRowAt(int index) {
        Grid.FooterRow gridRow = component.addFooterRowAt(index);
        return addFooterRowInternal(gridRow);
    }

    protected FooterRow addFooterRowInternal(Grid.FooterRow gridRow) {
        FooterRowImpl rowImpl = new FooterRowImpl(this, gridRow);
        footerRows.add(rowImpl);
        return rowImpl;
    }

    @Override
    public void removeFooterRow(FooterRow footerRow) {
        if (footerRow == null || !footerRows.contains(footerRow)) {
            return;
        }

        component.removeFooterRow(((FooterRowImpl) footerRow).getGridRow());
        footerRows.remove(footerRow);
    }

    @Override
    public void removeFooterRow(int index) {
        FooterRow footerRow = getFooterRow(index);
        removeFooterRow(footerRow);
    }

    @Override
    public int getFooterRowCount() {
        return component.getFooterRowCount();
    }

    @Override
    public void addSelectionListener(SelectionListener<E> listener) {
        getEventRouter().addListener(SelectionListener.class, listener);

        if (selectionListener == null) {
            selectionListener = (com.vaadin.event.SelectionEvent.SelectionListener) e -> {
                List<E> added = getItemsByIds(e.getAdded());
                List<E> removed = getItemsByIds(e.getRemoved());
                List<E> selected = getItemsByIds(e.getSelected());

                SelectionEvent<E> event = new SelectionEvent<>(WebDataGrid.this, added, removed, selected);
                //noinspection unchecked
                getEventRouter().fireEvent(SelectionListener.class, SelectionListener::selected, event);
            };
            component.addSelectionListener(selectionListener);
        }
    }

    protected List<E> getItemsByIds(Set itemIds) {
        //noinspection unchecked
        return (List<E>) itemIds.stream()
                .map(itemId -> datasource.getItem(itemId))
                .collect(Collectors.toList());
    }

    @Override
    public void removeSelectionListener(SelectionListener<E> listener) {
        getEventRouter().removeListener(SelectionListener.class, listener);

        if (!getEventRouter().hasListeners(SelectionListener.class)) {
            component.removeSelectionListener(selectionListener);
            selectionListener = null;
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    protected String getGeneratedRowStyle(Object itemId) {
        if (rowStyleProviders == null) {
            return null;
        }

        Entity item = datasource.getItem(itemId);
        String joinedStyle = null;
        for (RowStyleProvider styleProvider : rowStyleProviders) {
            String styleName = styleProvider.getStyleName(item);
            if (styleName != null) {
                if (joinedStyle == null) {
                    joinedStyle = styleName;
                } else {
                    joinedStyle += " " + styleName;
                }
            }
        }

        return joinedStyle;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    protected String getGeneratedCellStyle(Object itemId, Object propertyId) {
        if (cellStyleProviders == null) {
            return null;
        }

        Entity item = datasource.getItem(itemId);
        String joinedStyle = null;
        for (CellStyleProvider styleProvider : cellStyleProviders) {
            Column column = getColumnByPropertyId(propertyId);
            if (column == null) {
                throw new RuntimeException("Column not found for propertyId: " + propertyId);
            }
            String styleName = styleProvider.getStyleName(item, column.getId());
            if (styleName != null) {
                if (joinedStyle == null) {
                    joinedStyle = styleName;
                } else {
                    joinedStyle += " " + styleName;
                }
            }
        }

        return joinedStyle;
    }

    @Override
    public int getTabIndex() {
        return component.getTabIndex();
    }

    @Override
    public void setTabIndex(int tabIndex) {
        component.setTabIndex(tabIndex);
    }

    protected class DataGridDsWrapper extends IndexedCollectionDsWrapper {

        public DataGridDsWrapper(CollectionDatasource.Indexed datasource, Collection<MetaPropertyPath> properties) {
            super(datasource, properties, true);
        }

        @Override
        protected void createProperties(View view, MetaClass metaClass) {
            if (columns.isEmpty()) {
                super.createProperties(view, metaClass);
            } else {
                columns.values().forEach(column ->
                        properties.add(column.getPropertyPath()));
            }
        }
    }

    protected class SortableDataGridDsWrapper
            extends SortableIndexedCollectionDsWrapper
            implements DataGridContainer {

        public SortableDataGridDsWrapper(CollectionDatasource.Indexed datasource,
                                         Collection<MetaPropertyPath> properties) {
            super(datasource, properties, true);
        }

        @Override
        protected void createProperties(View view, MetaClass metaClass) {
            if (columns.isEmpty()) {
                super.createProperties(view, metaClass);
            } else {
                columns.values().forEach(column ->
                        properties.add(column.getPropertyPath()));
            }
        }

        @Override
        public void sort(Object[] propertyId, boolean[] ascending) {
            // FIXME: gg, workaround to prevent exception from datasource
            if (propertyId.length == 1) {
                super.sort(propertyId, ascending);
            }
        }

        @Override
        public Collection getSortableContainerPropertyIds() {
            Collection<?> ids = new ArrayList<>(super.getSortableContainerPropertyIds());
            for (Column column : getColumns()) {
                if (!column.isSortable()) {
                    ids.remove(column.getPropertyPath());
                }
            }
            return ids;
        }

        @Override
        public void resetSortOrder() {
            if (datasource instanceof CollectionDatasource.Sortable) {
                ((CollectionDatasource.Sortable) datasource).resetSortOrder();
            }
        }
    }

    protected class RowStyleGeneratorAdapter implements Grid.RowStyleGenerator {
        @Override
        public String getStyle(Grid.RowReference row) {
            return getGeneratedRowStyle(row.getItemId());
        }
    }

    protected class CellStyleGeneratorAdapter implements Grid.CellStyleGenerator {
        @Override
        public String getStyle(Grid.CellReference cell) {
            return getGeneratedCellStyle(cell.getItemId(), cell.getPropertyId());
        }
    }

    public static abstract class AbstractRenderer<T> implements RendererWrapper<T> {
        protected com.vaadin.ui.renderers.Renderer<T> renderer;
        protected WebDataGrid dataGrid;
        protected String nullRepresentation;

        protected AbstractRenderer() {
        }

        protected AbstractRenderer(String nullRepresentation) {
            this.nullRepresentation = nullRepresentation;
        }

        @Override
        public com.vaadin.ui.renderers.Renderer<T> getImplementation() {
            if (renderer == null) {
                renderer = createImplementation();
            }
            return renderer;
        }

        protected abstract com.vaadin.ui.renderers.Renderer<T> createImplementation();

        @Override
        public void resetImplementation() {
            renderer = null;
        }

        protected WebDataGrid getDataGrid() {
            return dataGrid;
        }

        protected void setDataGrid(WebDataGrid dataGrid) {
            this.dataGrid = dataGrid;
        }

        protected String getNullRepresentation() {
            return nullRepresentation;
        }

        protected void setNullRepresentation(String nullRepresentation) {
            checkRendererNotSet();
            this.nullRepresentation = nullRepresentation;
        }

        protected Column getColumnByGridColumn(Grid.Column column) {
            return dataGrid.getColumnByGridColumn(column);
        }

        protected void checkRendererNotSet() {
            if (renderer != null) {
                throw new IllegalStateException("Renderer parameters cannot be changed after it is set to a column");
            }
        }
    }

    protected static class ColumnImpl implements Column {

        protected String id;
        protected MetaPropertyPath propertyPath;

        protected String caption;
        protected String collapsingToggleCaption;
        protected double width;
        protected double minWidth;
        protected double maxWidth;
        protected int expandRatio;
        protected boolean collapsed;
        protected boolean visible;
        protected boolean collapsible;
        protected boolean sortable;
        protected boolean resizable;
        protected Formatter formatter;

        protected AbstractRenderer<?> renderer;
        protected Converter<?, ?> converter;

        protected Class type;
        protected Element element;

        protected WebDataGrid owner;
        protected Grid.Column gridColumn;

        public ColumnImpl(String id, @Nullable MetaPropertyPath propertyPath, WebDataGrid owner) {
            this(id, propertyPath, propertyPath != null ? propertyPath.getRangeJavaClass() : String.class, owner);
        }

        public ColumnImpl(String id, Class type, WebDataGrid owner) {
            this(id, null, type, owner);
        }

        protected ColumnImpl(String id, @Nullable MetaPropertyPath propertyPath, Class type, WebDataGrid owner) {
            this.id = id;
            this.propertyPath = propertyPath;
            this.type = type;
            this.owner = owner;

            setupDefaults();
        }

        protected void setupDefaults() {
            resizable = true;
            // the generated properties are not normally sortable,
            // but require special handling to enable sorting.
            // for now sorting for generated properties is disabled
            sortable = propertyPath != null && owner.isSortable();
            collapsible = owner.isColumnsCollapsingAllowed();
            collapsed = false;

            visible = true;

            width = -1;
            maxWidth = -1;
            minWidth = 10;
            expandRatio = -1;

            ThemeConstants theme = App.getInstance().getThemeConstants();
            width = theme.getInt("cuba.web.DataGrid.defaultColumnWidth");
            maxWidth = theme.getInt("cuba.web.DataGrid.defaultColumnMaxWidth");
            minWidth = theme.getInt("cuba.web.DataGrid.defaultColumnMinWidth");
            expandRatio = theme.getInt("cuba.web.DataGrid.defaultColumnExpandRatio");
        }

        @Override
        public String getId() {
            return id;
        }

        @Nullable
        @Override
        public MetaPropertyPath getPropertyPath() {
            return propertyPath;
        }

        public Object getColumnPropertyId() {
            return propertyPath != null ? propertyPath : id;
        }

        @Override
        public String getCaption() {
            if (gridColumn != null) {
                return gridColumn.getHeaderCaption();
            }
            return caption;
        }

        @Override
        public void setCaption(String caption) {
            this.caption = caption;
            if (gridColumn != null) {
                gridColumn.setHeaderCaption(caption);
            }
        }

        @Override
        public String getCollapsingToggleCaption() {
            if (gridColumn != null) {
                return gridColumn.getHidingToggleCaption();
            }
            return collapsingToggleCaption;
        }

        @Override
        public void setCollapsingToggleCaption(String collapsingToggleCaption) {
            this.collapsingToggleCaption = collapsingToggleCaption;
            if (gridColumn != null) {
                gridColumn.setHidingToggleCaption(collapsingToggleCaption);
            }
        }

        @Override
        public double getWidth() {
            if (gridColumn != null) {
                return gridColumn.getWidth();
            }
            return width;
        }

        @Override
        public void setWidth(double width) {
            this.width = width;
            if (gridColumn != null) {
                gridColumn.setWidth(width);
            }
        }

        @Override
        public boolean isWidthAuto() {
            if (gridColumn != null) {
                return gridColumn.isWidthUndefined();
            }
            return width < 0;
        }

        @Override
        public void setWidthAuto() {
            if (!isWidthAuto()) {
                width = -1;
                if (gridColumn != null) {
                    gridColumn.setWidthUndefined();
                }
            }
        }

        @Override
        public int getExpandRatio() {
            if (gridColumn != null) {
                return gridColumn.getExpandRatio();
            }
            return expandRatio;
        }

        @Override
        public void setExpandRatio(int expandRatio) {
            this.expandRatio = expandRatio;
            if (gridColumn != null) {
                gridColumn.setExpandRatio(expandRatio);
            }
        }

        @Override
        public void clearExpandRatio() {
            setExpandRatio(-1);
        }

        @Override
        public double getMinimumWidth() {
            if (gridColumn != null) {
                return gridColumn.getMinimumWidth();
            }
            return minWidth;
        }

        @Override
        public void setMinimumWidth(double pixels) {
            this.minWidth = pixels;
            if (gridColumn != null) {
                gridColumn.setMinimumWidth(pixels);
            }
        }

        @Override
        public double getMaximumWidth() {
            if (gridColumn != null) {
                return gridColumn.getMaximumWidth();
            }
            return maxWidth;
        }

        @Override
        public void setMaximumWidth(double pixels) {
            this.maxWidth = pixels;
            if (gridColumn != null) {
                gridColumn.setMaximumWidth(pixels);
            }
        }

        @Override
        public boolean isVisible() {
            return visible;
        }

        @Override
        public void setVisible(boolean visible) {
            if (this.visible != visible) {
                this.visible = visible;

                if (visible) {
                    owner.addContainerProperty(this);

                    Grid grid = (Grid) owner.getComponent();
                    Grid.Column gridColumn = grid.getColumn(getColumnPropertyId());
                    if (gridColumn != null) {
                        owner.setupGridColumnProperties(gridColumn, this);
                    }

                    grid.setColumnOrder(owner.getColumnPropertyIds());
                } else {
                    owner.removeContainerProperty(this);
                    setGridColumn(null);
                }
            }
        }

        @Override
        public boolean isCollapsed() {
            if (gridColumn != null) {
                return gridColumn.isHidden();
            }
            return collapsed;
        }

        @Override
        public void setCollapsed(boolean collapsed) {
            this.collapsed = collapsed;
            if (gridColumn != null) {
                gridColumn.setHidden(collapsed);
            }
        }

        @Override
        public boolean isCollapsible() {
            if (gridColumn != null) {
                return gridColumn.isHidable();
            }
            return collapsible;
        }

        @Override
        public void setCollapsible(boolean collapsible) {
            this.collapsible = collapsible;
            updateCollapsible();
        }

        public void updateCollapsible() {
            if (gridColumn != null) {
                gridColumn.setHidable(collapsible && owner.isColumnsCollapsingAllowed());
            }
        }

        @Override
        public boolean isSortable() {
            return sortable;
        }

        @Override
        public void setSortable(boolean sortable) {
            this.sortable = sortable;
            updateSortable();
        }

        public void updateSortable() {
            if (gridColumn != null) {
                gridColumn.setSortable(this.sortable && owner.isSortable());
            }
        }

        @Override
        public boolean isResizable() {
            if (gridColumn != null) {
                return gridColumn.isResizable();
            }
            return resizable;
        }

        @Override
        public void setResizable(boolean resizable) {
            this.resizable = resizable;
            if (gridColumn != null) {
                gridColumn.setResizable(resizable);
            }
        }

        @Override
        public void setLastFrozenColumn() {
            if (gridColumn != null) {
                gridColumn.setLastFrozenColumn();
            }
        }

        @Override
        public Formatter getFormatter() {
            return formatter;
        }

        @Override
        public void setFormatter(Formatter formatter) {
            this.formatter = formatter;
            if (gridColumn != null) {
                if (formatter != null) {
                    com.vaadin.data.util.converter.Converter converter = gridColumn.getConverter();
                    if (converter instanceof FormatterBasedConverter) {
                        ((FormatterBasedConverter) converter).setFormatter(formatter);
                    } else {
                        gridColumn.setConverter(new FormatterBasedConverter(formatter));
                    }
                } else {
                    if (converter != null) {
                        gridColumn.setConverter(createConverterWrapper(converter));
                    } else {
                        owner.setDefaultConverter(gridColumn, getMetaProperty(), type);
                    }
                }
                owner.repaint();
            }
        }

        @Nullable
        protected MetaProperty getMetaProperty() {
            return getPropertyPath() != null
                    ? getPropertyPath().getMetaProperty()
                    : null;
        }

        @Override
        public Element getXmlDescriptor() {
            return element;
        }

        @Override
        public void setXmlDescriptor(Element element) {
            this.element = element;
        }

        @Override
        public Renderer getRenderer() {
            return renderer;
        }

        @Override
        public void setRenderer(Renderer renderer) {
            if (renderer == null && this.renderer != null) {
                this.renderer.resetImplementation();
                this.renderer.setDataGrid(null);
            }

            this.renderer = (AbstractRenderer<?>) renderer;
            if (gridColumn != null) {
                if (this.renderer != null) {
                    this.renderer.setDataGrid(owner);
                    gridColumn.setRenderer(this.renderer.getImplementation());
                } else {
                    owner.setDefaultRenderer(gridColumn, getMetaProperty(), type);
                }
                owner.repaint();
            }
        }

        @Override
        public Converter<?, ?> getConverter() {
            return converter;
        }

        @Override
        public void setConverter(Converter<?, ?> converter) {
            this.converter = converter;
            if (gridColumn != null) {
                gridColumn.setConverter(converter != null ? createConverterWrapper(converter) : null);
                owner.repaint();
            }
        }

        protected com.vaadin.data.util.converter.Converter<?, ?> createConverterWrapper(final Converter converter) {
            return new com.vaadin.data.util.converter.Converter<Object, Object>() {
                @SuppressWarnings("unchecked")
                @Override
                public Object convertToModel(Object value, Class<?> targetType, Locale locale)
                        throws ConversionException {
                    return converter.convertToModel(value, targetType, locale);
                }

                @SuppressWarnings("unchecked")
                @Override
                public Object convertToPresentation(Object value, Class<?> targetType, Locale locale)
                        throws ConversionException {
                    return converter.convertToPresentation(value, targetType, locale);
                }

                @SuppressWarnings("unchecked")
                @Override
                public Class<Object> getModelType() {
                    return converter.getModelType();
                }

                @SuppressWarnings("unchecked")
                @Override
                public Class<Object> getPresentationType() {
                    return converter.getPresentationType();
                }
            };
        }

        @Nullable
        @Override
        public Class getType() {
            return type;
        }

        public Grid.Column getGridColumn() {
            return gridColumn;
        }

        public void setGridColumn(Grid.Column gridColumn) {
            this.gridColumn = gridColumn;
        }

        @Override
        public DataGrid getOwner() {
            return owner;
        }

        @Override
        public void setOwner(DataGrid owner) {
            this.owner = (WebDataGrid) owner;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            //noinspection unchecked
            ColumnImpl column = (ColumnImpl) o;

            return id.equals(column.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return id == null ? super.toString() : id;
        }
    }

    protected abstract static class AbstractStaticRowImp<T extends StaticCell> implements StaticRow<T> {

        protected WebDataGrid dataGrid;
        protected Grid.StaticSection.StaticRow<?> gridRow;

        public AbstractStaticRowImp(WebDataGrid dataGrid, Grid.StaticSection.StaticRow<?> gridRow) {
            this.dataGrid = dataGrid;
            this.gridRow = gridRow;
        }

        @Override
        public String getStyleName() {
            return gridRow.getStyleName();
        }

        @Override
        public void setStyleName(String styleName) {
            gridRow.setStyleName(styleName);
        }

        @Override
        public T join(String... columnIds) {
            Object[] propertyIds = new Object[columnIds.length];
            for (int i = 0; i < columnIds.length; i++) {
                ColumnImpl column = (ColumnImpl) dataGrid.getColumnNN(columnIds[i]);
                propertyIds[i] = column.getColumnPropertyId();
            }

            T cell = joinInternal(propertyIds);

            // FIXME: gg, workaround for https://github.com/vaadin/framework/issues/8512
            switch (cell.getCellType()) {
                case HTML:
                    cell.setHtml(cell.getHtml());
                    break;
                case TEXT:
                    cell.setText(cell.getText());
                    break;
                case COMPONENT:
                    cell.setComponent(cell.getComponent());
                    break;
            }

            return cell;
        }

        protected abstract T joinInternal(Object[] propertyIds);

        @Override
        public T getCell(String columnId) {
            ColumnImpl column = (ColumnImpl) dataGrid.getColumnNN(columnId);
            return getCellInternal(column.getColumnPropertyId());
        }

        protected abstract T getCellInternal(Object propertyId);

        public Grid.StaticSection.StaticRow<?> getGridRow() {
            return gridRow;
        }
    }

    protected static class HeaderRowImpl extends AbstractStaticRowImp<HeaderCell> implements HeaderRow {

        public HeaderRowImpl(WebDataGrid dataGrid, Grid.HeaderRow gridRow) {
            super(dataGrid, gridRow);
        }

        @Override
        public Grid.HeaderRow getGridRow() {
            return (Grid.HeaderRow) super.getGridRow();
        }

        @Override
        protected HeaderCell getCellInternal(Object propertyId) {
            Grid.HeaderCell gridCell = getGridRow().getCell(propertyId);
            return new HeaderCellImpl(this, gridCell);
        }

        @Override
        protected HeaderCell joinInternal(Object[] propertyIds) {
            Grid.HeaderCell gridCell = getGridRow().join(propertyIds);
            return new HeaderCellImpl(this, gridCell);
        }
    }

    protected static class FooterRowImpl extends AbstractStaticRowImp<FooterCell> implements FooterRow {

        public FooterRowImpl(WebDataGrid dataGrid, Grid.FooterRow gridRow) {
            super(dataGrid, gridRow);
        }

        @Override
        public Grid.FooterRow getGridRow() {
            return (Grid.FooterRow) super.getGridRow();
        }

        @Override
        protected FooterCell getCellInternal(Object propertyId) {
            Grid.FooterCell gridCell = getGridRow().getCell(propertyId);
            return new FooterCellImpl(this, gridCell);
        }

        @Override
        protected FooterCell joinInternal(Object[] propertyIds) {
            Grid.FooterCell gridCell = getGridRow().join(propertyIds);
            return new FooterCellImpl(this, gridCell);
        }
    }

    protected abstract static class AbstractStaticCellImpl implements StaticCell {

        protected StaticRow<?> row;
        protected com.haulmont.cuba.gui.components.Component component;

        public AbstractStaticCellImpl(StaticRow<?> row) {
            this.row = row;
        }

        @Override
        public StaticRow<?> getRow() {
            return row;
        }

        @Override
        public com.haulmont.cuba.gui.components.Component getComponent() {
            return component;
        }

        @Override
        public void setComponent(com.haulmont.cuba.gui.components.Component component) {
            this.component = component;
        }
    }

    protected static class HeaderCellImpl extends AbstractStaticCellImpl implements HeaderCell {

        protected Grid.HeaderCell gridCell;

        public HeaderCellImpl(StaticRow<?> row, Grid.HeaderCell gridCell) {
            super(row);
            this.gridCell = gridCell;
        }

        @Override
        public String getStyleName() {
            return gridCell.getStyleName();
        }

        @Override
        public void setStyleName(String styleName) {
            gridCell.setStyleName(styleName);
        }

        @Override
        public DataGridStaticCellType getCellType() {
            return WebWrapperUtils.toDataGridStaticCellType(gridCell.getCellType());
        }

        @Override
        public void setComponent(com.haulmont.cuba.gui.components.Component component) {
            super.setComponent(component);
            gridCell.setComponent(component.unwrap(Component.class));
        }

        @Override
        public String getHtml() {
            return gridCell.getHtml();
        }

        @Override
        public void setHtml(String html) {
            gridCell.setHtml(html);
        }

        @Override
        public String getText() {
            return gridCell.getText();
        }

        @Override
        public void setText(String text) {
            gridCell.setText(text);
        }
    }

    protected static class FooterCellImpl extends AbstractStaticCellImpl implements FooterCell {

        protected Grid.FooterCell gridCell;

        public FooterCellImpl(StaticRow<?> row, Grid.FooterCell gridCell) {
            super(row);
            this.gridCell = gridCell;
        }

        @Override
        public String getStyleName() {
            return gridCell.getStyleName();
        }

        @Override
        public void setStyleName(String styleName) {
            gridCell.setStyleName(styleName);
        }

        @Override
        public DataGridStaticCellType getCellType() {
            return WebWrapperUtils.toDataGridStaticCellType(gridCell.getCellType());
        }

        @Override
        public void setComponent(com.haulmont.cuba.gui.components.Component component) {
            super.setComponent(component);
            gridCell.setComponent(component.unwrap(Component.class));
        }

        @Override
        public String getHtml() {
            return gridCell.getHtml();
        }

        @Override
        public void setHtml(String html) {
            gridCell.setHtml(html);
        }

        @Override
        public String getText() {
            return gridCell.getText();
        }

        @Override
        public void setText(String text) {
            gridCell.setText(text);
        }
    }

    public static class ActionMenuItemWrapper {
        protected MenuItem menuItem;
        protected Action action;

        protected boolean showIconsForPopupMenuActions;

        protected PropertyChangeListener actionPropertyChangeListener;

        public ActionMenuItemWrapper(MenuItem menuItem, boolean showIconsForPopupMenuActions) {
            this.menuItem = menuItem;
            this.showIconsForPopupMenuActions = showIconsForPopupMenuActions;

            this.menuItem.setCommand((Menu.Command) selectedItem -> {
                if (action != null) {
                    performAction(action);
                }
            });
        }

        public void performAction(Action action) {
            action.actionPerform(null);
        }

        public MenuItem getMenuItem() {
            return menuItem;
        }

        public Action getAction() {
            return action;
        }

        public void setAction(Action action) {
            if (this.action != action) {
                if (this.action != null) {
                    this.action.removePropertyChangeListener(actionPropertyChangeListener);
                }

                this.action = action;

                if (action != null) {
                    String caption = action.getCaption();
                    if (!StringUtils.isEmpty(caption)) {
                        setCaption(caption);
                    }

                    menuItem.setEnabled(action.isEnabled());
                    menuItem.setVisible(action.isVisible());

                    if (action.getIcon() != null) {
                        setIcon(action.getIcon());
                    }

                    actionPropertyChangeListener = evt -> {
                        if (Action.PROP_ICON.equals(evt.getPropertyName())) {
                            setIcon(ActionMenuItemWrapper.this.action.getIcon());
                        } else if (Action.PROP_CAPTION.equals(evt.getPropertyName())) {
                            setCaption(ActionMenuItemWrapper.this.action.getCaption());
                        } else if (Action.PROP_ENABLED.equals(evt.getPropertyName())) {
                            setEnabled(ActionMenuItemWrapper.this.action.isEnabled());
                        } else if (Action.PROP_VISIBLE.equals(evt.getPropertyName())) {
                            setVisible(ActionMenuItemWrapper.this.action.isVisible());
                        }
                    };
                    action.addPropertyChangeListener(actionPropertyChangeListener);
                }
            }
        }

        public void setEnabled(boolean enabled) {
            menuItem.setEnabled(enabled);
        }

        public void setVisible(boolean visible) {
            menuItem.setVisible(visible);
        }

        public void setIcon(String icon) {
            if (showIconsForPopupMenuActions) {
                if (!StringUtils.isEmpty(icon)) {
                    menuItem.setIcon(WebComponentsHelper.getIcon(icon));
                } else {
                    menuItem.setIcon(null);
                }
            }
        }

        public void setCaption(String caption) {
            if (action.getShortcutCombination() != null) {
                StringBuilder sb = new StringBuilder();
                sb.append(caption);
                if (action.getShortcutCombination() != null) {
                    sb.append(" (").append(action.getShortcutCombination().format()).append(")");
                }
                caption = sb.toString();
            }

            menuItem.setText(caption);
        }
    }

    protected static class GridComposition extends CssLayout {
        protected Grid grid;

        public Grid getGrid() {
            return grid;
        }

        public void setGrid(Grid grid) {
            this.grid = grid;
        }

        @Override
        public void setHeight(String height) {
            super.setHeight(height);

            if (getHeight() < 0) {
                grid.setHeightUndefined();
                grid.setHeightMode(HeightMode.UNDEFINED);
            } else {
                grid.setHeight(100, Unit.PERCENTAGE);
                grid.setHeightMode(HeightMode.CSS);
            }
        }

        @Override
        public void setWidth(String width) {
            super.setWidth(width);

            if (getWidth() < 0) {
                grid.setWidthUndefined();
            } else {
                grid.setWidth(100, Unit.PERCENTAGE);
            }
        }
    }
}
