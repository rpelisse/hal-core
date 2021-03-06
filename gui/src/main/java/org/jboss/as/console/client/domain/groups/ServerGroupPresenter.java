/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */

package org.jboss.as.console.client.domain.groups;

import com.google.gwt.user.client.Command;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.Presenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.NameToken;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.proxy.Place;
import com.gwtplatform.mvp.client.proxy.PlaceManager;
import com.gwtplatform.mvp.client.proxy.ProxyPlace;
import com.gwtplatform.mvp.client.proxy.RevealContentEvent;
import com.gwtplatform.mvp.shared.proxy.PlaceRequest;
import org.jboss.as.console.client.Console;
import org.jboss.as.console.client.core.MainLayoutPresenter;
import org.jboss.as.console.client.core.NameTokens;
import org.jboss.as.console.client.domain.model.ProfileRecord;
import org.jboss.as.console.client.domain.model.ServerGroupDAO;
import org.jboss.as.console.client.domain.model.ServerGroupRecord;
import org.jboss.as.console.client.domain.model.SimpleCallback;
import org.jboss.as.console.client.rbac.SecurityFramework;
import org.jboss.as.console.client.shared.BeanFactory;
import org.jboss.as.console.client.shared.jvm.CreateJvmCmd;
import org.jboss.as.console.client.shared.jvm.DeleteJvmCmd;
import org.jboss.as.console.client.shared.jvm.Jvm;
import org.jboss.as.console.client.shared.jvm.JvmManagement;
import org.jboss.as.console.client.shared.jvm.UpdateJvmCmd;
import org.jboss.as.console.client.shared.properties.CreatePropertyCmd;
import org.jboss.as.console.client.shared.properties.DeletePropertyCmd;
import org.jboss.as.console.client.shared.properties.NewPropertyWizard;
import org.jboss.as.console.client.shared.properties.PropertyManagement;
import org.jboss.as.console.client.shared.properties.PropertyRecord;
import org.jboss.as.console.client.v3.dmr.AddressTemplate;
import org.jboss.as.console.client.v3.stores.domain.ProfileStore;
import org.jboss.as.console.client.v3.stores.domain.ServerGroupStore;
import org.jboss.as.console.client.v3.stores.domain.ServerStore;
import org.jboss.as.console.client.widgets.forms.ApplicationMetaData;
import org.jboss.as.console.mbui.behaviour.CoreGUIContext;
import org.jboss.as.console.spi.OperationMode;
import org.jboss.as.console.spi.RequiredResources;
import org.jboss.as.console.spi.SearchIndex;
import org.jboss.ballroom.client.rbac.SecurityContextChangedEvent;
import org.jboss.ballroom.client.widgets.window.DefaultWindow;
import org.jboss.dmr.client.ModelNode;
import org.jboss.dmr.client.dispatch.DispatchAsync;

import java.util.List;
import java.util.Map;

import static org.jboss.as.console.spi.OperationMode.Mode.DOMAIN;

/**
 * Maintains a single server group.
 *
 * @author Heiko Braun
 * @date 2/16/11
 *
 *
 */
