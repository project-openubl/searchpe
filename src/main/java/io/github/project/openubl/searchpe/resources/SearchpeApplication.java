package io.github.project.openubl.searchpe.resources;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.info.License;

import javax.ws.rs.core.Application;

@OpenAPIDefinition(

        info = @Info(
                version = "2.0.0",
                title = "Searchpe API",
                contact = @Contact(
                        name = "Searchpe community support",
                        url = "https://project-openubl.github.io/",
                        email = "https://project-openubl.github.io/"),
                license = @License(
                        name = "EPL-2.0",
                        url = "https://opensource.org/licenses/EPL-2.0")
        )
)
public class SearchpeApplication extends Application {
}
