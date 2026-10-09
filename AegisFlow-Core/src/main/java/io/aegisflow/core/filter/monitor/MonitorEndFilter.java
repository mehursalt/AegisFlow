package io.aegisflow.core.filter.monitor;
import io.aegisflow.core.context.GatewayContext;
import io.aegisflow.core.filter.Filter;
import io.aegisflow.core.filter.FilterAspect;
import static io.aegisflow.common.constant.FilterConst.*;
/** Compatibility hook: measurement is owned by ingress and response write completion. */
@FilterAspect(id=MONITOR_END_FILTER_ID, name=MONITOR_END_FILTER_NAME, order=MONITOR_END_FILTER_ORDER)
public class MonitorEndFilter implements Filter {
    @Override
    public void doFilter(GatewayContext ctx) { }
}
