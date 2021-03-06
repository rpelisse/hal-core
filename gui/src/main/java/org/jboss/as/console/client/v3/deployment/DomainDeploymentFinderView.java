/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.console.client.v3.deployment;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.LayoutPanel;
import com.google.gwt.user.client.ui.SplitLayoutPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.ProvidesKey;
import com.google.inject.Inject;
import org.jboss.as.console.client.Console;
import org.jboss.as.console.client.core.NameTokens;
import org.jboss.as.console.client.core.SuspendableViewImpl;
import org.jboss.as.console.client.domain.model.ServerGroupRecord;
import org.jboss.as.console.client.domain.model.SimpleCallback;
import org.jboss.as.console.client.preview.PreviewContent;
import org.jboss.as.console.client.preview.PreviewContentFactory;
import org.jboss.as.console.client.v3.dmr.Operation;
import org.jboss.as.console.client.v3.dmr.ResourceAddress;
import org.jboss.as.console.client.widgets.nav.v3.ClearFinderSelectionEvent;
import org.jboss.as.console.client.widgets.nav.v3.ColumnManager;
import org.jboss.as.console.client.widgets.nav.v3.FinderColumn;
import org.jboss.as.console.client.widgets.nav.v3.MenuDelegate;
import org.jboss.ballroom.client.widgets.window.Feedback;
import org.jboss.dmr.client.ModelDescriptionConstants;
import org.jboss.dmr.client.ModelNode;
import org.jboss.dmr.client.dispatch.DispatchAsync;
import org.jboss.dmr.client.dispatch.impl.DMRAction;
import org.jboss.dmr.client.dispatch.impl.DMRResponse;

import java.util.Arrays;

import static org.jboss.as.console.client.widgets.nav.v3.FinderColumn.FinderId.DEPLOYMENT;
import static org.jboss.as.console.client.widgets.nav.v3.MenuDelegate.Role.Operation;
import static org.jboss.dmr.client.ModelDescriptionConstants.*;

/**
 * @author Harald Pehl
 */
public class DomainDeploymentFinderView extends SuspendableViewImpl implements DomainDeploymentFinder.MyView {

    private DomainDeploymentFinder presenter;
    private SplitLayoutPanel layout;
    private LayoutPanel contentCanvas;
    private ColumnManager columnManager;

    private BrowseByColumn browseByColumn;
    private Widget browseByColumnWidget;
    private FinderColumn<Content> contentColumn;
    private Widget contentColumnWidget;
    private FinderColumn<Content> unassignedColumn;
    private Widget unassignedColumnWidget;
    private FinderColumn<ServerGroupRecord> serverGroupColumn;
    private Widget serverGroupColumnWidget;
    private FinderColumn<Assignment> assignmentColumn;
    private Widget assignmentColumnWidget;
    private SubdeploymentColumn subdeploymentColumn;
    private Widget subdeploymentColumnWidget;

