package io.aegisflow.dubbo;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Hello world!
 *
 */
@SpringBootApplication(scanBasePackages = "io.aegisflow")
@EnableDubbo
public class DubboWebApplication 
{
    public static void main( String[] args )
    {
        SpringApplication.run(DubboWebApplication.class,args);
    }
}
