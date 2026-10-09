package com.example.todoapp.user.infrastructure.repository;

import com.example.todoapp.todo.infrastructure.mapper.TodoMyBatisConfiguration;
import com.example.todoapp.todo.infrastructure.mapper.TodoMapper;
import com.example.todoapp.todo.infrastructure.repository.TodoRepositoryImpl;
import com.example.todoapp.todo.domain.service.TodoServiceImpl;
import com.example.todoapp.todo.domain.service.TodoAccessDeniedException;
import com.example.todoapp.user.domain.model.User;
import com.example.todoapp.user.domain.service.DuplicateUsernameException;
import com.example.todoapp.user.domain.service.UserRegistrationService;
import com.example.todoapp.user.infrastructure.mapper.UserMapper;
import com.example.todoapp.user.infrastructure.security.DatabaseUserDetailsService;
import com.example.todoapp.user.infrastructure.security.SecurityConfiguration;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.mybatis.spring.boot.autoconfigure.SqlSessionFactoryBeanCustomizer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.util.PropertyPlaceholderHelper;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 기존 데이터와 분리된 PostgreSQL 스키마에서 검증하고 모든 변경을 롤백한다. */
class UserPersistenceTests {

    @Test
    void postgresStoresHashesRejectsDuplicatesAndPreservesDataOnRepeatedInitialization() throws Exception {
        Properties properties = new Properties();
        try (var input = new ClassPathResource("application.properties").getInputStream()) {
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        String url = property(properties, "spring.datasource.url");
        if (url == null || !url.startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException("PostgreSQL test connection is not configured; values hidden.");
        }
        Connection connection;
        try {
            DriverManager.setLoginTimeout(5);
            connection = DriverManager.getConnection(url,
                    property(properties, "spring.datasource.username"),
                    property(properties, "spring.datasource.password"));
        } catch (SQLException exception) {
            throw new IllegalStateException("PostgreSQL test connection failed; details hidden.");
        }
        try (connection) {
            var dataSource = new SingleConnectionDataSource(connection, true);
            var transactionManager = new DataSourceTransactionManager(dataSource);
            var transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
            try {
                String schema = "security_test_" + UUID.randomUUID().toString().replace("-", "");
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE SCHEMA " + schema);
                    statement.execute("SET LOCAL search_path TO " + schema);
                    // 변경 전 구조의 테이블에서 작성자 컬럼 추가를 검증한다.
                    statement.execute("CREATE TABLE todos (id serial PRIMARY KEY, todo varchar(255) NOT NULL, "
                            + "detail text, created_at timestamp, updated_at timestamp)");
                }
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("data.sql"));
                applyAuthorMigration(connection);
                applyAuthorMigration(connection);
                try (var statement = connection.createStatement();
                     var rows = statement.executeQuery("SELECT count(*), count(author_id) FROM todos")) {
                    rows.next();
                    assertThat(rows.getInt(1)).isEqualTo(3);
                    assertThat(rows.getInt(2)).isZero();
                }
                try (var context = new AnnotationConfigApplicationContext(TodoMyBatisConfiguration.class)) {
                    var factory = new SqlSessionFactoryBean();
                    factory.setDataSource(dataSource);
                    Configuration configuration = new Configuration();
                    context.getBean(ConfigurationCustomizer.class).customize(configuration);
                    factory.setConfiguration(configuration);
                    context.getBean(SqlSessionFactoryBeanCustomizer.class).customize(factory);
                    var sessions = new SqlSessionTemplate(factory.getObject());
                    var repository = new UserRepositoryImpl(sessions.getMapper(UserMapper.class));
                    var security = new SecurityConfiguration();
                    var encoder = security.passwordEncoder();
                    var service = new UserRegistrationService(repository, security.passwordHasher(encoder));
                    String raw = UUID.randomUUID().toString();
                    service.register("member", raw);
                    User stored = repository.findByUsername("member").orElseThrow();
                    assertThat(stored.getId()).isPositive();
                    assertThat(stored.getPasswordHash().equals(raw)).isFalse();
                    assertThat(encoder.matches(raw, stored.getPasswordHash())).isTrue();
                    var details = new DatabaseUserDetailsService(repository).loadUserByUsername("member");
                    assertThat(encoder.matches(raw, details.getPassword())).isTrue();
                    assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");

                    service.register("other", UUID.randomUUID().toString());
                    int otherId = repository.findByUsername("other").orElseThrow().getId();
                    var todoMapper = sessions.getMapper(TodoMapper.class);
                    var todoService = new TodoServiceImpl(new TodoRepositoryImpl(todoMapper));
                    int todoId = todoService.create("owner item", "original", stored.getId());
                    assertThat(todoService.findById(todoId).getAuthorId()).isEqualTo(stored.getId());
                    assertThatThrownBy(() -> todoService.findByIdForEdit(todoId, otherId))
                            .isInstanceOf(TodoAccessDeniedException.class);
                    assertThatThrownBy(() -> todoService.update(todoId, "changed", null, otherId))
                            .isInstanceOf(TodoAccessDeniedException.class);
                    assertThatThrownBy(() -> todoService.deleteById(todoId, otherId))
                            .isInstanceOf(TodoAccessDeniedException.class);
                    var forged = todoMapper.findById(todoId);
                    forged.setAuthorId(otherId);
                    forged.setTitle("forged");
                    assertThat(todoMapper.update(forged)).isZero();
                    assertThat(todoMapper.deleteById(todoId, otherId)).isZero();
                    assertThat(todoService.findById(todoId).getTitle()).isEqualTo("owner item");
                    assertThatThrownBy(() -> todoService.update(1, "legacy changed", null, stored.getId()))
                            .isInstanceOf(TodoAccessDeniedException.class);
                    assertThatThrownBy(() -> todoService.deleteById(1, stored.getId()))
                            .isInstanceOf(TodoAccessDeniedException.class);
                    todoService.update(todoId, "owner updated", null, stored.getId());
                    assertThat(todoService.findById(todoId).getTitle()).isEqualTo("owner updated");
                    assertThat(todoService.findById(todoId).getAuthorId()).isEqualTo(stored.getId());
                    todoService.deleteById(todoId, stored.getId());
                    assertThat(todoMapper.findById(todoId) == null).isTrue();

                    // 같은 스키마·예시를 다시 실행해도 계정과 기존 할일이 유지되어야 한다.
                    try (var statement = connection.createStatement()) {
                        statement.executeUpdate("UPDATE todos SET detail = 'preserved' WHERE id = 1");
                    }
                    ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
                    ScriptUtils.executeSqlScript(connection, new ClassPathResource("data.sql"));
                    assertThat(repository.findByUsername("member").orElseThrow().getPasswordHash()
                            .equals(stored.getPasswordHash())).isTrue();
                    try (var statement = connection.createStatement();
                         var rows = statement.executeQuery("SELECT count(*), count(*) FILTER (WHERE detail = 'preserved') FROM todos")) {
                        rows.next();
                        assertThat(rows.getInt(1)).isEqualTo(3);
                        assertThat(rows.getInt(2)).isEqualTo(1);
                    }

                    // UNIQUE 제약으로 동시 가입 상황의 중복 저장도 차단하는지 확인한다.
                    var savepoint = connection.setSavepoint();
                    assertThatThrownBy(() -> repository.insert(stored)).isInstanceOf(DuplicateUsernameException.class);
                    connection.rollback(savepoint);
                    var constraintSavepoint = connection.setSavepoint();
                    boolean rejected = false;
                    try (var statement = connection.prepareStatement("INSERT INTO users (username, password_hash) VALUES (?, ?)")) {
                        statement.setString(1, "plaintext_check");
                        statement.setString(2, raw);
                        statement.executeUpdate();
                    } catch (SQLException exception) {
                        rejected = "23514".equals(exception.getSQLState());
                    }
                    assertThat(rejected).isTrue();
                    connection.rollback(constraintSavepoint);
                }
            } finally {
                // 전용 스키마와 테스트 데이터는 모두 롤백한다.
                transactionManager.rollback(transaction);
            }
        }
    }

    private static void applyAuthorMigration(Connection connection) throws Exception {
        try (var input = new ClassPathResource("sql/add-todo-author.sql").getInputStream();
             var statement = connection.createStatement()) {
            statement.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static String property(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            return null;
        }
        return new PropertyPlaceholderHelper("${", "}")
                .replacePlaceholders(value, name -> properties.getProperty(name, System.getenv(name)));
    }
}
