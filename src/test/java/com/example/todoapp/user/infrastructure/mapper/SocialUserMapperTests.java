package com.example.todoapp.user.infrastructure.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SocialUserMapperTests {
    @Test
    void upsertBindsUntrustedSubjectAndMarksReturningStatementAsAWrite() throws Exception {
        var configuration = new Configuration();
        configuration.setLogImpl(NoLoggingImpl.class);
        try (var input = new ClassPathResource("mybatis/UserMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input, configuration, "mybatis/UserMapper.xml", configuration.getSqlFragments()).parse();
        }
        var statement = configuration.getMappedStatement(UserMapper.class.getName() + ".findOrCreateSocialUser");
        var sql = statement.getBoundSql(Map.of("provider", "naver", "subject", "' OR 1=1 --", "username", "naver_member"));
        assertThat(sql.getSql().replaceAll("\\s+", " ").strip()).isEqualTo(
                "INSERT INTO users (username, password_hash, provider, provider_user_id) VALUES (?, NULL, ?, ?) "
                        + "ON CONFLICT (provider, provider_user_id) DO UPDATE SET provider = EXCLUDED.provider "
                        + "RETURNING id, username, password_hash");
        assertThat(sql.getParameterMappings()).extracting("property").containsExactly("username", "provider", "subject");
        assertThat(statement.isDirtySelect()).isTrue();
        assertThat(statement.isFlushCacheRequired()).isTrue();
    }
}
