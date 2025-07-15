package com.example;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.example.project.mapper")
public class KafkaTestJava8Application {
    public static void main(String[] args) {
        SpringApplication.run(KafkaTestJava8Application.class, args);
    }

    @Bean
    public ApplicationRunner dumpMappedStatements(SqlSessionFactory factory) {
        return args -> {
            System.out.println("▶ MyBatis에 등록된 MappedStatement 목록 ▶");
            factory.getConfiguration()
                    .getMappedStatementNames()
                    .forEach(System.out::println);
        };
    }
}
