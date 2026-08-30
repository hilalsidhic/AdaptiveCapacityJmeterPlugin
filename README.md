# Adaptive Capacity JMeter Plugin

A JMeter backend-listener plugin that monitors sampler performance across time windows and stops a test when configured degradation thresholds are exceeded.

The plugin supports multiple policies at the same time and evaluates them together without a switch-based branching model.

## Supported policies

- PERCENT_LATENCY
- ABSOLUTE_LATENCY
- ERROR_RATE
- ERROR_COUNT

## How it works

- JMeter sends sample results to the backend listener.
- The plugin groups results by sampler label.
- It tracks per-sampler stage metrics over a configured interval.
- It compares the current stage to the previous stage.
- If any selected policy exceeds its threshold, the plugin logs a warning and stops the running JMeter test.

## Project structure

- `src/main/java/org/example/jmeter/AdaptiveCapacityBackendListener.java` - JMeter backend listener entry point
- `src/main/java/org/example/jmeter/AdaptiveCapacityState.java` - stage tracking and multi-policy evaluation
- `src/main/java/org/example/core/degradation/*` - degradation policy implementations
- `src/main/resources/META-INF/services/org.apache.jmeter.visualizers.backend.BackendListenerClient` - JMeter service registration
- `src/test/java/*` - unit tests

## Prerequisites

- Java 17
- Gradle
- Apache JMeter 5.6.3

## Build

From the project root:

```bash
./gradlew clean test jar
```

This creates the plugin jar at:

```bash
build/libs/AdaptiveCapacityJmeterPlugin-1.0-SNAPSHOT.jar
```

## Install into JMeter

Copy the jar into your JMeter installation:

```bash
cp build/libs/AdaptiveCapacityJmeterPlugin-1.0-SNAPSHOT.jar /path/to/apache-jmeter-5.6.3/lib/ext/
```

Then restart JMeter.

## Use in JMeter

1. Open JMeter.
2. Add a Thread Group.
3. Add HTTP Request(s) or any sampler you want to monitor.
4. Add a Backend Listener.
5. Select the class:
   - `org.example.jmeter.AdaptiveCapacityBackendListener`
6. Configure the parameters:

```text
policyModes = PERCENT_LATENCY,ABSOLUTE_LATENCY,ERROR_RATE,ERROR_COUNT

evaluationIntervalSeconds = 10
degradationPercentThreshold = 20.0
absoluteLatencyThresholdMs = 1000
errorRateThresholdPercent = 5.0
errorCountThreshold = 10
sampleLabels =
```

Notes:
- Leave `sampleLabels` blank to monitor all samplers.
- To monitor specific samplers, comma-separate labels, for example:
  `GET /items,POST /orders`
- Use a Thread Group with loops or a duration so the plugin can compare multiple stages.

## Recommended test plan setup

For degradation detection, the test should run for a realistic duration and have repeated samples. A good basic setup is:

- Thread Group: 1 to 10 threads
- Ramp-up: 1 second
- Loop Count: Forever or Duration: 180 seconds
- HTTP Request(s): repeated requests
- Backend Listener: AdaptiveCapacityBackendListener

## Example configuration values

A typical starting point:

```text
policyModes = PERCENT_LATENCY,ABSOLUTE_LATENCY,ERROR_RATE,ERROR_COUNT
evaluationIntervalSeconds = 15
degradationPercentThreshold = 20.0
absoluteLatencyThresholdMs = 2000
errorRateThresholdPercent = 5.0
errorCountThreshold = 10
sampleLabels =
```

This means the plugin will compare the current window against the previous one and stop the test if latency grows too much, absolute latency exceeds the limit, error rate crosses the threshold, or error count exceeds the configured amount.

## Logs

The plugin logs key metrics to JMeter's log output. You can view them in the JMeter console or log file.

## Notes

This plugin is intended to run inside JMeter as a backend listener. It is not a standalone Java application and is not designed to be launched with a `main` method.