public class ServerGroupPresenter
        extends Presenter<ServerGroupPresenter.MyView, ServerGroupPresenter.MyProxy>
        implements JvmManagement, PropertyManagement {

    @ProxyCodeSplit
    @NameToken(NameTokens.ServerGroupPresenter)
    @OperationMode(DOMAIN)
    @RequiredResources(resources = {
            "/server-group=*",
            "opt://server-group=*/system-property=*"},
            recursive = false)
    @SearchIndex(keywords = {"group", "server-group", "profile", "socket-binding", "jvm"})
    public interface MyProxy extends ProxyPlace<ServerGroupPresenter>, Place {}


    public interface MyView extends View {
        void setPresenter(ServerGroupPresenter presenter);
        void updateSocketBindings(List<String> result);
        void setJvm(ServerGroupRecord group, Jvm jvm);
        void setProperties(ServerGroupRecord group, List<PropertyRecord> properties);
        void updateProfiles(List<ProfileRecord> result);
        void updateFrom(ServerGroupRecord group);
    }

    private ServerGroupDAO serverGroupDAO;
    private DefaultWindow window;
    private DefaultWindow propertyWindow;

    private final ProfileStore profileStore;
    private DispatchAsync dispatcher;
    private BeanFactory factory;
    private ApplicationMetaData propertyMetaData;
    private final ServerGroupStore serverGroupStore;
    private final PlaceManager placeManager;
    private final SecurityFramework securityFramework;
    private final ServerStore serverStore;
    private final CoreGUIContext statementContext;

    @Inject
    public ServerGroupPresenter(
            EventBus eventBus, MyView view, MyProxy proxy,
            ServerGroupDAO serverGroupDAO,
            ProfileStore profileStore,
            DispatchAsync dispatcher, BeanFactory factory,
            ApplicationMetaData propertyMetaData, ServerGroupStore serverGroupStore, PlaceManager placeManager,
            SecurityFramework securityFramework, ServerStore serverStore, CoreGUIContext statementContext) {
        super(eventBus, view, proxy);

        this.serverGroupDAO = serverGroupDAO;
        this.profileStore = profileStore;

        this.dispatcher = dispatcher;
        this.factory = factory;
        this.propertyMetaData = propertyMetaData;
        this.serverGroupStore = serverGroupStore;
        this.placeManager = placeManager;
        this.securityFramework = securityFramework;
        this.serverStore = serverStore;
        this.statementContext = statementContext;
    }

    @Override
    protected void onBind() {
        super.onBind();
        getView().setPresenter(this);
    }

    @Override
    public void prepareFromRequest(PlaceRequest request) {
        SecurityContextChangedEvent.AddressResolver resolver = new SecurityContextChangedEvent.AddressResolver<AddressTemplate>() {
            @Override
            public String resolve(AddressTemplate template) {
                String resolved = template.resolveAsKey(statementContext, serverStore.getSelectedGroup());
                return resolved;
            }
        };

        Command cmd = () -> getProxy().manualReveal(ServerGroupPresenter.this);

        // RBAC: context change propagation
        SecurityContextChangedEvent.fire(
                ServerGroupPresenter.this,
                cmd,
                resolver
        );
    }

    @Override
    protected void onReset() {

        super.onReset();

        // (1)
        getView().updateProfiles(profileStore.getProfiles());

        // (2)
        serverGroupDAO.loadSocketBindingGroupNames(new SimpleCallback<List<String>>() {
            @Override
            public void onSuccess(List<String> result) {

                getView().updateSocketBindings(result);

                // (3)
                serverGroupDAO.loadServerGroup(serverStore.getSelectedGroup(), new SimpleCallback<ServerGroupRecord>() {
                    @Override
                    public void onSuccess(ServerGroupRecord result) {

                        updateView(result);

                    }
                });

            }
        });
    }

    private void loadServerGroup() {
        serverGroupDAO.loadServerGroup(serverStore.getSelectedGroup(),
                new SimpleCallback<ServerGroupRecord>() {
                    @Override
                    public void onSuccess(ServerGroupRecord group) {
                        updateView(group);
                    }
                });
    }

    private void updateView(ServerGroupRecord serverGroup) {

        getView().updateFrom(serverGroup);
    }

    @Override
    protected void revealInParent() {
        RevealContentEvent.fire(this, MainLayoutPresenter.TYPE_MainContent, this);
    }

    public void onDeleteGroup(final ServerGroupRecord group) {

        serverGroupDAO.delete(group, new SimpleCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean wasSuccessful) {
                if (wasSuccessful) {
                    Console.info(Console.MESSAGES.deleted(group.getName()));
                } else {
                    Console.error(Console.MESSAGES.deletionFailed(group.getName()));
                }
                loadServerGroup();
            }
        });
    }

    public void onSaveChanges(final ServerGroupRecord group, Map<String,Object> changeset) {

        serverGroupDAO.save(group.getName(), changeset, new SimpleCallback<Boolean>() {

            @Override
            public void onSuccess(Boolean wasSuccessful) {
                if (wasSuccessful) {
                    Console.info(Console.MESSAGES.modified(group.getName()));
                } else {
                    Console.info(Console.MESSAGES.modificationFailed(group.getName()));
                }

                loadServerGroup();
            }
        });

    }

    public void closeDialoge()
    {
        if(window!=null) window.hide();
    }

    public void onUpdateJvm(final String groupName, String jvmName, Map<String, Object> changedValues) {

        ModelNode address = new ModelNode();
        address.add("server-group", groupName);
        address.add("jvm", jvmName);

        UpdateJvmCmd cmd = new UpdateJvmCmd(dispatcher, factory, propertyMetaData, address);
        cmd.execute(changedValues, new SimpleCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                loadServerGroup();
            }
        });

    }

    public void onCreateJvm(final String groupName, Jvm jvm) {

        ModelNode address = new ModelNode();
        address.add("server-group", groupName);
        address.add("jvm", jvm.getName());

        CreateJvmCmd cmd = new CreateJvmCmd(dispatcher, factory, address);
        cmd.execute(jvm, new SimpleCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                loadServerGroup();
            }
        });

    }

    public void onDeleteJvm(final String groupName, Jvm jvm) {

        ModelNode address = new ModelNode();
        address.add("server-group", groupName);
        address.add("jvm", jvm.getName());

        DeleteJvmCmd cmd = new DeleteJvmCmd(dispatcher, factory, address);
        cmd.execute(new SimpleCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                loadServerGroup();
            }
        });
    }

    public void closePropertyDialoge() {
        propertyWindow.hide();
    }

    public void launchNewPropertyDialoge(String group) {

        propertyWindow = new DefaultWindow(Console.MESSAGES.createTitle("System Property"));
        propertyWindow.setWidth(480);
        propertyWindow.setHeight(360);

        propertyWindow.trapWidget(
                new NewPropertyWizard(this, group, true).asWidget()
        );

        propertyWindow.setGlassEnabled(true);
        propertyWindow.center();
    }

    public void onCreateProperty(final String groupName, final PropertyRecord prop)
    {
        if(propertyWindow!=null && propertyWindow.isShowing())
        {
            propertyWindow.hide();
        }

        ModelNode address = new ModelNode();
        address.add("server-group", groupName);
        address.add("system-property", prop.getKey());

        CreatePropertyCmd cmd = new CreatePropertyCmd(dispatcher, factory, address);
        cmd.execute(prop, new SimpleCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                loadServerGroup();
            }
        });
    }

    public void onDeleteProperty(final String groupName, final PropertyRecord prop)
    {
        ModelNode address = new ModelNode();
        address.add("server-group", groupName);
        address.add("system-property", prop.getKey());

        DeletePropertyCmd cmd = new DeletePropertyCmd(dispatcher,factory,address);
        cmd.execute(prop, new SimpleCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                loadServerGroup();
            }
        });
    }

    @Override
    public void onChangeProperty(String groupName, PropertyRecord prop) {
        // do nothing
    }

    public void loadJVMConfiguration(final ServerGroupRecord group) {
        serverGroupDAO.loadJVMConfiguration(group, new SimpleCallback<Jvm>() {
            @Override
            public void onSuccess(Jvm jvm) {
                getView().setJvm(group, jvm);
            }
        });
    }

    public void loadProperties(final ServerGroupRecord group) {
        serverGroupDAO.loadProperties(group, new SimpleCallback<List<PropertyRecord>>() {
            @Override
            public void onSuccess(List<PropertyRecord> properties) {
                getView().setProperties(group, properties);
            }
        });
    }

}
