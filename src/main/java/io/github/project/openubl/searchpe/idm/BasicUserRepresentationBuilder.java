package io.github.project.openubl.searchpe.idm;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.Set;

@RegisterForReflection
public final class BasicUserRepresentationBuilder {
    private Long id;
    private String fullName;
    private String username;
    private String password;
    private Set<String> permissions;

    private BasicUserRepresentationBuilder() {
    }

    public static BasicUserRepresentationBuilder aBasicUserRepresentation() {
        return new BasicUserRepresentationBuilder();
    }

    public BasicUserRepresentationBuilder withId(Long id) {
        this.id = id;
        return this;
    }

    public BasicUserRepresentationBuilder withFullName(String fullName) {
        this.fullName = fullName;
        return this;
    }

    public BasicUserRepresentationBuilder withUsername(String username) {
        this.username = username;
        return this;
    }

    public BasicUserRepresentationBuilder withPassword(String password) {
        this.password = password;
        return this;
    }

    public BasicUserRepresentationBuilder withPermissions(Set<String> permissions) {
        this.permissions = permissions;
        return this;
    }

    public BasicUserRepresentation build() {
        BasicUserRepresentation basicUserRepresentation = new BasicUserRepresentation();
        basicUserRepresentation.setId(id);
        basicUserRepresentation.setFullName(fullName);
        basicUserRepresentation.setUsername(username);
        basicUserRepresentation.setPassword(password);
        basicUserRepresentation.setPermissions(permissions);
        return basicUserRepresentation;
    }
}
