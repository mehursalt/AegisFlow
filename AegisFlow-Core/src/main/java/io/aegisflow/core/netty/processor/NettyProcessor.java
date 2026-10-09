package io.aegisflow.core.netty.processor;


import io.aegisflow.core.context.HttpRequestWrapper;
/**
 */
public interface NettyProcessor {

    void process(HttpRequestWrapper wrapper);

    void  start();

    void shutDown();
}
