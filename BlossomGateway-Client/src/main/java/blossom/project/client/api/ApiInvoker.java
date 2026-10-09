package blossom.project.client.api;/**
 * @Author:Serendipity
 * @Date:
 * @Description:
 */

import java.lang.annotation.*;

/**
 * ApiInvoker注解
 * 当前注解必须在服务的方法上面强制声明
 */

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiInvoker {
    String path();
}
