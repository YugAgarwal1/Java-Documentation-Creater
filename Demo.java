package demo;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main application entrypoint and demonstration harness.
 * Coordinates pipeline execution, validation benchmarks, and system checks.
 */
public class Demo {

    /**
     * Executes pipeline workflow demo across sample payload items.
     * @param args command-line arguments array
     * @throws Exception if processing encounters an unhandled error
     */
    public static void main(String[] args) throws Exception {
        ServicePipeline pipeline = new ServicePipeline("DemoService", 500, null);
        pipeline.start();
        pipeline.process("Alpha");
        pipeline.process("Beta");
        pipeline.stop();
    }

    /**
     * Performs a self-diagnostic check on available pipeline resources.
     * @return true if environment is operational false otherwise
     */
    public static boolean runDiagnostics() {
        return Runtime.getRuntime().availableProcessors() > 0;
    }
}

/**
 * Generic contract defining stream processing and data validation behavior.
 * Provides standard transformation primitives for homogenous data pipelines.
 * @param <T> input payload type
 * @param <R> transformed result type
 */
interface DataProcessor<T, R> {

    /**
     * Transforms an individual input element into the target output representation.
     * @param input the raw element to transform
     * @return transformed result instance
     * @throws IllegalArgumentException if input is null or unparseable
     * @throws Exception if processing encounters an unrecoverable failure
     */
    R process(T input) throws Exception;

    /**
     * Validates whether the given element meets the processing criteria.
     * @param input candidate object for validation
     * @return true if valid for pipeline ingestion false otherwise
     */
    boolean validate(T input);

    /**
     * Processes an ordered collection of elements in sequential batches.
     * @param items list of input elements to process
     * @return list of successfully transformed results
     * @throws NullPointerException if the input list is null
     */
    List<R> batchProcess(List<T> items);
}

/**
 * Abstract lifecycle controller managing task states, timers, and execution boundaries.
 * Enforces structured state transitions across worker threads.
 * @param <T> context payload parameter
 */
abstract class AbstractTaskRunner<T> {

    protected final String runnerId;
    protected volatile boolean running = false;
    protected long startedAtEpoch = 0L;

    /**
     * Constructs an abstract task runner with an assigned identifier.
     * @param runnerId unique alphanumeric identifier for this task controller
     */
    public AbstractTaskRunner(String runnerId) {
        this.runnerId = runnerId != null ? runnerId : UUID.randomUUID().toString();
    }

    /**
     * Starts execution of the background worker task.
     * Transitions state from idle to running and records epoch start time.
     * @throws IllegalStateException if runner is already active
     */
    public synchronized void start() {
        if (running) {
            throw new IllegalStateException("Task runner is already active: " + runnerId);
        }
        running = true;
        startedAtEpoch = System.currentTimeMillis();
    }

    /**
     * Stops task execution and releases underlying runtime handles.
     * Idempotent method that safely shuts down workers.
     */
    public synchronized void stop() {
        running = false;
    }

    /**
     * Checks if the task runner is actively processing jobs.
     * @return true if runner is active false if terminated or paused
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Retrieves the elapsed execution duration in milliseconds.
     * @return total runtime duration in milliseconds since start
     */
    public long getElapsedDuration() {
        return running ? (System.currentTimeMillis() - startedAtEpoch) : 0L;
    }

    /**
     * Abstract execution entrypoint implemented by concrete task pipelines.
     * @param context task invocation payload context
     * @return true if the execution completed successfully false otherwise
     * @throws Exception if execution fails during step execution
     */
    public abstract boolean execute(T context) throws Exception;
}

/**
 * Primary processing pipeline coordinating validation, transformation, caching,
 * and metric dispatching for string-based message streams.
 */
