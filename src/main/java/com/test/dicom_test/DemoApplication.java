package com.test.dicom_test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
    	System.out.println("Iniciando...");
        SpringApplication.run(DemoApplication.class, args);
    }
}