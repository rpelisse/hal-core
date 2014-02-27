/*
 * JBoss, Home of Professional Open Source
 * Copyright 2013 Red Hat Inc. and/or its affiliates and other contributors
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
package org.jboss.as.console.client.shared.patching.ui;

import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;

/**
* @author Harald Pehl
*/
public interface PatchManagementTemplates extends SafeHtmlTemplates {

    @Template("<div class=\"patch-success-panel\"><i class=\"icon-ok icon-large\"></i> {0}</div>")
    SafeHtml successPanel(String message);

    @Template("<div class=\"patch-error-panel\"><i class=\"icon-exclamation-sign icon-large\"></i> {0}</div>")
    SafeHtml errorPanel(String message);

    @Template("<ul class=\"patch-actions\">" +
            "<li><div class=\"title\">{0}</div><div class=\"body\">{1}</div></li>" +
            "<li><div class=\"title\">{2}</div><div class=\"body\">{3}</div></li>" +
            "</ul>")
    SafeHtml stopServers(String firstTitle, String firstBody, String secondTitle, String secondBody);

    // This version takes a SafeHtml instance as 3rd parameter
    @Template("<ul class=\"patch-actions\">" +
            "<li><div class=\"title\">{0}</div><div class=\"body\">{1}</div></li>" +
            "<li><div class=\"title\">{2}</div><div class=\"body\">{3}</div><div id=\"patch-conflict-override\"></div></li>" +
            "</ul>")
    SafeHtml patchConflicts(String cancelTitle, String cancelBody, SafeHtml overrideTitle, String overrideBody);

    // This version takes a SafeHtml instance as 3rd parameter
    @Template("<ul class=\"patch-actions\">" +
            "<li><div class=\"title\">{0}</div><div class=\"body\">{1}</div></li>" +
            "<li><div class=\"title\">{2}</div><div class=\"body\">{3}</div><div id=\"select-different-patch\"></div></li>" +
            "</ul>")
    SafeHtml appliedFailed(String cancelTitle, String cancelBody, String overrideTitle, String overrideBody);
}