package org.jboss.as.console.client.tools;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.PopupView;
import com.gwtplatform.mvp.client.PresenterWidget;
import org.jboss.as.console.client.Console;
import org.jboss.as.console.client.core.ReadRequiredResources;
import org.jboss.as.console.client.core.RequiredResourcesContext;
import org.jboss.as.console.client.domain.model.SimpleCallback;
import org.jboss.as.console.client.rbac.SecurityContextImpl;
import org.jboss.as.console.client.shared.util.LRUCache;
import org.jboss.as.console.client.v3.ResourceDescriptionRegistry;
import org.jboss.as.console.client.v3.dmr.AddressTemplate;
import org.jboss.as.console.client.v3.dmr.ResourceDescription;
import org.jboss.as.console.mbui.behaviour.CoreGUIContext;
import org.jboss.as.console.mbui.behaviour.ModelNodeAdapter;
import org.jboss.as.console.mbui.widgets.AddressUtils;
import org.jboss.ballroom.client.rbac.SecurityContext;
import org.jboss.ballroom.client.widgets.window.Feedback;
import org.jboss.dmr.client.ModelNode;
import org.jboss.dmr.client.dispatch.DispatchAsync;
import org.jboss.dmr.client.dispatch.impl.DMRAction;
import org.jboss.dmr.client.dispatch.impl.DMRResponse;
import org.jboss.gwt.flow.client.Async;
import org.jboss.gwt.flow.client.Control;
import org.jboss.gwt.flow.client.Function;
import org.jboss.gwt.flow.client.Outcome;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.jboss.dmr.client.ModelDescriptionConstants.*;

/**
 * @author Heiko Braun
 * @date 6/15/12
 */
public class BrowserPresenter extends PresenterWidget<BrowserPresenter.MyView>{

    private static final ModelNode ROOT = new ModelNode().setEmptyList();
    private DispatchAsync dispatcher;
    private final CoreGUIContext statementContext;
    private boolean hasBeenRevealed;
    private ModelNode pinToAddress = null;

    private LRUCache<AddressTemplate, SecurityContext> contextCache = new LRUCache<AddressTemplate, SecurityContext>(25);
    final ResourceDescriptionRegistry resourceDescriptionRegistry = new ResourceDescriptionRegistry();

    public interface MyView extends PopupView {
        void setPresenter(BrowserPresenter presenter);
        void updateRootTypes(ModelNode address, List<ModelNode> modelNodes);
        void updateChildrenTypes(ModelNode address, List<ModelNode> modelNodes);
        void updateChildrenNames(ModelNode address, List<ModelNode> modelNodes);
        void updateResource(ModelNode address, SecurityContext securityContext, ModelNode description, ModelNode resource);

        void showAddDialog(ModelNode address, boolean isSingleton, SecurityContext securityContext, ModelNode desc);
    }

    @Inject
    public BrowserPresenter(
            EventBus eventBus, MyView view,
            DispatchAsync dispatcher, CoreGUIContext statementContext) {
        super(eventBus, view);

        this.dispatcher = dispatcher;
        this.statementContext = statementContext;
    }

    @Override
    protected void onBind() {
        super.onBind();
        getView().setPresenter(this);
    }

    @Override
    protected void onReveal() {
        if(!hasBeenRevealed)
        {
            hasBeenRevealed = true;
            onRefresh();
        }
    }

    @Override
    protected void onHide() {
        super.onHide();
        Console.getPlaceManager().revealRelativePlace(-1);
    }

    public void onRefresh() {

        ModelNode target = pinToAddress != null ? pinToAddress : ROOT;

        readChildrenTypes(target, true);
        readResource(target, false);
    }

    class DMRContext {
        ModelNode response;
        boolean flagSquatting;
    }

    public void readChildrenTypes(final ModelNode address, final boolean resetRoot) {

        Function<DMRContext> fn = new Function<DMRContext>() {
            @Override
            public void execute(final Control<DMRContext> control) {

                // read children types
                ModelNode childTypeOp  = new ModelNode();
                childTypeOp.get(ADDRESS).set(address);
                childTypeOp.get(OP).set(READ_CHILDREN_TYPES_OPERATION);
                childTypeOp.get(INCLUDE_SINGLETONS).set(true);

                dispatcher.execute(new DMRAction(childTypeOp), new SimpleCallback<DMRResponse>() {
                    @Override
                    public void onSuccess(DMRResponse dmrResponse) {
                        ModelNode dmrRsp = dmrResponse.get();
                        control.getContext().response = dmrRsp;

                        if(dmrRsp.isFailure())
                            control.abort();
                        else
                            control.proceed();
                    }

                    @Override
                    public void onFailure(Throwable caught) {
                        Console.error("Failed to load children types: "+ caught.getMessage());
                        control.abort();
                    }
                });
            }
        };

        new Async(BrowserView.PROGRESS_ELEMENT).waterfall(new DMRContext(), new Outcome<DMRContext>() {
            @Override
            public void onFailure(DMRContext context) {
                Console.error("Failed ot load children types: "+context.response.getFailureDescription());
            }

            @Override
            public void onSuccess(DMRContext context) {
                ModelNode response = context.response;
                if(resetRoot)
                    getView().updateRootTypes(address, response.get(RESULT).asList());
                else
                    getView().updateChildrenTypes(address, response.get(RESULT).asList());
            }
        }, fn);
    }

