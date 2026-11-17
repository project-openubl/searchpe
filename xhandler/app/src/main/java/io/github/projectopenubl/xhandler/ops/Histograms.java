package io.github.projectopenubl.xhandler.ops;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/** Histograms for the latency panels of the SLO dashboard (HTTP requests, job runs). */
public class Histograms {

    @Produces
    @Singleton
    public MeterFilter histograms() {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                if (id.getName().equals("http.server.requests") || id.getName().equals("xhandler.jobs.run")) {
                    return DistributionStatisticConfig.builder().percentilesHistogram(true).build().merge(config);
                }
                return config;
            }
        };
    }
}
