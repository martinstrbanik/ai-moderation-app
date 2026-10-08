package sk.automoder.model;

/**
 * Level (sample size) of a benchmark run - to save tokens.
 *
 * <p>Each level carries the number of samples taken <b>per class</b>; the benchmark
 * samples in a balanced way (see {@code BenchmarkExecutor.selectSamples}), so the total
 * sample count is {@code samplesPerClass * number-of-classes}. {@link #FULL} means "all
 * samples" and is expressed as {@link Integer#MAX_VALUE}.</p>
 */
public enum BenchmarkLevel {

    // tiny sample - quick debug / smoke test
    DEBUG(10),
    // small sample - quick orientation test
    EXTRA_LIGHT(50),
    // medium sample - standard run
    LIGHT(500),
    // the whole dataset
    FULL(Integer.MAX_VALUE);

    /** Samples taken per class (sampling is balanced per class). */
    private final int samplesPerClass;

    BenchmarkLevel(int samplesPerClass) {
        this.samplesPerClass = samplesPerClass;
    }

    /**
     * @return the number of samples to take per class ({@link Integer#MAX_VALUE} = the
     *         whole dataset).
     */
    public int getSamplesPerClass() {
        return samplesPerClass;
    }
}
