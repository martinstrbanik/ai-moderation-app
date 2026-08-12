package sk.automoder.model;

/** Level (sample size) of a benchmark run - to save tokens. */
public enum BenchmarkLevel {
    // small sample - quick orientation test
    EXTRA_LIGHT,
    // medium sample - standard run
    LIGHT,
    // the whole dataset
    FULL
}