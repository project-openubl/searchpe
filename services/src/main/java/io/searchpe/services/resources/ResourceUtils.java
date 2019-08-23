package io.searchpe.services.resources;

import javax.ws.rs.core.Response;
import java.util.Optional;

public class ResourceUtils {

    public static <T> Response getResponseFromOptionalVersion(Optional<T> optional) {
        Response response;
        if (optional.isPresent()) {
            response = Response.ok(optional.get()).build();
        } else {
            response = Response.status(Response.Status.NOT_FOUND).build();
        }

        return response;
    }

}
