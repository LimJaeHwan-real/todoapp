package com.example.todoapp.todo.infrastructure.mapper;

import org.mybatis.spring.boot.autoconfigure.SqlSessionFactoryBeanCustomizer;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
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
        return factory -> factory.setMapperLocations(
                new ClassPathResource("mybatis/TodoMapper.xml"),
                new ClassPathResource("mybatis/UserMapper.xml"));
    }

    @Bean
    public ConfigurationCustomizer sensitiveSqlLoggingCustomizer() {
        // 로컬 StdOutImpl 설정이 있어도 계정 아이디와 비밀번호 해시를 출력하지 않는다.
        return configuration -> configuration.setLogImpl(NoLoggingImpl.class);
    }
}