    @Inject
    @SuppressWarnings("unchecked")
    public DomainDeploymentFinderView(final DispatchAsync dispatcher, final PreviewContentFactory contentFactory) {

        contentCanvas = new LayoutPanel();
        layout = new SplitLayoutPanel(2);
        columnManager = new ColumnManager(layout, DEPLOYMENT);


        // ------------------------------------------------------ subdeployments

        subdeploymentColumn = new SubdeploymentColumn(columnManager, 4, NameTokens.DomainDeploymentFinder);
        subdeploymentColumnWidget = subdeploymentColumn.asWidget();


        // ------------------------------------------------------ assignments

        assignmentColumn = new FinderColumn<>(
                DEPLOYMENT,
                "Deployment",
                new FinderColumn.Display<Assignment>() {
                    @Override
                    public boolean isFolder(final Assignment data) {
                        return data.isEnabled() && data.hasDeployment() && data.getDeployment().hasSubdeployments();
                    }

                    @Override
                    public SafeHtml render(final String baseCss, final Assignment data) {
                        return Templates.ITEMS.item(baseCss, data.getName(), ""); // tooltip is defined below
                    }

                    @Override
                    public String rowCss(final Assignment data) {
                        if (!data.hasDeployment()) {
                            return "error";
                        } else if (!data.isEnabled()) {
                            return "paused";
                        }
                        return "good";
                    }
                },
                new ProvidesKey<Assignment>() {
                    @Override
                    public Object getKey(final Assignment item) {
                        return item.getName();
                    }
                },
                NameTokens.DomainDeploymentFinder,
                999);
        assignmentColumn.setShowSize(true);

        assignmentColumn.setTopMenuItems(
                new MenuDelegate<>("Add",
                        item -> presenter.launchAddAssignmentWizard(serverGroupColumn.getSelectedItem().getName()),
                        Operation)
        );

        //noinspection Convert2MethodRef
        MenuDelegate<Assignment> enableDisableDelegate = new MenuDelegate<Assignment>("(En/Dis)able",
                item -> presenter.verifyEnableDisableAssignment(item), Operation) {
            @Override
            public String render(final Assignment data) {
                return data.isEnabled() ? "Disable" : "Enable";
            }
        };
        //noinspection Convert2MethodRef
        assignmentColumn.setMenuItems(
                enableDisableDelegate,
                new MenuDelegate<>("Replace", item -> presenter.launchReplaceAssignmentWizard(item), Operation),
                new MenuDelegate<>("Unassign", item ->
                        Feedback.confirm(Console.CONSTANTS.common_label_areYouSure(),
                                "Unassign " + item.getName(),
                                isConfirmed -> {
                                    if (isConfirmed) {
                                        presenter.modifyAssignment(item, REMOVE,
                                                item.getName() + " successfully unassigned.");
                                    }
                                }))
        );

        assignmentColumn.setFilter((item, token) -> item.getDeployment().getName().contains(token));

        assignmentColumn.setTooltipDisplay(Templates::assignmentTooltip);
        assignmentColumn.setPreviewFactory((data, callback) -> callback.onSuccess(Templates.assignmentPreview(data)));

        assignmentColumn.addSelectionChangeHandler(selectionChangeEvent -> {
            columnManager.reduceColumnsTo(3);
            if (assignmentColumn.hasSelectedItem()) {
                columnManager.updateActiveSelection(assignmentColumnWidget);
                Assignment assignment = assignmentColumn.getSelectedItem();
                if (assignment.isEnabled() && assignment.hasDeployment() && assignment.getDeployment()
                        .hasSubdeployments()) {
                    Deployment deployment = assignment.getDeployment();
                    columnManager.appendColumn(subdeploymentColumnWidget);
                    subdeploymentColumn.updateFrom(deployment.getSubdeployments());
                }
            }
        });
        assignmentColumnWidget = assignmentColumn.asWidget();


        // ------------------------------------------------------ server group

        serverGroupColumn = new FinderColumn<>(
                DEPLOYMENT,
                "Server Group",
                new FinderColumn.Display<ServerGroupRecord>() {

                    @Override
                    public boolean isFolder(ServerGroupRecord data) {
                        return true;
                    }

                    @Override
                    public SafeHtml render(String baseCss, ServerGroupRecord data) {
                        return Templates.ITEMS.item(baseCss, data.getName(),
                                data.getName() + " (Profile " + data.getProfileName() + ")");
                    }

                    @Override
                    public String rowCss(ServerGroupRecord data) {
                        return "";
                    }
                },
                new ProvidesKey<ServerGroupRecord>() {
                    @Override
                    public Object getKey(ServerGroupRecord item) {
                        return item.getName();
                    }
                },
                NameTokens.DomainDeploymentFinder);
        serverGroupColumn.setShowSize(true);

        serverGroupColumn.setFilter((item, token) -> item.getName().contains(token));

        serverGroupColumn.setPreviewFactory((data, callback) -> {
            ResourceAddress address = new ResourceAddress().add("server-group", data.getName());
            Operation op = new Operation.Builder(ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION, address)
                    .param(CHILD_TYPE, "deployment")
                    .build();
            dispatcher.execute(new DMRAction(op), new AsyncCallback<DMRResponse>() {
                @Override
                public void onFailure(final Throwable caught) {
                    callback.onSuccess(Templates.serverGroupPreview(data, -1));
                }

                @Override
                public void onSuccess(final DMRResponse response) {
                    ModelNode result = response.get();
                    if (!result.hasDefined(OUTCOME) || result.isFailure()) {
                        callback.onSuccess(Templates.serverGroupPreview(data, -1));
                    } else {
                        int deployments = result.get(RESULT).asList().size();
                        callback.onSuccess(Templates.serverGroupPreview(data, deployments));
                    }
                }
            });
        });

        serverGroupColumn.addSelectionChangeHandler(event -> {
            columnManager.reduceColumnsTo(2);
            if (serverGroupColumn.hasSelectedItem()) {
                columnManager.updateActiveSelection(serverGroupColumnWidget);
                columnManager.appendColumn(assignmentColumnWidget);
                presenter.loadAssignments(serverGroupColumn.getSelectedItem().getName());
            }
        });
        serverGroupColumnWidget = serverGroupColumn.asWidget();


        // ------------------------------------------------------ (unassigned) content

        //noinspection Convert2MethodRef
        contentColumn = new ContentColumn("All Content", columnManager,
                new MenuDelegate<Content>("Add", item -> presenter.launchAddContentWizard(), Operation)
                        .setOperationAddress("/deployment=*", "add"),
                new MenuDelegate<Content>("Assign", item -> presenter.launchAssignContentDialog(item), Operation)
                        .setOperationAddress("/deployment=*", "add"),
                new MenuDelegate<Content>("Unassign", item -> presenter.launchUnassignContentDialog(item), Operation)
                        .setOperationAddress("/deployment=*", "remove"),
                new MenuDelegate<Content>("Remove", item -> {
                    if (!item.getAssignments().isEmpty()) {
                        String serverGroups = "\t- " + Joiner.on("\n\t- ").join(
                                Lists.transform(item.getAssignments(), Assignment::getServerGroup));
                        Console.error(item.getName() + " is in use. Please remove its assignments first.",
                                item.getName() + " is assigned to the following server groups:\n\n" + serverGroups);
                    } else {
                        Feedback.confirm(Console.CONSTANTS.common_label_areYouSure(), "Remove " + item.getName(),
                                isConfirmed -> {
                                    if (isConfirmed) {
                                        presenter.removeContent(item, false);
                                    }
                                });
                    }
                }, Operation).setOperationAddress("/deployment=*", "remove"));

        contentColumn.setFilter((item, token) ->
                item.getName().contains(token) || item.getRuntimeName().contains(token));

        contentColumnWidget = contentColumn.asWidget();

        //noinspection Convert2MethodRef
        unassignedColumn = new ContentColumn("Unassigned", columnManager,
                null,
                new MenuDelegate<Content>("Assign", item -> presenter.launchAssignContentDialog(item), Operation)
                        .setOperationAddress("/deployment=*", "add"),
                new MenuDelegate<Content>("Remove", item ->
                        Feedback.confirm(Console.CONSTANTS.common_label_areYouSure(), "Remove " + item.getName(),
                                isConfirmed -> {
                                    if (isConfirmed) {
                                        presenter.removeContent(item, true);
                                    }
                                }), Operation)
                        .setOperationAddress("/deployment=*", "remove"));

        unassignedColumn.setFilter((item, token) ->
                item.getName().contains(token) || item.getRuntimeName().contains(token));

        unassignedColumnWidget = unassignedColumn.asWidget();


        // ------------------------------------------------------ browse by

        BrowseByItem contentRepositoryItem = new BrowseByItem("Content Repository",
                PreviewContent.INSTANCE.content_repository(), () -> {
            columnManager.appendColumn(contentColumnWidget);
            presenter.loadContentRepository();
        });
        BrowseByItem unassignedContentItem = new BrowseByItem("Unassigned Content",
                PreviewContent.INSTANCE.unassigned_content(), () -> {
            columnManager.appendColumn(unassignedColumnWidget);
            presenter.loadUnassignedContent();
        });
        BrowseByItem serverGroupsItem = new BrowseByItem("Server Groups",
                PreviewContent.INSTANCE.server_group_content(), () -> {
            columnManager.appendColumn(serverGroupColumnWidget);
            presenter.loadServerGroups();
        });

        browseByColumn = new BrowseByColumn(contentFactory, event -> {
            columnManager.reduceColumnsTo(1);
            if (browseByColumn.hasSelectedItem()) {
                columnManager.updateActiveSelection(browseByColumnWidget);
                browseByColumn.getSelectedItem().onSelect().execute();
            } else {
                startupContent(contentFactory);
            }
        }, NameTokens.DomainDeploymentFinder);
        browseByColumnWidget = browseByColumn.asWidget();
        browseByColumn.updateFrom(Arrays.asList(contentRepositoryItem, unassignedContentItem, serverGroupsItem));


        // ------------------------------------------------------ assemble UI

        columnManager.addWest(browseByColumnWidget);
        columnManager.addWest(contentColumnWidget);
        columnManager.addWest(unassignedColumnWidget);
        columnManager.addWest(serverGroupColumnWidget);
        columnManager.addWest(assignmentColumnWidget);
        columnManager.addWest(subdeploymentColumnWidget);
        columnManager.add(contentCanvas);
        columnManager.setInitialVisible(1);
    }

