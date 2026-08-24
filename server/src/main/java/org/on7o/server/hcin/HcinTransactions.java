package org.on7o.server.hcin;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Explicit transactions over the HCIN dataset.
 *
 * <p>TDB2 requires every read and every write to be inside a transaction, and
 * getting that wrong fails at runtime rather than at compile time. Routing
 * everything through these two methods means no caller can forget to open one,
 * forget to end one, or leave a failed write half applied.
 */
@Service
public class HcinTransactions {

    private final HcinDataset hcin;

    public HcinTransactions(HcinDataset hcin) {
        this.hcin = hcin;
    }

    /**
     * Runs a read.
     *
     * <p>Whatever the function returns must not hold on to the dataset: models
     * and iterators are only valid until the transaction ends.
     */
    public <T> T read(Function<Dataset, T> work) {
        Dataset dataset = hcin.dataset();
        dataset.begin(ReadWrite.READ);
        try {
            return work.apply(dataset);
        } finally {
            dataset.end();
        }
    }

    /** Runs a write, committing it or, if it throws, rolling it back whole. */
    public void write(Consumer<Dataset> work) {
        writeAnd(dataset -> {
            work.accept(dataset);
            return null;
        });
    }

    /** Runs a write that produces a value, committing it or rolling it back whole. */
    public <T> T writeAnd(Function<Dataset, T> work) {
        Dataset dataset = hcin.dataset();
        dataset.begin(ReadWrite.WRITE);
        try {
            T result = work.apply(dataset);
            dataset.commit();
            return result;
        } catch (RuntimeException e) {
            dataset.abort();
            throw e;
        } finally {
            dataset.end();
        }
    }
}
