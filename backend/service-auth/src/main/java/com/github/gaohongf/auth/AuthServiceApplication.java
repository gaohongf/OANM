package com.github.gaohongf.auth;


import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
@EnableDiscoveryClient 
@SpringBootApplication 
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }

    @Autowired 
    public void data(DataSource dataSource){
        try {
            dataSource.getConnection();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}