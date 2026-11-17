package io.github.projectopenubl.xhandler.ose;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jboss.logging.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The OSE's SOAP channel (DESIGN.md §5, §11.4; RS 117-2017 Annex B): compatible with SUNAT's
 * {@code billService} — {@code sendBill}, {@code sendSummary}, {@code sendPack}, {@code getStatus},
 * {@code getStatusCdr} — with WS-Security UsernameToken, so an issuer's system switches by changing
 * only the URL. Served at {@code /ose/billService} and at SUNAT's path. The envelope is parsed
 * with DTDs and external entities disabled.
 */
@Path("/ose/billService")
public class OseSoapResource {

    static final int MAX_ENVELOPE = 15 * 1024 * 1024;
    private static final Logger LOG = Logger.getLogger(OseSoapResource.class);

    private final OseClients clients;
    private final OseIntake intake;
    private final io.micrometer.core.instrument.MeterRegistry metrics;

    public OseSoapResource(OseClients clients, OseIntake intake, io.micrometer.core.instrument.MeterRegistry metrics) {
        this.clients = clients;
        this.intake = intake;
        this.metrics = metrics;
    }

    @POST
    @Consumes({"text/xml", "application/soap+xml", "application/xml"})
    @Produces("text/xml")
    public Response handle(byte[] body) {
        return answer(clients, intake, metrics, body);
    }

    record Request(String operation, String username, String password, Map<String, String> fields) {
        String field(String name) {
            return fields.getOrDefault(name, "");
        }
    }

    static Response answer(OseClients clients, OseIntake intake, io.micrometer.core.instrument.MeterRegistry metrics, byte[] body) {
        Response response = answer(clients, intake, body);
        if (response.getStatus() == 500) {
            String xml = new String((byte[]) response.getEntity(), java.nio.charset.StandardCharsets.UTF_8);
            int at = xml.indexOf("Client.");
            String code = at < 0 ? "?" : xml.substring(at + 7, Math.min(xml.length(), at + 11));
            metrics.counter("xhandler.ose.faults", "code", code).increment();
        }
        return response;
    }

    static Response answer(OseClients clients, OseIntake intake, byte[] body) {
        try {
            if (body == null || body.length > MAX_ENVELOPE) {
                throw new OseIntake.Fault("0155", "El mensaje es demasiado grande.");
            }
            Request request = parse(body).orElseThrow(() -> new OseIntake.Fault("0306", "No se puede leer (parsear) el mensaje SOAP."));
            OseClients.Client client = clients.authenticate(request.username(), request.password());
            return switch (request.operation()) {
                case "sendBill" -> ok("sendBillResponse", "<applicationResponse>"
                        + Base64.getEncoder().encodeToString(intake.sendBill(client, "soap", request.field("fileName"), content(request)).zip())
                        + "</applicationResponse>");
                case "sendSummary" -> ok("sendSummaryResponse",
                        "<ticket>" + intake.sendSummary(client, "soap", request.field("fileName"), content(request)) + "</ticket>");
                case "sendPack" -> ok("sendPackResponse",
                        "<ticket>" + intake.sendPack(client, "soap", request.field("fileName"), content(request)) + "</ticket>");
                case "getStatus" -> {
                    OseIntake.Status status = intake.getStatus(client, request.field("ticket"));
                    yield ok("getStatusResponse", "<status><statusCode>" + status.statusCode() + "</statusCode><content>"
                            + Base64.getEncoder().encodeToString(status.content()) + "</content></status>");
                }
                case "getStatusCdr" -> {
                    long numero;
                    try {
                        numero = Long.parseLong(request.field("numeroComprobante").strip());
                    } catch (NumberFormatException e) {
                        throw new OseIntake.Fault("0306", "Número de comprobante inválido.");
                    }
                    yield intake.cdrOf(client, request.field("rucComprobante").strip(), request.field("tipoComprobante").strip(),
                                    request.field("serieComprobante").strip().toUpperCase(), numero)
                            .map(cdr -> ok("getStatusCdrResponse", "<statusCdr><statusCode>0004</statusCode><statusMessage>La constancia existe"
                                    + "</statusMessage><content>" + Base64.getEncoder().encodeToString(cdr.zip()) + "</content></statusCdr>"))
                            .orElseGet(() -> ok("getStatusCdrResponse",
                                    "<statusCdr><statusCode>0011</statusCode><statusMessage>El comprobante de pago electrónico no existe"
                                            + "</statusMessage></statusCdr>"));
                }
                default -> throw new OseIntake.Fault("0100", "Operación no soportada.");
            };
        } catch (OseClients.Unauthorized e) {
            return fault("0102", "Usuario o contraseña incorrectos");
        } catch (OseIntake.Fault e) {
            return fault(e.code, e.getMessage());
        } catch (ZipGuard.Refused e) {
            return fault(e.code, e.getMessage());
        } catch (RuntimeException e) {
            LOG.errorf(e, "OSE SOAP request failed");
            return fault("0109", "El sistema no puede responder su solicitud.");
        }
    }

    private static byte[] content(Request request) {
        try {
            return Base64.getMimeDecoder().decode(request.field("contentFile"));
        } catch (IllegalArgumentException e) {
            throw new OseIntake.Fault("0156", "El archivo ZIP está corrupto.");
        }
    }

    static Optional<Request> parse(byte[] body) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(body));
            Element envelope = document.getDocumentElement();
            Element bodyElement = child(envelope, "Body");
            Element header = child(envelope, "Header");
            if (bodyElement == null) {
                return Optional.empty();
            }
            Element operation = firstElement(bodyElement);
            if (operation == null) {
                return Optional.empty();
            }
            Map<String, String> fields = new HashMap<>();
            NodeList children = operation.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element e) {
                    fields.put(e.getLocalName(), e.getTextContent());
                }
            }
            String username = header == null ? null : text(header, "Username");
            String password = header == null ? null : text(header, "Password");
            return Optional.of(new Request(operation.getLocalName(), username, password, fields));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Element child(Element parent, String localName) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && localName.equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }

    private static Element firstElement(Element parent) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                return e;
            }
        }
        return null;
    }

    private static String text(Element root, String localName) {
        NodeList nodes = root.getElementsByTagNameNS("*", localName);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().strip();
    }

    private static final String OPEN = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><soap-env:Envelope "
            + "xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Header/><soap-env:Body>";
    private static final String CLOSE = "</soap-env:Body></soap-env:Envelope>";

    static Response ok(String element, String content) {
        String xml = OPEN + "<br:" + element + " xmlns:br=\"http://service.sunat.gob.pe\">" + content + "</br:" + element + ">" + CLOSE;
        return Response.ok(xml.getBytes(StandardCharsets.UTF_8)).type("text/xml; charset=utf-8").build();
    }

    /** A SOAP fault; counted by code (xhandler.ose.faults): client mistakes (0102, 0156...) aren't outages, 0109 is ours. */
    static Response fault(String code, String message) {
        String xml = OPEN + "<soap-env:Fault><faultcode>soap-env:Client." + code + "</faultcode><faultstring>" + escape(message)
                + "</faultstring></soap-env:Fault>" + CLOSE;
        return Response.status(500).entity(xml.getBytes(StandardCharsets.UTF_8)).type("text/xml; charset=utf-8").build();
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
