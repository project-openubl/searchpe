package io.github.project.openubl.searchpe.security;

import java.util.Arrays;
import java.util.List;

public interface Permission {
    String admin = "admin:app";
    String search = "search";
    String version_write = "version:write";
    String user_write = "user:write";

    List<String> allPermissions = Arrays.asList(admin, search, version_write, user_write);
}
