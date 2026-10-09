package io.aegisflow.client.api;

import java.lang.annotation.*;

/**
 * ApiService类
 * 服务定义
 */

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiService {

    String serviceId();

    String version() default "1.0.0";

    ApiProtocol protocol();

    String patternPath();

    String interfaceName() default "";
}
