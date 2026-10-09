package blossom.project.core.filter.monitor;
import blossom.project.core.context.GatewayContext;
import blossom.project.core.filter.Filter;
import blossom.project.core.filter.FilterAspect;
import static blossom.project.common.constant.FilterConst.*;
/** Compatibility hook: measurement is owned by ingress and response write completion. */
@FilterAspect(id=MONITOR_END_FILTER_ID, name=MONITOR_END_FILTER_NAME, order=MONITOR_END_FILTER_ORDER)
public class MonitorEndFilter implements Filter {
    @Override
    public void doFilter(GatewayContext ctx) { }
}
