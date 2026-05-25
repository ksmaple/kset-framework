package com.kset.cloud.dubbo.route;

import com.kset.cloud.trace.TraceContext;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.Router;
import org.apache.dubbo.rpc.cluster.RouterFactory;
import org.apache.dubbo.rpc.cluster.router.RouterResult;
import org.slf4j.MDC;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 基于灰度标签与权重配置的 Dubbo 路由
 */
@Activate(order = 1)
public class KsetTagRouterFactory implements RouterFactory {

    @Override
    public Router getRouter(org.apache.dubbo.common.URL url) {
        return new KsetTagRouter(url);
    }

    static class KsetTagRouter implements Router {

        private final org.apache.dubbo.common.URL url;

        KsetTagRouter(org.apache.dubbo.common.URL url) {
            this.url = url;
        }

        @Override
        public <T> RouterResult<Invoker<T>> route(List<Invoker<T>> invokers, org.apache.dubbo.common.URL url,
                                                  Invocation invocation, boolean needToPrintMessage) throws RpcException {
            if (invokers == null || invokers.isEmpty()) {
                return new RouterResult<>(invokers);
            }

            String grayTag = resolveGrayTag(invocation);
            String metadataKey = url.getParameter("kset.gray.metadata.key", DubboRouteRuleHolder.getMetadataKey());

            List<Invoker<T>> matched = invokers.stream()
                    .filter(inv -> grayTag.equals(inv.getUrl().getParameter(metadataKey)))
                    .collect(Collectors.toList());

            if (matched.isEmpty()) {
                matched = filterByRouteRules(invokers);
            }
            if (matched.isEmpty()) {
                matched = invokers;
            }
            return new RouterResult<>(matched);
        }

        private <T> List<Invoker<T>> filterByRouteRules(List<Invoker<T>> invokers) {
            List<DubboRouteRuleHolder.RouteCondition> conditions = DubboRouteRuleHolder.getConditions();
            if (conditions.isEmpty()) {
                return List.of();
            }

            int totalWeight = conditions.stream().mapToInt(DubboRouteRuleHolder.RouteCondition::getWeight).sum();
            int random = ThreadLocalRandom.current().nextInt(Math.max(totalWeight, 1));
            int current = 0;
            String selectedTag = conditions.get(0).getTag();
            for (DubboRouteRuleHolder.RouteCondition condition : conditions) {
                current += condition.getWeight();
                if (random < current) {
                    selectedTag = condition.getTag();
                    break;
                }
            }

            String tag = selectedTag;
            String metadataKey = DubboRouteRuleHolder.getMetadataKey();
            return invokers.stream()
                    .filter(inv -> tag.equals(inv.getUrl().getParameter(metadataKey)))
                    .collect(Collectors.toList());
        }

        private String resolveGrayTag(Invocation invocation) {
            String grayTag = invocation.getAttachment(TraceContext.GRAY_TAG_KEY);
            if (grayTag != null && !grayTag.isBlank()) {
                return grayTag;
            }
            grayTag = MDC.get(TraceContext.GRAY_TAG_KEY);
            return grayTag != null ? grayTag : "stable";
        }

        @Override
        public org.apache.dubbo.common.URL getUrl() {
            return url;
        }

        @Override
        public boolean isRuntime() {
            return true;
        }

        @Override
        public boolean isForce() {
            return false;
        }

        @Override
        public int getPriority() {
            return 0;
        }
    }
}
