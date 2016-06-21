package com.splicemachine.metrics;

/**
 * @author Scott Fines
 *         Date: 1/17/14
 */
public interface TimeMeasure {

		void startTime();

		long stopTime();

		long getElapsedTime();

		long getStopTimestamp();

		long getStartTimestamp();
}