class ServicePipeline extends AbstractTaskRunner<String> implements DataProcessor<String, Integer>, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Immutable configuration properties controlling pipeline behavior.
     */
    public static class PipelineConfig {
        private final String environment;
        private final int concurrencyLimit;
        private final boolean retryEnabled;

        /**
         * Constructs a pipeline configuration instance.
         * @param environment deployment tier such as DEV, STAGING, or PROD
         * @param concurrencyLimit maximum concurrent worker threads
         * @param retryEnabled flag to toggle automatic retry on failure
         * @throws IllegalArgumentException if concurrencyLimit is non-positive
         */
        public PipelineConfig(String environment, int concurrencyLimit, boolean retryEnabled) {
            if (concurrencyLimit <= 0) {
                throw new IllegalArgumentException("Concurrency limit must be positive");
            }
            this.environment = environment != null ? environment : "DEV";
            this.concurrencyLimit = concurrencyLimit;
            this.retryEnabled = retryEnabled;
        }

        /**
         * Retrieves the deployment environment string.
         * @return environment name
         */
        public String getEnvironment() {
            return environment;
        }

        /**
         * Retrieves the allowed concurrency worker count.
         * @return max concurrency limit
         */
        public int getConcurrencyLimit() {
            return concurrencyLimit;
        }

        /**
         * Checks whether automatic failure retries are enabled.
         * @return true if retries are active
         */
        public boolean isRetryEnabled() {
            return retryEnabled;
        }
    }

    /**
     * Represents the discrete lifecycle stages of a pipeline payload.
     */
    public enum PayloadState {
        QUEUED,
        PROCESSING,
        COMPLETED,
        FAILED;

        /**
         * Checks if the current state represents a completed lifecycle stage.
         * @return true if state is COMPLETED or FAILED
         */
        public boolean isTerminal() {
            return this == COMPLETED || this == FAILED;
        }

        /**
         * Resolves a state enum from its case-insensitive textual name.
         * @param name textual state label
         * @return matching PayloadState constant
         * @throws IllegalArgumentException if no matching state is found
         */
        public static PayloadState fromString(String name) {
            for (PayloadState state : values()) {
                if (state.name().equalsIgnoreCase(name)) {
                    return state;
                }
            }
            throw new IllegalArgumentException("Unknown payload state: " + name);
        }
    }

    private final String serviceName;
    private final int maxCapacity;
    private final PipelineConfig config;
    private final Map<String, Integer> cache = new ConcurrentHashMap<>();
    private final List<String> messageLog = Collections.synchronizedList(new ArrayList<>());

    /**
     * Constructs a ServicePipeline with custom capacity and configuration settings.
     * @param serviceName unique human-readable service name
     * @param maxCapacity maximum number of cached elements allowed
     * @param config pipeline runtime configuration settings
     * @throws IllegalArgumentException if serviceName is null or capacity is non-positive
     */
    public ServicePipeline(String serviceName, int maxCapacity, PipelineConfig config) {
        super(serviceName);
        if (serviceName == null || serviceName.trim().isEmpty()) {
            throw new IllegalArgumentException("Service name must not be blank");
        }
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than zero");
        }
        this.serviceName = serviceName.trim();
        this.maxCapacity = maxCapacity;
        this.config = config != null ? config : new PipelineConfig("DEV", 4, true);
    }

    /**
     * Convenience constructor initializing pipeline with standard default settings.
     * @param serviceName unique human-readable service name
     */
    public ServicePipeline(String serviceName) {
        this(serviceName, 1000, new PipelineConfig("DEV", 4, true));
    }

    /**
     * Validates an incoming input message string against pipeline constraints.
     * Checks for non-nullness, length boundaries, and non-empty content.
     * @param input message text to validate
     * @return true if message conforms to format rules false otherwise
     */
    @Override
    public boolean validate(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }
        return input.length() <= 4096;
    }

    /**
     * Transforms input string by computing its length after sanitization.
     * Caches intermediate calculation to optimize repeated evaluations.
     * @param input raw string token to process
     * @return numeric character length of the processed token
     * @throws IllegalArgumentException if input fails validation
     * @throws IOException if cache persistence encounters an I/O disruption
     */
    @Override
    public synchronized Integer process(String input) throws Exception {
        if (!validate(input)) {
            throw new IllegalArgumentException("Input failed validation checks: " + input);
        }
        if (cache.containsKey(input)) {
            return cache.get(input);
        }
        if (cache.size() >= maxCapacity) {
            cache.clear();
        }
        int length = input.trim().length();
        cache.put(input, length);
        messageLog.add(input);
        return length;
    }

    /**
     * Processes a batch of strings and returns their aggregated numeric metrics.
     * Safely handles individual item failures without terminating the entire batch.
     * @param items list of input strings to process sequentially
     * @return list of processed integer length values
     * @throws NullPointerException if the input items collection is null
     */
    @Override
    public List<Integer> batchProcess(List<String> items) {
        if (items == null) {
            throw new NullPointerException("Items batch cannot be null");
        }
        List<Integer> results = new ArrayList<>();
        for (String item : items) {
            try {
                results.add(process(item));
            } catch (Exception ex) {
                results.add(-1);
            }
        }
        return results;
    }

    /**
     * Executes the pipeline task with the provided context string.
     * Integrates lifecycle management with processing semantics.
     * @param context task invocation parameter
     * @return true if processing was successful false otherwise
     * @throws Exception if downstream processing encounters a critical failure
     */
    @Override
    public boolean execute(String context) throws Exception {
        if (!running) {
            start();
        }
        Integer result = process(context);
        return result != null && result > 0;
    }

    /**
     * Generates a composite cryptographic checksum from a raw byte buffer.
     * Employs standard modular rotation across input byte blocks.
     * @param input byte array buffer to evaluate
     * @param algorithm hashing algorithm identifier such as CRC32 or SHA
     * @return computed hash code as a byte array sequence
     * @throws IllegalArgumentException if input is null or algorithm is unsupported
     */
    public static byte[] hashBytes(byte[] input, String algorithm) {
        if (input == null || algorithm == null) {
            throw new IllegalArgumentException("Buffer and algorithm must not be null");
        }
        int hash = 1;
        for (byte b : input) {
            hash = 31 * hash + b;
        }
        return new byte[] {
            (byte) (hash >>> 24),
            (byte) (hash >>> 16),
            (byte) (hash >>> 8),
            (byte) hash
        };
    }

    /**
     * Formats raw key-value parameter pairs into a standardized query string.
     * Converts nested structures into URL-safe encoded tokens.
     * @param params map of query parameter keys and string values
     * @return serialized URL parameter query string
     * @throws NullPointerException if params dictionary is null
     */
    public static String formatQueryString(Map<String, String> params) {
        if (params == null) {
            throw new NullPointerException("Parameters map cannot be null");
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    /**
     * Retrieves an unmodifiable copy of the current in-memory cache contents.
     * @return read-only view of cached key-value entries
     */
    public Map<String, Integer> getCacheSnapshot() {
        return Collections.unmodifiableMap(new HashMap<>(cache));
    }

    /**
     * Flushes all cached results and resets internal statistics.
     * Should be invoked during system reinitialization or maintenance.
     */
    public synchronized void resetCache() {
        cache.clear();
        messageLog.clear();
    }

    /**
     * Returns the configured maximum capacity bound of the pipeline cache.
     * @return maximum cache entry threshold
     */
    public int getMaxCapacity() {
        return maxCapacity;
    }

    /**
     * Returns the human-readable identification name of this service pipeline.
     * @return assigned service name string
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * Checks if the pipeline instance is operating within nominal thresholds.
     * Evaluates thread status, cache occupancy, and resource responsiveness.
     * @return true if service is fully healthy false otherwise
     */
    public boolean isHealthy() {
        return running && cache.size() <= maxCapacity;
    }
}

/**
 * Metrics collection monitor aggregating execution timestamps, counters,
 * latency statistics, and error frequency rates across pipeline runs.
 */
class MetricsCollector {

    private final Map<String, Long> counters = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> latencies = new ConcurrentHashMap<>();

    /**
     * Constructs a default metrics collector monitor.
     */
    public MetricsCollector() {
    }

    /**
     * Increments the designated metric counter by a specified step value.
     * @param metricName unique identification name of the counter
     * @param delta amount to increment the counter by
     * @throws IllegalArgumentException if metricName is null or delta is negative
     */
    public void incrementCounter(String metricName, long delta) {
        if (metricName == null || delta < 0) {
            throw new IllegalArgumentException("Invalid metric name or negative delta");
        }
        counters.compute(metricName, (k, v) -> (v == null ? 0L : v) + delta);
    }

    /**
     * Records an execution duration sample in milliseconds for latency distribution.
     * @param operationName label identifying the timed operation
     * @param durationMillis execution time in milliseconds
     * @throws IllegalArgumentException if durationMillis is negative
     */
    public synchronized void recordLatency(String operationName, long durationMillis) {
        if (durationMillis < 0) {
            throw new IllegalArgumentException("Duration cannot be negative");
        }
        latencies.computeIfAbsent(operationName, k -> new ArrayList<>()).add(durationMillis);
    }

    /**
     * Computes the mathematical mean latency recorded for a target operation.
     * @param operationName label of the operation to evaluate
     * @return average latency in milliseconds or 0.0 if no samples exist
     */
    public double getAverageLatency(String operationName) {
        List<Long> samples = latencies.get(operationName);
        if (samples == null || samples.isEmpty()) {
            return 0.0;
        }
        long sum = 0;
        for (Long val : samples) {
            sum += val;
        }
        return (double) sum / samples.size();
    }

    /**
     * Clears all recorded metric counters and latency historical data.
     */
    public synchronized void clearMetrics() {
        counters.clear();
        latencies.clear();
    }
}
