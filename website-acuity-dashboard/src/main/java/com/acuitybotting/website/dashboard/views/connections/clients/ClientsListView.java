package com.acuitybotting.website.dashboard.views.connections.clients;

import com.acuitybotting.website.dashboard.security.view.interfaces.UsersOnly;
import com.acuitybotting.website.dashboard.views.RootLayout;
import com.acuitybotting.website.dashboard.views.connections.ConnectionsTabNavComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

/**
 * Created by Zachary Herridge on 8/8/2018.
 */
@Route(value = "connections/clients", layout = RootLayout.class)
public class ClientsListView extends VerticalLayout implements UsersOnly {

    public ClientsListView(ConnectionsTabNavComponent connectionsTabNavComponent) {
        add(connectionsTabNavComponent);
    }

}