    public void readChildrenNames(final ModelNode address) {

        final List<ModelNode> addressList = address.asList();
        final List<ModelNode> actualAddress = new ArrayList<ModelNode>();

        ModelNode typeDenominator = null;
        int i=0;
        for(ModelNode path : addressList)
        {
            if(i<addressList.size()-1)
                actualAddress.add(path);
            else
                typeDenominator = path;

            i++;
        }

        final String typeName = typeDenominator.asProperty().getName();

        Function<DMRContext> childNameFn = new Function<DMRContext>() {
            @Override
            public void execute(final Control<DMRContext> control) {

                ModelNode operation  = new ModelNode();
                operation.get(ADDRESS).set(actualAddress);
                operation.get(OP).set(READ_CHILDREN_NAMES_OPERATION);
                operation.get(CHILD_TYPE).set(typeName);

                dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {

                    @Override
                    public void onFailure(Throwable caught) {
                        Console.error("Failed to load child names: "+caught.getMessage());
                        control.abort();
                    }

                    @Override
                    public void onSuccess(DMRResponse dmrResponse) {
                        ModelNode dmrRsp = dmrResponse.get();
                        control.getContext().response = dmrRsp;

                        if(dmrRsp.isFailure())
                        {
                            control.abort();
                        }
                        else
                        {
                            control.proceed();
                        }
                    }
                });

            }
        };

