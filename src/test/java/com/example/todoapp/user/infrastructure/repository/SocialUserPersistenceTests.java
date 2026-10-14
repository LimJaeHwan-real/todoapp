package com.example.todoapp.user.infrastructure.repository;

import com.example.todoapp.user.infrastructure.mapper.UserMapper;
import com.example.todoapp.user.infrastructure.security.DatabaseUserDetailsService;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 명시적으로 지정한 테스트 DB에서만 실행한다. 테스트 전용 스키마와 모든 데이터를 롤백한다. */
class SocialUserPersistenceTests {
    @Test
    void migrationKeepsLocalAccountsAndUpsertReusesSocialMembersAndEnforcesIdentityConstraints() throws Exception {
        String url = System.getenv("TODOAPP_SOCIAL_TEST_DB_URL");
        assumeTrue(url != null && url.startsWith("jdbc:postgresql:"), "Dedicated social-login test DB is not configured.");
        Connection connection;
        try {
            DriverManager.setLoginTimeout(5);
            connection = DriverManager.getConnection(url, System.getenv("TODOAPP_SOCIAL_TEST_DB_USERNAME"),
                    System.getenv("TODOAPP_SOCIAL_TEST_DB_PASSWORD"));
        } catch (SQLException exception) {
            throw new IllegalStateException("Social-login test DB connection failed; details hidden.");
        }
        try (connection) {
            var dataSource = new SingleConnectionDataSource(connection, true);
            var manager = new DataSourceTransactionManager(dataSource);
            var transaction = manager.getTransaction(new DefaultTransactionDefinition());
            try {
                String schema = "social_test_" + UUID.randomUUID().toString().replace("-", "");
                String existingHash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                        .encode(UUID.randomUUID().toString());
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE SCHEMA " + schema);
                    statement.execute("SET LOCAL search_path TO " + schema);
                    statement.execute("CREATE TABLE users (id serial PRIMARY KEY, username varchar(50) NOT NULL UNIQUE, "
                            + "password_hash varchar(100) NOT NULL, CONSTRAINT users_bcrypt_hash CHECK "
                            + "(password_hash ~ '^\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}$'))");
                }
                try (var statement = connection.prepareStatement("INSERT INTO users (username, password_hash) VALUES (?, ?)")) {
                    statement.setString(1, "existing_member");
                    statement.setString(2, existingHash);
                    statement.executeUpdate();
                }
                applyMigrationInsideTransaction(connection);
                applyMigrationInsideTransaction(connection);
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
                var configuration = new Configuration();
                configuration.setLogImpl(NoLoggingImpl.class);
                var factory = new SqlSessionFactoryBean();
                factory.setConfiguration(configuration);
                factory.setDataSource(dataSource);
                factory.setMapperLocations(new ClassPathResource("mybatis/UserMapper.xml"));
                var repository = new UserRepositoryImpl(new SqlSessionTemplate(factory.getObject()).getMapper(UserMapper.class));
                var first = repository.findOrCreate("naver", "same-subject");
                var again = repository.findOrCreate("naver", "same-subject");
                var google = repository.findOrCreate("google", "same-subject");
                assertThat(first.getId()).isEqualTo(again.getId()).isNotEqualTo(google.getId());
                assertThat(first.getUsername()).isEqualTo(again.getUsername());
                assertThat(first.getPasswordHash() == null).isTrue();
                assertThat(repository.findByUsername("existing_member").orElseThrow().getPasswordHash().equals(existingHash)).isTrue();
                assertThatThrownBy(() -> new DatabaseUserDetailsService(repository).loadUserByUsername(first.getUsername()))
                        .isInstanceOf(UsernameNotFoundException.class);
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
                assertThat(repository.findOrCreate("naver", "same-subject").getId()).isEqualTo(first.getId());
                assertRejected(connection, "INSERT INTO users (username) VALUES ('missing_auth')");
                assertRejected(connection, "INSERT INTO users (username, provider_user_id) VALUES ('missing_provider', 'id')");
                assertRejected(connection, "INSERT INTO users (username, provider, provider_user_id, password_hash) "
                        + "VALUES ('mixed_auth', 'google', 'id', 'hash')");
                assertRejected(connection, "INSERT INTO users (username, provider, provider_user_id) "
                        + "VALUES ('duplicate_social', 'naver', 'same-subject')");
            } catch (SQLException | org.springframework.dao.DataAccessException exception) {
                throw new AssertionError("Social-login persistence verification failed; DB details hidden.");
            } finally {
                manager.rollback(transaction);
            }
        }
    }

    private static void applyMigrationInsideTransaction(Connection connection) throws Exception {
        try (var input = new ClassPathResource("sql/add-social-login.sql").getInputStream();
             var statement = connection.createStatement()) {
            // 파일의 트랜잭션 경계만 제거해 테스트 전체를 하나의 롤백 범위에 둔다. DO 블록은 그대로 실행한다.
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("(?m)^(BEGIN;|COMMIT;)\\r?$", "");
            statement.execute(sql);
        }
    }

    private static void assertRejected(Connection connection, String sql) throws SQLException {
        var savepoint = connection.setSavepoint();
        boolean rejected = false;
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException exception) {
            rejected = "23514".equals(exception.getSQLState()) || "23505".equals(exception.getSQLState());
        } finally {
            connection.rollback(savepoint);
        }
        assertThat(rejected).isTrue();
    }
}
