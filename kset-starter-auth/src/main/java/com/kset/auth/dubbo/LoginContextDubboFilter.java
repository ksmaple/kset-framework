package com.kset.auth.dubbo;

import com.kset.auth.config.KsetAuthProperties;
import com.kset.auth.spi.LoginUserHeaderCodec;
import com.kset.common.auth.LoginContext;
import com.kset.common.auth.LoginContextSnapshot;
import com.kset.common.auth.LoginUser;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;

import java.util.Map;
import java.util.Optional;

public class LoginContextDubboFilter implements Filter {

    private final KsetAuthProperties properties;
    private final LoginUserHeaderCodec headerCodec;

    public LoginContextDubboFilter(KsetAuthProperties properties, LoginUserHeaderCodec headerCodec) {
        this.properties = properties;
        this.headerCodec = headerCodec;
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        RpcContext context = RpcContext.getServiceContext();
        LoginContextSnapshot previous = LoginContext.capture();
        if (context.isConsumerSide()) {
            LoginContext.currentUser().ifPresent(user -> propagate(invocation, user));
            return invoker.invoke(invocation);
        }
        if (context.isProviderSide()) {
            String subject = attachment(invocation, com.kset.common.auth.AuthHeaders.AUTH_SUBJECT);
            headerCodec.decode(name -> attachment(invocation, name), subject, null).ifPresent(LoginContext::bind);
        }
        try {
            return invoker.invoke(invocation);
        } finally {
            LoginContext.restore(previous);
        }
    }

    private void propagate(Invocation invocation, LoginUser user) {
        for (Map.Entry<String, String> entry : headerCodec.encode(
                user, properties.getDubbo().isPropagateToken(), user.getSubjectType()).entrySet()) {
            invocation.setAttachment(entry.getKey(), entry.getValue());
        }
    }

    private String attachment(Invocation invocation, String name) {
        String value = invocation.getAttachment(name);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return RpcContext.getServiceContext().getAttachment(name);
    }
}
