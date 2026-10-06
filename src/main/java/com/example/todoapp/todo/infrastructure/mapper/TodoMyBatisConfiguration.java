package com.example.todoapp.todo.infrastructure.mapper;

import org.mybatis.spring.boot.autoconfigure.SqlSessionFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * 로컬 연결 설정과 분리하여 계획서에 정한 매퍼 XML 위치를 연결한다.
 */
@Configuration(proxyBeanMethods = false)
public class TodoMyBatisConfiguration {

    @Bean
    public SqlSessionFactoryBeanCustomizer todoMapperLocationsCustomizer() {
        return factory -> factory.setMapperLocations(new ClassPathResource("mybatis/TodoMapper.xml"));
    }
}
