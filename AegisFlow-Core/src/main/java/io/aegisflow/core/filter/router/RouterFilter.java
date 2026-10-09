package io.aegisflow.core.filter.router;

import io.aegisflow.common.config.Rule;
import io.aegisflow.core.ConfigLoader;
import io.aegisflow.core.context.GatewayContext;
import io.aegisflow.core.filter.Filter;
import io.aegisflow.core.filter.FilterAspect;
import io.aegisflow.core.helper.AsyncHttpHelper;
import com.netflix.hystrix.*;
import org.asynchttpclient.Request;
import org.asynchttpclient.Response;
import rx.Subscription;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import static io.aegisflow.common.constant.FilterConst.*;

@FilterAspect(id=ROUTER_FILTER_ID, name=ROUTER_FILTER_NAME, order=ROUTER_FILTER_ORDER)
public class RouterFilter implements Filter {
    private final Function<Request, CompletableFuture<Response>> sender;
    public RouterFilter() { this(request -> AsyncHttpHelper.getInstance().executeRequest(request)); }
    RouterFilter(Function<Request, CompletableFuture<Response>> sender) { this.sender = sender; }

    @Override
    public void doFilter(GatewayContext context) {
        Rule.HystrixConfig hystrix = context.getRule().getHystrixConfigs().stream()
                .filter(config -> context.getRequest().getPath().equals(config.getPath())).findFirst().orElse(null);
        Function<Request, CompletableFuture<Response>> transport = hystrix == null ? sender
                : request -> executeWithHystrix(context, request, hystrix);
        new RoutingExchange(context, ConfigLoader.getConfig().getHttpRequestTimeout(), hystrix, transport).start();
    }

    private CompletableFuture<Response> executeWithHystrix(GatewayContext context, Request request, Rule.HystrixConfig config) {
        CompletableFuture<Response> result = new CompletableFuture<>();
        AtomicReference<CompletableFuture<Response>> network = new AtomicReference<>();
        AtomicReference<Subscription> subscription = new AtomicReference<>();
        HystrixCommand.Setter settings = HystrixCommand.Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey(context.getUniqueId()))
                .andCommandKey(HystrixCommandKey.Factory.asKey(context.getRule().getId()))
                .andThreadPoolPropertiesDefaults(HystrixThreadPoolProperties.Setter()
                        .withCoreSize(Math.max(1, Math.min(32, config.getThreadCoreSize()))))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
                        .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD)
                        .withExecutionTimeoutInMilliseconds(Math.max(1, config.getTimeoutInMilliseconds()))
                        .withExecutionTimeoutEnabled(true)
                        .withExecutionIsolationThreadInterruptOnTimeout(true));
        HystrixCommand<Response> command = new HystrixCommand<Response>(settings) {
            @Override protected Response run() throws Exception {
                if (result.isCancelled()) { throw new java.util.concurrent.CancellationException(); }
                CompletableFuture<Response> pending = sender.apply(request);
                network.set(pending);
                if (result.isCancelled()) { pending.cancel(true); }
                try { return pending.get(); }
                catch (InterruptedException error) { pending.cancel(true); Thread.currentThread().interrupt(); throw error; }
            }
        };
        result.whenComplete((response, error) -> {
            if (result.isCancelled() || error != null) {
                CompletableFuture<Response> pending = network.get();
                if (pending != null) { pending.cancel(true); }
                Subscription sub = subscription.get();
                if (sub != null) { sub.unsubscribe(); }
            }
        });
        Subscription sub = command.toObservable().subscribe(result::complete, error -> {
            if (command.isResponseTimedOut()) {
                result.completeExceptionally(new TimeoutException("Hystrix execution timeout"));
            } else {
                result.completeExceptionally(new IOException("Hystrix rejected or failed", error));
            }
        });
        subscription.set(sub);
        if (result.isCancelled()) { sub.unsubscribe(); }
        return result;
    }
}