        new Async(BrowserView.PROGRESS_ELEMENT).waterfall(new DMRContext(), new Outcome<DMRContext>() {
            @Override
            public void onFailure(DMRContext context) {
                Console.error("Failed to load children names: "+ context.response.getFailureDescription());
            }

            @Override
            public void onSuccess(DMRContext context) {
                ModelNode response = context.response;
                getView().updateChildrenNames(address, response.get(RESULT).asList());
            }
        }, childNameFn);

    }

    class ResourceData {
        private final boolean isTransient;
        SecurityContext securityContext;
        ModelNode description;
        ModelNode data;

        public ResourceData(boolean isTransient) {
            this.isTransient = isTransient;
        }
    }



    public void readResource(final ModelNode modelNode, boolean isPlaceHolder) {

        _loadMetaData(modelNode, new ResourceData(isPlaceHolder), new Outcome<ResourceData>() {
            @Override
            public void onFailure(ResourceData context) {

            }

            @Override
            public void onSuccess(ResourceData context) {
                getView().updateResource(modelNode, context.securityContext, context.description, context.data);
            }
        });

    }

    private void _loadMetaData(ModelNode modelNodeAddress, ResourceData resourceData, Outcome<ResourceData> delegate) {

        AddressTemplate address = AddressTemplate.of(AddressUtils.toString(modelNodeAddress, true));
        String token = "token"; // not used, it's a single request

        Function<ResourceData> metaFn = new Function<ResourceData>() {
            @Override
            public void execute(final Control<ResourceData> control) {

                final Set<AddressTemplate> resources = new HashSet<AddressTemplate>();
                resources.add(address);

                if(contextCache.containsKey(address))
                {
                    control.getContext().securityContext = contextCache.get(address);
                    control.getContext().description = resourceDescriptionRegistry.lookup(address);
                    control.proceed();
                }
                else {

                    // we delegate to an existing instance that does the parsing
                    // TOOD: the API is pretty awkward and the parser should be extracted so it can be used standalone,
                    // without the dependency on the Flow framework

                    final RequiredResourcesContext ctx = new RequiredResourcesContext(token);
                    final ReadRequiredResources operation = new ReadRequiredResources(dispatcher, statementContext);
                    operation.add(address, false);

                    operation.execute(
                            new Control<RequiredResourcesContext>() {
                                @Override
                                public void proceed() {

                                    // create a new security context
                                    SecurityContextImpl securityContext = new SecurityContextImpl(ctx.getToken(), resources);
                                    ctx.mergeWith(securityContext);

                                    contextCache.put(address, securityContext);
                                    control.getContext().securityContext = securityContext;

                                    ResourceDescription description = ctx.getDescriptions().get(address);

                                    // the registry acts as cache in this case, see above
                                    resourceDescriptionRegistry.add(address, description);
                                    control.getContext().description = description;
                                    System.out.println("Loaded data for " +  address);
                                    control.proceed();
                                }

                                @Override
                                public void abort() {
                                    Console.error("Failed to create security context for " + address, ctx.getError().getMessage());
                                    control.abort();
                                }

                                @Override
                                public RequiredResourcesContext getContext() {
                                    return ctx;
                                }
                            }
                    );
                }

            }
        };

        Function<ResourceData> dataFn = new Function<ResourceData>() {
            @Override
            public void execute(final Control<ResourceData> control) {

                if(!control.getContext().isTransient) {
                    _readResouce(modelNodeAddress, new SimpleCallback<ModelNode>() {

                        @Override
                        public void onFailure(Throwable caught) {
                            Console.error("Failed to read resource: " + caught.getMessage());
                            control.abort();
                        }

                        @Override
                        public void onSuccess(ModelNode result) {
                            control.getContext().data = result;
                            control.proceed();
                        }
                    });
                }
                else
                {
                    control.getContext().data = new ModelNode(); // no data
                    control.proceed();
                }
            }
        };


        new Async(BrowserView.PROGRESS_ELEMENT).waterfall(resourceData, delegate, metaFn, dataFn);

    }

    private void _readResouce(ModelNode address, final AsyncCallback<ModelNode> callback) {

        // the actual values
        final ModelNode resourceOp  = new ModelNode();
        resourceOp.get(ADDRESS).set(address);
        resourceOp.get(OP).set(READ_RESOURCE_OPERATION);
        resourceOp.get(INCLUDE_RUNTIME).set(true);

        dispatcher.execute(new DMRAction(resourceOp), new SimpleCallback<DMRResponse>() {

                    @Override
                    public void onFailure(Throwable caught) {
                        callback.onFailure(caught);
                    }

                    @Override
                    public void onSuccess(DMRResponse dmrResponse) {

                        final ModelNode response = dmrResponse.get();

                        if(response.isFailure())
                        {
                            callback.onFailure(new RuntimeException("Failed to load resource: "+response.getFailureDescription()));
                        }
                        else
                        {

                            ModelNode resourceData = response.get(RESULT).asObject();
                            callback.onSuccess(resourceData);
                        }
                    }
                }
        );
    }

    public void onSaveResource(final ModelNode address, Map<String, Object> changeset) {

        final ModelNodeAdapter adapter = new ModelNodeAdapter();

        ModelNode operation = adapter.fromChangeset(changeset, address);

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {
            @Override
            public void onSuccess(DMRResponse dmrResponse) {
                ModelNode response = dmrResponse.get();

                if (response.isFailure()) {
                    Console.error("Failed to save resource " + address.asString(), response.getFailureDescription());
                }
                else {
                    Console.info("Successfully saved resource " + address.asString());
                    readResource(address, false); // refresh
                }

            }
        });


    }

    /**
     * Creates a permanent selection for a subtree
     * @param address
     */
    public void onPinTreeSelection(ModelNode address) {
        this.pinToAddress = address;
        onRefresh();
    }

    /**
     * Remove a child resource
     * @param address
     * @param selection
     */
    public void onRemoveChildResource(final ModelNode address, final ModelNode selection) {

        final ModelNode fqAddress = AddressUtils.toFqAddress(address, selection.asString());

        final ModelNode operation = new ModelNode();
        operation.get(OP).set(REMOVE);
        operation.get(ADDRESS).set(fqAddress);

        Feedback.confirm(
                "Remove Resource",
                "Do you really want to remove resource "+fqAddress.toString(),
                new Feedback.ConfirmationHandler() {
                    @Override
                    public void onConfirmation(boolean isConfirmed) {

                        if(isConfirmed) {

                            dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {
                                @Override
                                public void onSuccess(DMRResponse dmrResponse) {
                                    ModelNode response = dmrResponse.get();

                                    if (response.isFailure()) {
                                        Console.error("Failed to remove resource " + fqAddress.asString(), response.getFailureDescription());
                                    } else {
                                        Console.info("Successfully removed resource " + fqAddress.asString());
                                        readChildrenNames(address);
                                    }

                                }
                            });
                        }
                    }
                }
        );
    }

    /**
     * Add a child resource
     * @param address
     * @param isSingleton
     */
    public void onPrepareAddChildResource(final ModelNode address, final boolean isSingleton) {


        _loadMetaData(address, new ResourceData(true), new Outcome<ResourceData>() {
                    @Override
                    public void onFailure(ResourceData context) {

                    }

                    @Override
                    public void onSuccess(ResourceData context) {
                        getView().showAddDialog(address, isSingleton, context.securityContext, context.description);
                    }
                }
        );

    }

    public void onAddChildResource(final ModelNode address, ModelNode resource) {

        final ModelNode fqAddress = AddressUtils.toFqAddress(address, resource.get("name").asString());

        resource.get(OP).set(ADD);
        resource.get(ADDRESS).set(fqAddress);

        dispatcher.execute(new DMRAction(resource), new SimpleCallback<DMRResponse>() {
            @Override
            public void onSuccess(DMRResponse dmrResponse) {
                ModelNode response = dmrResponse.get();

                if (response.isFailure()) {
                    Console.error("Failed to add resource " + fqAddress.asString(),
                            response.getFailureDescription());
                } else {
                    readChildrenNames(AddressUtils.fromFqAddress(address));
                }

            }
        });
    }

}
