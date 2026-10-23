package com.atlassian.migration.agent.okhttp;

/**
 * Stand-in for the exception thrown by the CCMA agent, which is not on the compile classpath.
 */
public class HttpServiceException extends RuntimeException {
}
