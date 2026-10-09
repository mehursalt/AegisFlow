package io.aegisflow.core.netty.processor;

import io.aegisflow.common.enums.ResponseCode;
import io.aegisflow.common.exception.BaseException;
import io.aegisflow.common.exception.LimitedException;
import io.aegisflow.core.context.GatewayContext;
import io.aegisflow.core.context.HttpRequestWrapper;
import io.aegisflow.core.filter.FilterChainFactory;
import io.aegisflow.core.filter.GatewayFilterChainChainFactory;
import io.aegisflow.core.helper.RequestHelper;
import io.aegisflow.core.helper.ResponseHelper;
import io.aegisflow.common.utils.RequestIds;
import io.aegisflow.core.netty.PacketLossCalculator;
import com.netflix.hystrix.Hystrix;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

/**
 */

/**
 * NettyCoreProcessor 是负责在基于 Netty 的服务器中处理 HTTP 请求的组件。
 */
@Slf4j
public class NettyCoreProcessor implements NettyProcessor {

    // FilterChainFactory 负责创建和管理过滤器的执行。
    private FilterChainFactory filterChainFactory = GatewayFilterChainChainFactory.getInstance();


    /**
     * 处理传入的 HTTP 请求。
     *
     * @param wrapper 包含 FullHttpRequest 和 ChannelHandlerContext 的 HttpRequestWrapper。
     */
    @Override
    public void process(HttpRequestWrapper wrapper) {
        FullHttpRequest request = wrapper.getRequest();
        ChannelHandlerContext ctx = wrapper.getCtx();
        log.info("gateway_request requestId={} method={} path={}",
                request.headers().get(RequestIds.HEADER), request.method(), new io.netty.handler.codec.http.QueryStringDecoder(request.uri()).path());

        try {
            // 创建并填充 GatewayContext 以保存有关传入请求的信息。
            GatewayContext gatewayContext = RequestHelper.doContext(request, ctx);
            gatewayContext.setObservation(wrapper.getObservation());
            if (wrapper.getObservation() != null) { wrapper.getObservation().route(gatewayContext.getRule().getId()); }
            if (wrapper.getObservation() != null) { wrapper.getObservation().service(gatewayContext.getRule().getServiceId()); }
            int limit = gatewayContext.getRule().getMaxConcurrentRequests();
            if (limit > 0) {
                var permit = io.aegisflow.core.monitor.AdmissionLimiter.getInstance()
                        .tryAcquire(gatewayContext.getRule().getServiceId(), limit);
                if (permit == null) { throw new LimitedException(ResponseCode.GATEWAY_OVERLOADED); }
                if (wrapper.getObservation() != null) { wrapper.getObservation().onComplete(permit::close); }
                else {
                    gatewayContext.setCompletedCallBack(ignored -> permit.close());
                    ctx.channel().closeFuture().addListener(ignored -> permit.close());
                }
            }

            // 在 GatewayContext 上执行过滤器链逻辑。
            filterChainFactory.buildFilterChain(gatewayContext).doFilter(gatewayContext);
        }catch (HystrixRuntimeException e){
            // 通过记录日志并发送内部服务器错误响应处理未知异常。
            log.error("网关请求超时错误", e);
            FullHttpResponse httpResponse = ResponseHelper.getHttpResponse(ResponseCode.GATEWAY_FALLBACK);
            doWriteAndRelease(wrapper, httpResponse, "timeout");
        }
        catch (LimitedException e) {
            // 通过记录日志并发送内部服务器错误响应处理未知异常。
            log.warn("request_rejected requestId={} code={}", request.headers().get(RequestIds.HEADER), e.getCode().getCode());
            FullHttpResponse httpResponse = ResponseHelper.getHttpResponse(e.getCode());
            if (e.getCode() == ResponseCode.GATEWAY_OVERLOADED) { httpResponse.headers().set("Retry-After", "1"); }
            doWriteAndRelease(wrapper, httpResponse, e.getCode() == ResponseCode.GATEWAY_OVERLOADED ? "overloaded" : "limited");
        } catch (BaseException e) {
            // 通过记录日志并发送适当的 HTTP 响应处理已知异常。
            log.error("处理错误 {} {}", e.getCode().getCode(), e.getCode().getMessage());
            FullHttpResponse httpResponse = ResponseHelper.getHttpResponse(e.getCode());
            doWriteAndRelease(wrapper, httpResponse, e.getCode() == ResponseCode.UNAUTHORIZED ? "unauthorized" : "gateway_error");
        } catch (Throwable t) {
            // 通过记录日志并发送内部服务器错误响应处理未知异常。
            log.error("处理未知错误", t);
            FullHttpResponse httpResponse = ResponseHelper.getHttpResponse(ResponseCode.INTERNAL_ERROR);
            doWriteAndRelease(wrapper, httpResponse, "gateway_error");
        }
    }

    /**
     * 将 HTTP 响应写入通道并释放资源。
     *
     * @param ctx          用于写入响应的 ChannelHandlerContext。
     * @param request      从客户端接收的 FullHttpRequest。
     * @param httpResponse 作为响应发送的 FullHttpResponse。
     */
    private void doWriteAndRelease(HttpRequestWrapper wrapper, FullHttpResponse httpResponse, String reason) {
        httpResponse.headers().set(RequestIds.HEADER, wrapper.getRequest().headers().get(RequestIds.HEADER));
        if (wrapper.getObservation() != null) { wrapper.getObservation().reason(reason); }
        ResponseHelper.writeAndObserve(wrapper.getCtx(), httpResponse, wrapper.getObservation(), false, null);
        ReferenceCountUtil.release(wrapper.getRequest());
    }

    /**
     * 启动 NettyCoreProcessor。（目前为空方法）
     */
    @Override
    public void start() {
        // 如果需要，启动处理器的实现。
    }

    /**
     * 关闭 NettyCoreProcessor。（目前为空方法）
     */
    @Override
    public void shutDown() {
        // 如果需要，关闭处理器的实现。
    }
}