    @Override
    public void setPresenter(final DomainDeploymentFinder presenter) {
        this.presenter = presenter;
    }

    @Override
    public Widget createWidget() {
        return layout;
    }


    // ------------------------------------------------------ slot management

    @Override
    public void setInSlot(final Object slot, final IsWidget content) {
        if (slot == DomainDeploymentFinder.TYPE_MainContent) {
            if (content != null) { setContent(content); } else { contentCanvas.clear(); }
        }
    }

    private void setContent(IsWidget newContent) {
        contentCanvas.clear();
        contentCanvas.add(newContent);
    }


    // ------------------------------------------------------ finder related methods

    @Override
    public void setPreview(final SafeHtml html) {
        Scheduler.get().scheduleDeferred(() -> {
            contentCanvas.clear();
            contentCanvas.add(new HTML(html));
        });
    }

    @Override
    public void toggleScrolling(final boolean enforceScrolling, final int requiredWidth) {
        columnManager.toogleScrolling(enforceScrolling, requiredWidth);
    }

    public void clearActiveSelection(final ClearFinderSelectionEvent event) {
        browseByColumnWidget.getElement().removeClassName("active");
        contentColumnWidget.getElement().removeClassName("active");
        unassignedColumnWidget.getElement().removeClassName("active");
        serverGroupColumnWidget.getElement().removeClassName("active");
        assignmentColumnWidget.getElement().removeClassName("active");
        subdeploymentColumnWidget.getElement().removeClassName("active");
    }

    private void startupContent(PreviewContentFactory contentFactory) {
        contentFactory.createContent(PreviewContent.INSTANCE.deployments_empty(),
                new SimpleCallback<SafeHtml>() {
                    @Override
                    public void onSuccess(SafeHtml previewContent) {
                        setPreview(previewContent);
                    }
                }
        );
    }


    // ------------------------------------------------------ update columns

    @Override
    public void updateContentRepository(final Iterable<Content> content) {
        contentColumn.updateFrom(Lists.newArrayList(content));
    }

    @Override
    public void updateUnassigned(final Iterable<Content> unassigned) {
        unassignedColumn.updateFrom(Lists.newArrayList(unassigned));
    }

    @Override
    public void updateServerGroups(final Iterable<ServerGroupRecord> serverGroups) {
        serverGroupColumn.updateFrom(Lists.newArrayList(serverGroups));
    }

    @Override
    public void updateAssignments(final Iterable<Assignment> assignments) {
        assignmentColumn.updateFrom(Lists.newArrayList(assignments));
    }
}
