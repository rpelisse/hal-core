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

package org.jboss.as.console.client.domain.hosts.general;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.NameToken;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.proxy.Place;
import com.gwtplatform.mvp.client.proxy.ProxyPlace;
import com.gwtplatform.mvp.client.proxy.RevealContentEvent;
import com.gwtplatform.mvp.shared.proxy.PlaceRequest;
import org.jboss.as.console.client.core.CircuitPresenter;
import org.jboss.as.console.client.core.MainLayoutPresenter;
import org.jboss.as.console.client.core.NameTokens;
import org.jboss.as.console.client.domain.model.SimpleCallback;
import org.jboss.as.console.client.shared.BeanFactory;
import org.jboss.as.console.client.shared.properties.CreatePropertyCmd;
import org.jboss.as.console.client.shared.properties.DeletePropertyCmd;
import org.jboss.as.console.client.shared.properties.LoadPropertiesCmd;
import org.jboss.as.console.client.shared.properties.NewPropertyWizard;
import org.jboss.as.console.client.shared.properties.PropertyManagement;
import org.jboss.as.console.client.shared.properties.PropertyRecord;
import org.jboss.as.console.client.v3.dmr.AddressTemplate;
import org.jboss.as.console.client.v3.stores.domain.HostStore;
import org.jboss.as.console.mbui.behaviour.CoreGUIContext;
import org.jboss.as.console.spi.AccessControl;
import org.jboss.as.console.spi.OperationMode;
import org.jboss.as.console.spi.SearchIndex;
import org.jboss.ballroom.client.rbac.SecurityContextChangedEvent;
import org.jboss.ballroom.client.widgets.window.DefaultWindow;
import org.jboss.dmr.client.ModelNode;
import org.jboss.dmr.client.dispatch.DispatchAsync;
import org.jboss.gwt.circuit.Action;
import org.jboss.gwt.circuit.Dispatcher;

import java.util.List;

import static org.jboss.as.console.spi.OperationMode.Mode.DOMAIN;

/**
 * @author Heiko Braun
 * @date 5/17/11
 */
public class HostPropertiesPresenter extends CircuitPresenter<HostPropertiesPresenter.MyView, HostPropertiesPresenter.MyProxy>
        implements PropertyManagement {

    private DefaultWindow window;

    @ProxyCodeSplit
    @NameToken(NameTokens.HostPropertiesPresenter)
    @OperationMode(DOMAIN)
    @SearchIndex(keywords = {"system-property", "property"})
    @AccessControl(resources = {"/{selected.host}/system-property=*",})
    public interface MyProxy extends ProxyPlace<HostPropertiesPresenter>, Place {}


    public interface MyView extends View {
        void setPresenter(HostPropertiesPresenter presenter);
        void setProperties(List<PropertyRecord> properties);
    }


    private final DispatchAsync dispatcher;
    private final HostStore hostStore;
    private final BeanFactory factory;
    private final CoreGUIContext statementContext;
    private DefaultWindow propertyWindow;

    @Inject
    public HostPropertiesPresenter(EventBus eventBus, MyView view, MyProxy proxy, Dispatcher circuit,
                                   DispatchAsync dispatcher,BeanFactory factory, HostStore hostStore, CoreGUIContext statementContext) {
        super(eventBus, view, proxy, circuit);

        this.dispatcher = dispatcher;
        this.hostStore = hostStore;
        this.factory = factory;

        this.statementContext = statementContext;
    }

    @Override
    protected void onBind() {
        super.onBind();
        getView().setPresenter(this);
        addChangeHandler(hostStore);

    }

    @Override
    protected void onAction(Action action) {

        Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
            @Override
            public void execute() {
                loadProperties();
            }
        });
    }

    @Override
    public boolean useManualReveal() {
        return true;
    }

    @Override
    protected void revealInParent() {
        RevealContentEvent.fire(this, MainLayoutPresenter.TYPE_MainContent, this);
    }

    @Override
    protected void onReset() {
        super.onReset();

        Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
            @Override
            public void execute() {
                loadProperties();
            }
        });

    }

    @Override
    public void prepareFromRequest(PlaceRequest request) {
        SecurityContextChangedEvent.AddressResolver resolver = new SecurityContextChangedEvent.AddressResolver<AddressTemplate>() {
            @Override
            public String resolve(AddressTemplate template) {
                String resolved = template.resolveAsKey(statementContext);
                return resolved;
            }
        };

        Command cmd = () -> getProxy().manualReveal(HostPropertiesPresenter.this);

        // RBAC: context change propagation
        SecurityContextChangedEvent.fire(
                HostPropertiesPresenter.this,
                cmd,
                resolver
        );
    }

    private PlaceRequest hostPlaceRequest() {
        return new PlaceRequest.Builder().nameToken(getProxy().getNameToken())
                .with("host", hostStore.getSelectedHost()).build();
    }

    private void loadProperties() {
        ModelNode address = new ModelNode();
        address.add("host", hostStore.getSelectedHost());
        LoadPropertiesCmd loadPropCmd = new LoadPropertiesCmd(dispatcher, factory, address);
        loadPropCmd.execute(new SimpleCallback<List<PropertyRecord>>() {
            @Override
            public void onSuccess(List<PropertyRecord> result) {
                getView().setProperties(result);
            }
        });
    }

    public void closePropertyDialoge() {
        propertyWindow.hide();
    }

    public void launchNewPropertyDialoge(String group) {

        propertyWindow = new DefaultWindow("New Host Property");
        propertyWindow.setWidth(480);
        propertyWindow.setHeight(360);
        propertyWindow.addCloseHandler(new CloseHandler<PopupPanel>() {
            @Override
            public void onClose(CloseEvent<PopupPanel> event) {

            }
        });

        propertyWindow.trapWidget(
                new NewPropertyWizard(this, group, true).asWidget()
        );

        propertyWindow.setGlassEnabled(true);
        propertyWindow.center();
    }

    public void onCreateProperty(final String groupName, final PropertyRecord prop) {

        if (propertyWindow != null && propertyWindow.isShowing()) {
            propertyWindow.hide();
        }

        ModelNode address = new ModelNode();
        address.add("host", hostStore.getSelectedHost());
        address.add("system-property", prop.getKey());

        CreatePropertyCmd cmd = new CreatePropertyCmd(dispatcher, factory, address);
        cmd.execute(prop, new SimpleCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                loadProperties();
            }
        });


    }

    public void onDeleteProperty(final String groupName, final PropertyRecord prop) {
        ModelNode address = new ModelNode();
        address.add("host", hostStore.getSelectedHost());
        address.add("system-property", prop.getKey());

        DeletePropertyCmd cmd = new DeletePropertyCmd(dispatcher, factory, address);
        cmd.execute(prop, new SimpleCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                loadProperties();
            }
        });
    }

    @Override
    public void onChangeProperty(String groupName, PropertyRecord prop) {
        // do nothing
    }
}
