package blossom.project.core.filter.router;

import blossom.project.common.config.Rule;
import blossom.project.common.enums.ResponseCode;
import blossom.project.core.context.GatewayContext;
import blossom.project.core.helper.ResponseHelper;
import blossom.project.core.response.GatewayResponse;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.concurrent.EventExecutor;
import org.asynchttpclient.Request;
import org.asynchttpclient.Response;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/** One logical request; mutable state is confined to its inbound event loop. */
final class RoutingExchange {
    private final GatewayContext context;
    private final EventExecutor loop;
    private final Function<Request, CompletableFuture<Response>> sender;
    private final Rule.HystrixConfig hystrix;
    private final int maxRetries;
    private final int attemptTimeout;
    private final int backoff;
    private final long deadline;
    private final Request firstRequest;
    private final ChannelFutureListener onClose;
    private ScheduledFuture<?> deadlineTask;
    private ScheduledFuture<?> retryTask;
    private CompletableFuture<Response> active;
    private boolean finished;
    private int attempts;

    RoutingExchange(GatewayContext context, int defaultTimeout,
                    Rule.HystrixConfig hystrix, Function<Request, CompletableFuture<Response>> sender) {
        this.context = context;
        this.loop = context.getNettyCtx().executor();
        this.sender = sender;
        this.hystrix = hystrix;
        Rule.RetryConfig retry = context.getRule().getRetryConfig();
        if (retry == null) { retry = new Rule.RetryConfig(); }
        int budget = boundedTimeout(retry.getTotalTimeoutMs(), defaultTimeout);
        if (hystrix != null) { budget = Math.min(budget, boundedTimeout(hystrix.getTimeoutInMilliseconds(), defaultTimeout)); }
        attemptTimeout = boundedTimeout(retry.getAttemptTimeoutMs(), defaultTimeout);
        String method = context.getRequest().getMethod().name();
        maxRetries = hystrix == null && (method.equals("GET") || method.equals("HEAD"))
                ? Math.max(0, Math.min(3, retry.getTimes())) : 0;
        backoff = Math.max(1, Math.min(1000, retry.getBackoffMs()));
        long alreadyElapsed = context.getObservation() == null ? 0 : context.getObservation().elapsedNanos();
        deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budget) - alreadyElapsed;
        long remainingMs = Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
        context.getRequest().setRequestTimeout((int)Math.min(attemptTimeout, remainingMs));
        firstRequest = context.getRequest().build();
        onClose = ignored -> dispatch(this::cancel);
    }

    private static int boundedTimeout(int supplied, int fallback) {
        return Math.max(1, Math.min(300000, supplied > 0 ? supplied : fallback));
    }

    void start() { dispatch(() -> {
        context.getNettyCtx().channel().closeFuture().addListener(onClose);
        long remaining = deadline - System.nanoTime();
        if (!context.getNettyCtx().channel().isActive()) { cancel(); return; }
        if (remaining <= 0) { fail(new TimeoutException("Total request deadline exceeded")); return; }
        deadlineTask = loop.schedule(() -> fail(new TimeoutException("Total request deadline exceeded")), remaining, TimeUnit.NANOSECONDS);
        attempt();
    }); }

    private void dispatch(Runnable action) {
        if (loop.inEventLoop()) { action.run(); } else { loop.execute(action); }
    }

    private void attempt() {
        if (finished) { return; }
        if (!context.getNettyCtx().channel().isActive()) { cancel(); return; }
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) { fail(new TimeoutException("Total request deadline exceeded")); return; }
        attempts++;
        context.setCurrentRetryTimes(attempts - 1);
        if (attempts > 1) {
            blossom.project.core.monitor.GatewayMetrics.getInstance().registry()
                    .counter("gateway.retry", "route", context.getRule().getId(), "method", context.getRequest().getMethod().name()).increment();
        }
        context.getRequest().setRequestTimeout((int)Math.max(1, Math.min(attemptTimeout, TimeUnit.NANOSECONDS.toMillis(remaining))));
        try {
            Request request = attempts == 1 ? firstRequest : context.getRequest().build();
            active = sender.apply(request);
            active.whenComplete((response, error) -> dispatch(() -> completed(response, error)));
        } catch (RuntimeException error) { completed(null, error); }
    }

    private void completed(Response response, Throwable error) {
        if (finished) { return; }
        if (System.nanoTime() >= deadline) { fail(new TimeoutException("Total request deadline exceeded")); return; }
        Throwable cause = unwrap(error);
        if (cause == null && response != null) {
            finish(GatewayResponse.buildGatewayResponse(response), null);
            return;
        }
        if (cause == null) { cause = new IOException("No downstream response"); }
        if ((cause instanceof IOException || cause instanceof TimeoutException) && attempts <= maxRetries) {
            long delay = Math.min(1000L, (long)backoff << (attempts - 1));
            if (TimeUnit.MILLISECONDS.toNanos(delay) < deadline - System.nanoTime()) {
                retryTask = loop.schedule(this::attempt, delay, TimeUnit.MILLISECONDS);
                return;
            }
        }
        fail(cause);
    }

    static Throwable unwrap(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null) { error = error.getCause(); }
        return error;
    }

    private void fail(Throwable error) {
        if (finished) { return; }
        boolean timeout = unwrap(error) instanceof TimeoutException;
        ResponseCode code = timeout ? ResponseCode.REQUEST_TIMEOUT : hystrix != null ? ResponseCode.SERVICE_UNAVAILABLE : ResponseCode.HTTP_RESPONSE_ERROR;
        GatewayResponse response = GatewayResponse.buildGatewayResponse(code);
        if (hystrix != null && hystrix.getFallbackResponse() != null && !hystrix.getFallbackResponse().isEmpty()) {
            response.setContent(hystrix.getFallbackResponse());
            response.getResponseHeaders().set("Content-Type", "text/plain;charset=utf-8");
        }
        finish(response, timeout ? "timeout" : "gateway_error");
    }

    private void cleanup() {
        if (deadlineTask != null) { deadlineTask.cancel(false); }
        if (retryTask != null) { retryTask.cancel(false); }
        context.getNettyCtx().channel().closeFuture().removeListener(onClose);
        if (active != null && !active.isDone()) { active.cancel(true); }
    }

    private void finish(GatewayResponse response, String reason) {
        if (finished) { return; }
        finished = true;
        cleanup();
        context.setResponse(response);
        if (reason != null && context.getObservation() != null) { context.getObservation().reason(reason); }
        context.written();
        ResponseHelper.writeResponse(context);
    }

    private void cancel() {
        if (finished) { return; }
        finished = true;
        cleanup();
        context.releaseRequest();
        context.terminated();
    }
}
