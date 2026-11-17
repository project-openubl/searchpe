package io.github.projectopenubl.xhandler.ose;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

/** The OSE's SOAP channel at SUNAT's own path, for clients that keep it (only the host changes). */
@Path("/ol-ti-itcpfegem/billService")
public class OseSoapSunatPath {

    private final OseClients clients;
    private final OseIntake intake;
    private final io.micrometer.core.instrument.MeterRegistry metrics;

    public OseSoapSunatPath(OseClients clients, OseIntake intake, io.micrometer.core.instrument.MeterRegistry metrics) {
        this.clients = clients;
        this.intake = intake;
        this.metrics = metrics;
    }

    @POST
    @Consumes({"text/xml", "application/soap+xml", "application/xml"})
    @Produces("text/xml")
    public Response handle(byte[] body) {
        return OseSoapResource.answer(clients, intake, metrics, body);
    }
}
