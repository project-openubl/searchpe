package io.searchpe.batchs;

import javax.batch.api.AbstractBatchlet;
import javax.batch.runtime.BatchStatus;
import javax.inject.Named;

@Named
public class EndBatchlet extends AbstractBatchlet {

    @Override
    public String process() {
        return BatchStatus.COMPLETED.toString();
    }

}