package io.github.projectopenubl.xhandler.jobs;

import java.util.Map;

/**
 * Runs one kind of job. Handlers must be idempotent (DESIGN.md §10.8 rule 7): a job can run again
 * from the start, so a handler re-reads state and writes with compare-and-set. Handlers open their
 * own transactions; none is open when {@link #run} is called.
 */
public interface JobHandler {

    /** The job name this handler runs, e.g. {@code document.attempt}. */
    String name();

    /** Runs the job. The returned map becomes the job's {@code result}; throwing fails the job. */
    Map<String, Object> run(JobContext context) throws Exception;
}
