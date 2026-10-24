package com.canalesdigitales.contactupdate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ContactUpdateServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContactUpdateServiceApplication.class, args);
    }

}
