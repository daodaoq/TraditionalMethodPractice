package org.java.retry;

import org.java.message.Response;

public interface RetryPolicy {

    Response retry(RetryContext context) throws Exception;
}
