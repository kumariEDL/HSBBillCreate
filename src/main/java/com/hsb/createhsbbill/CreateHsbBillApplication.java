package com.hsb.createhsbbill;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * The starting point of the application.
 *
 * There are two ways this app can start:
 * 1. As a .war file, deployed inside WildFly - this is how it will
 * really be used. WildFly calls configure(...) below to start it;
 * main() is not used in this case.
 * 2. Directly, by running main() - useful only to check the code
 * compiles and starts. This way has NO access to the real database,
 * because the database connection (java:/InformixDSSPS) is only
 * available when WildFly provides it - see application.properties.
 */
@SpringBootApplication
public class CreateHsbBillApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(CreateHsbBillApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(CreateHsbBillApplication.class);
    }
}
