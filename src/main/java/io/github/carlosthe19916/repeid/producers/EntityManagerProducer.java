package io.github.carlosthe19916.repeid.producers;

import javax.ejb.Stateless;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@Stateless
public class EntityManagerProducer {

    @PersistenceContext
    private EntityManager em;

    @Produces
    public EntityManager createEntityManager() {
        return em;
    }

}
