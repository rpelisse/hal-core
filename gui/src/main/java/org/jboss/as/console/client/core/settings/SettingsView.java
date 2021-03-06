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

package org.jboss.as.console.client.core.settings;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.PopupViewImpl;
import org.jboss.as.console.client.Console;
import org.jboss.as.console.client.ProductConfig;
import org.jboss.as.console.client.core.FeatureSet;
import org.jboss.as.console.client.search.Index;
import org.jboss.as.console.client.shared.Preferences;
import org.jboss.as.console.client.shared.help.StaticHelpPanel;
import org.jboss.ballroom.client.widgets.forms.*;
import org.jboss.ballroom.client.widgets.window.DefaultWindow;
import org.jboss.ballroom.client.widgets.window.DialogueOptions;
import org.jboss.ballroom.client.widgets.window.Feedback;
import org.jboss.ballroom.client.widgets.window.WindowContentBuilder;

import java.util.ArrayList;
import java.util.List;


/**
 * @author Heiko Braun
 * @date 5/3/11
 */
public class SettingsView extends PopupViewImpl implements SettingsPresenterWidget.MyView {

    private DefaultWindow window;
    private SettingsPresenterWidget presenter;
    private Form<CommonSettings> form;
    private ButtonItem clear;
    private boolean clearDisabled = false;

    @Inject
    public SettingsView(EventBus eventBus, ProductConfig productConfig, FeatureSet featureSet, final Index index) {
        super(eventBus);

        window = new DefaultWindow(Console.CONSTANTS.common_label_settings());
        VerticalPanel layout = new VerticalPanel();
        layout.setStyleName("window-content");

        form = new Form<CommonSettings>(CommonSettings.class);
        List<FormItem> fields = new ArrayList<>();

        ComboBoxItem localeItem = null;
        List<String> locales = productConfig.getLocales();
        if (locales.size() > 1) {
            localeItem = new ComboBoxItem(Preferences.Key.LOCALE.getToken(),
                    Preferences.Key.LOCALE.getTitle());
            localeItem.setDefaultToFirstOption(true);
            localeItem.setValueMap(locales);
            fields.add(localeItem);
        }

        //CheckBoxItem useCache = new CheckBoxItem(Preferences.Key.USE_CACHE.getToken(), Preferences.Key.USE_CACHE.getTitle());

        CheckBoxItem enableAnalytics = new CheckBoxItem(Preferences.Key.ANALYTICS.getToken(),
                Preferences.Key.ANALYTICS.getTitle());
        fields.add(enableAnalytics);

        if (featureSet.isSearchEnabled()) {
            clear = new ButtonItem("clear-search-index", Console.CONSTANTS.search_index_reset(),
                    Console.CONSTANTS.common_label_reset());
            clear.addClickHandler(new ClickHandler() {
                @Override
                public void onClick(ClickEvent event) {
                    clear.setEnabled(false);
                    clearDisabled = true;
                }
            });
            fields.add(clear);
        }

        form.setFields(fields.toArray(new FormItem[fields.size()]));

        CheckBoxItem enableSecurityContextCache = new CheckBoxItem(Preferences.Key.SECURITY_CONTEXT.getToken(),
                Preferences.Key.SECURITY_CONTEXT.getTitle());

        //form.include(localeItem, enableAnalytics, enableSecurityContextCache);

        Widget formWidget = form.asWidget();
        formWidget.getElement().setAttribute("style", "margin:15px");

        DialogueOptions options = new DialogueOptions(
                Console.CONSTANTS.common_label_save(),
                new ClickHandler() {
                    @Override
                    public void onClick(ClickEvent event) {

                        presenter.hideView();

                        Feedback.confirm(Console.MESSAGES.restartRequired(), Console.MESSAGES.restartRequiredConfirm(),
                                new Feedback.ConfirmationHandler() {
                                    @Override
                                    public void onConfirmation(boolean isConfirmed) {


                                        if(isConfirmed){

                                            // search index
                                            if(clear!=null && clearDisabled==true)
                                            {
                                                index.reset();
                                                //Console.info(Console.CONSTANTS.search_index_reset_finished());
                                            }

                                            presenter.onSaveDialogue(form.getUpdatedEntity());

                                            Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
                                                @Override
                                                public void execute() {
                                                    reload();
                                                }
                                            });

                                        }
                                    }
                                }
                        );
                    }
                },
                Console.CONSTANTS.common_label_cancel(),
                new ClickHandler() {
                    @Override
                    public void onClick(ClickEvent event) {
                        form.cancel();
                        presenter.onCancelDialogue();
                    }
                }
        );

        options.getElement().setAttribute("style", "padding:10px");

        // TODO I18N
        SafeHtmlBuilder html = new SafeHtmlBuilder();
        html.appendHtmlConstant("<ul>");
        if (localeItem != null) {
            html.appendHtmlConstant("<li>").appendEscaped("Locale: The user interface language.").appendHtmlConstant(
                    "</li>");
        }
        html.appendHtmlConstant("<li>");
        html.appendEscaped(
                "Enable Usage Data Collection: The Admin Console has the capability to collect usage data via ");
        html.appendHtmlConstant("<a href=\"http://www.google.com/analytics/\" target=\"_blank\">Google Analytics</a>");
        html.appendEscaped(
                ". This data will be used exclusively by Red Hat to improve the console in future releases. By default this data collection is ");
        if (productConfig.getProfile() == ProductConfig.Profile.COMMUNITY) {
            html.appendEscaped("enabled, but you can disable collection of this data by unchecking the Enable Usage Data Collection box.");
        } else {
            html.appendEscaped("disabled, but you can enable collection of this data by checking the Enable Usage Data Collection box.");
        }
        html.appendHtmlConstant("</li>");
        if (featureSet.isSearchEnabled()) {
            html.appendHtmlConstant("<li>").
                    appendEscaped("Clear Search Index: Removes the local search index in case the search does not work as expected. The index is re-generated automatically the next time you'll enter the search.").
                    appendHtmlConstant("</li>");
        }
        //html.appendHtmlConstant("<li>").appendEscaped("Security Cache: If disabled the security context will be re-created everytime you access a dialog (performance hit).");
        html.appendHtmlConstant("</ul>");
        StaticHelpPanel help = new StaticHelpPanel(html.toSafeHtml());
        layout.add(help.asWidget());
        layout.add(form.asWidget());

        window.setWidth(480);
        window.setHeight(360);
        window.trapWidget(new WindowContentBuilder(layout, options).build());
        window.setGlassEnabled(true);
        window.center();
    }

    /*private void onCenter() {
        form.edit(presenter.getCommonSettings());
    } */

    @Override
    public Widget asWidget() {
//        form.edit(presenter.getCommonSettings());
        return window;
    }

    @Override
    public void show() {
        super.show();
        form.edit(presenter.getCommonSettings());
        if(clear!=null) {
            clear.setEnabled(true);
            clearDisabled = false;
        }
    }

    @Override
    public void setPresenter(SettingsPresenterWidget presenter) {
        this.presenter = presenter;
    }

    public static native JavaScriptObject reload() /*-{
        $wnd.location.reload();
    }-*/;
}
