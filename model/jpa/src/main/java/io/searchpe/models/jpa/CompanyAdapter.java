package io.searchpe.models.jpa;

import io.searchpe.models.CompanyModel;
import io.searchpe.models.jpa.entity.CompanyEntity;

import javax.persistence.EntityManager;

public class CompanyAdapter implements CompanyModel {

    private final EntityManager em;
    private final CompanyEntity company;

    public CompanyAdapter(EntityManager em, CompanyEntity company) {
        this.em = em;
        this.company = company;
    }

    @Override
    public long getId() {
        return 0;
    }

    @Override
    public String getRuc() {
        return null;
    }

    @Override
    public String getRazonSocial() {
        return null;
    }

    @Override
    public String getEstadoContribuyente() {
        return null;
    }

    @Override
    public String getCondicionDomicilio() {
        return null;
    }

    @Override
    public String getUbigeo() {
        return null;
    }

    @Override
    public String getTipoVia() {
        return null;
    }

    @Override
    public String getNombreVia() {
        return null;
    }

    @Override
    public String getCodigoZona() {
        return null;
    }

    @Override
    public String getTipoZona() {
        return null;
    }

    @Override
    public String getNumero() {
        return null;
    }

    @Override
    public String getInterior() {
        return null;
    }

    @Override
    public String getLote() {
        return null;
    }

    @Override
    public String getDepartamento() {
        return null;
    }

    @Override
    public String getManzana() {
        return null;
    }

    @Override
    public String getKilometro() {
        return null;
    }
}
