package org.openbaton.api.occi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Created by pku on 05.02.16.
 */
@SpringBootApplication
@ComponentScan(basePackages = "org.openbaton")
public class Startup {

    public static void main(String[] args) {
        SpringApplication.run(Startup.class);
    }
}
