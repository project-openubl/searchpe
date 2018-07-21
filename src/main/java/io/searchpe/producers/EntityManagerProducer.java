package io.searchpe.producers;

import javax.ejb.Stateless;
import javax.enterprise.inject.Produces;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@Stateless
public class EntityManagerProducer {

    @PersistenceContext(unitName = "RepeidPU")
    private EntityManager em;

    @Produces
    public EntityManager createEntityManager() {
        return em;
    }

}
