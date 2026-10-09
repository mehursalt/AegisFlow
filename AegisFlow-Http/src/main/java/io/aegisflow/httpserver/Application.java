package io.aegisflow.httpserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Hello world!
 *
 */
@SpringBootApplication(scanBasePackages = "io.aegisflow")
public class Application {


    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
