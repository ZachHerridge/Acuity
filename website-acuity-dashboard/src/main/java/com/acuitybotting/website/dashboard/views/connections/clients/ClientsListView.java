package com.acuitybotting.website.dashboard.views.connections.clients;

import com.acuitybotting.db.arango.acuity.rabbit_db.domain.MapRabbitDocument;
import com.acuitybotting.db.arango.acuity.rabbit_db.repository.RabbitDocumentRepository;
import com.acuitybotting.website.dashboard.DashboardRabbitService;
import com.acuitybotting.website.dashboard.components.general.list_display.InteractiveList;
import com.acuitybotting.website.dashboard.security.view.interfaces.UsersOnly;
import com.acuitybotting.website.dashboard.views.RootLayout;
import com.acuitybotting.website.dashboard.views.connections.ConnectionsTabNavComponent;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

import java.util.stream.Collectors;

/**
 * Created by Zachary Herridge on 8/8/2018.
 */
@Route(value = "connections/clients", layout = RootLayout.class)
public class ClientsListView extends VerticalLayout implements UsersOnly {

    private ClientListComponent clientListComponent;

    public ClientsListView(ClientListComponent clientListComponent, ConnectionsTabNavComponent connectionsTabNavComponent) {
        this.clientListComponent = clientListComponent;
        setPadding(false);
        add(connectionsTabNavComponent, clientListComponent);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        clientListComponent.load();
    }

    @SpringComponent
    @UIScope
    private static class ClientListComponent extends InteractiveList<MapRabbitDocument> {

        public ClientListComponent(RabbitDocumentRepository documentRepository, DashboardRabbitService rabbitService) {
            withColumn("ID", "33%", document -> new Span(), (document, span) -> span.setText(document.getSubKey()));
            withColumn("Host", "33%", document -> new Span(), (document, span) -> span.setText(String.valueOf(document.getHeaders().getOrDefault("peerHost", ""))));
            withColumn("Last Update", "33%", document -> new Span(), (document, span) -> span.setText(String.valueOf(document.getHeaders().getOrDefault("connectionConfirmationTime", ""))));
            withLoad(
                    MapRabbitDocument::getSubKey,
                    () -> documentRepository.findAllByPrincipalIdAndDatabaseAndSubGroup(UsersOnly.getCurrentPrincipalUid(), "services.registered-connections", "connections")
                            .stream()
                            .filter(connection -> connection.getSubKey().startsWith("RPC_") && (boolean) connection.getHeaders().getOrDefault("connected", false))
                            .collect(Collectors.toSet())
            );
        }
    }
}
