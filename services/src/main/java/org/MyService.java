package org;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MyService {

    public String greeting(String name) {
        return "hello " + name;
    }

}
