package com.example.todoapp.todo.infrastructure.repository;

import com.example.todoapp.todo.domain.model.Todo;
import com.example.todoapp.todo.domain.model.TodoCursor;
import com.example.todoapp.todo.domain.service.TodoService;
import com.example.todoapp.todo.domain.service.TodoServiceImpl;
import com.example.todoapp.todo.infrastructure.mapper.TodoMapper;
import com.example.todoapp.todo.infrastructure.mapper.TodoMyBatisConfiguration;
import com.example.todoapp.todo.infrastructure.mapper.TodoRow;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.util.PropertyPlaceholderHelper;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** 기존 데이터와 분리된 스키마에서 검증하고 모든 변경을 롤백한다. */
class TodoSearchPersistenceTests {
    private Connection connection;
    private DataSourceTransactionManager transactionManager;
    private TransactionStatus transaction;
    private TodoMapper mapper;
    private TodoService service;
    private final LocalDateTime date = LocalDateTime.of(2026, 10, 14, 10, 0);

    @BeforeEach
    void isolatedPostgresSchema() throws Exception {
        Properties properties = new Properties();
        try (var input = new ClassPathResource("application.properties").getInputStream()) {
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        try {
            DriverManager.setLoginTimeout(5);
            connection = DriverManager.getConnection(property(properties, "spring.datasource.url"),
                    property(properties, "spring.datasource.username"), property(properties, "spring.datasource.password"));
        } catch (SQLException exception) {
            throw new IllegalStateException("PostgreSQL search test connection failed; details hidden.");
        }
        var dataSource = new SingleConnectionDataSource(connection, true);
        transactionManager = new DataSourceTransactionManager(dataSource);
        transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try (var statement = connection.createStatement()) {
            String schema = "todo_search_test_" + UUID.randomUUID().toString().replace("-", "");
            statement.execute("SET LOCAL statement_timeout = '30s'");
            statement.execute("CREATE SCHEMA " + schema);
            statement.execute("SET LOCAL search_path TO " + schema + ", public");
        }
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
        try (var context = new AnnotationConfigApplicationContext(TodoMyBatisConfiguration.class)) {
            var factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            Configuration configuration = new Configuration();
            context.getBean(ConfigurationCustomizer.class).customize(configuration);
            factory.setConfiguration(configuration);
            context.getBean(SqlSessionFactoryBeanCustomizer.class).customize(factory);
            mapper = new SqlSessionTemplate(factory.getObject()).getMapper(TodoMapper.class);
            service = new TodoServiceImpl(new TodoRepositoryImpl(mapper));
        }
    }

    @AfterEach
    void rollbackTestSchemaAndData() throws Exception {
        if (connection != null) {
            try {
                if (transaction != null) transactionManager.rollback(transaction);
                else if (!connection.getAutoCommit()) connection.rollback();
            } finally {
                connection.close();
            }
        }
    }

    @Test
    void cursorWalkAndReturnHandleEqualDatesAndNullDatesWithoutDuplicates() {
        for (int i = 1; i <= 27; i++) {
            insert("스프링 게시글 " + i, null, i <= 22 ? date : null);
        }
        var first = service.search("스프링", null, false);
        assertThat(first.items()).extracting(Todo::getId).containsExactly(22, 21, 20, 19, 18, 17, 16, 15, 14, 13);
        var second = service.search("스프링", TodoCursor.from(first.items().getLast()), false);
        assertThat(second.items()).extracting(Todo::getId).containsExactly(12, 11, 10, 9, 8, 7, 6, 5, 4, 3);
        var third = service.search("스프링", TodoCursor.from(second.items().getLast()), false);
        assertThat(third.items()).extracting(Todo::getId).containsExactly(2, 1, 27, 26, 25, 24, 23);
        assertThat(third.hasMore()).isFalse();
        var previous = service.search("스프링", TodoCursor.from(third.items().getFirst()), true);
        assertThat(previous.items()).extracting(Todo::getId).containsExactly(12, 11, 10, 9, 8, 7, 6, 5, 4, 3);
        assertThat(previous.hasMore()).isTrue();
        var oldest = service.search("스프링", new TodoCursor(null, 25), false);
        assertThat(oldest.items()).extracting(Todo::getId).containsExactly(24, 23);
        var beforeNull = service.search("스프링", new TodoCursor(null, 25), true);
        assertThat(beforeNull.items()).extracting(Todo::getId).containsExactly(8, 7, 6, 5, 4, 3, 2, 1, 27, 26);
        List<Integer> ids = new ArrayList<>();
        for (var slice : List.of(first, second, third)) {
            ids.addAll(slice.items().stream().map(Todo::getId).toList());
        }
        assertThat(ids).hasSize(27).doesNotHaveDuplicates();
    }

    @Test
    void substringSearchIsTitleOnlyCaseInsensitiveAndEscapesUserWildcards() {
        int literal = insert("SQL100%_! 스프링", null, date);
        insert("SQL100ABC 스프링", null, date);
        insert("제목에 검색어 없음", "내용에만 spring", date);
        int english = insert("Learning SPRING today", null, date);
        assertThat(service.search("100%_!", null, false).items()).extracting(Todo::getId).containsExactly(literal);
        assertThat(service.search("spring", null, false).items()).extracting(Todo::getId).containsExactly(english);
        assertThat(service.search("없는검색어", null, false).items()).isEmpty();
    }

    @Test
    void deletedAnchorDoesNotBreakNextPageAndNewerInsertsDoNotShiftIt() {
        for (int i = 1; i <= 21; i++) insert("스프링 " + i, null, date);
        var first = service.search("스프링", null, false);
        var cursor = TodoCursor.from(first.items().getLast());
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM todos WHERE id = 12");
        } catch (SQLException exception) {
            throw new IllegalStateException("Isolated fixture operation failed.");
        }
        insert("스프링 새 글", null, date.plusDays(1));
        assertThat(service.search("스프링", cursor, false).items()).extracting(Todo::getId)
                .containsExactly(11, 10, 9, 8, 7, 6, 5, 4, 3, 2);
    }

    @Test
    void selectiveSearchUsesGinIndexWithOneHundredThousandRows() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO todos (todo, created_at) SELECT '일반 게시글 ' || n, "
                    + "TIMESTAMP '2026-10-14' FROM generate_series(1, 100000) n");
            statement.executeUpdate("INSERT INTO todos (todo, created_at) VALUES ('특별검색어 제목', TIMESTAMP '2026-10-14')");
            statement.execute("ANALYZE todos");
            StringBuilder plan = new StringBuilder();
            try (var results = statement.executeQuery("EXPLAIN (ANALYZE, BUFFERS) SELECT id, todo, created_at FROM todos "
                    + "WHERE todo ILIKE '%특별검색어%' ESCAPE '!' ORDER BY COALESCE(created_at, '-infinity'::timestamp) DESC, id DESC LIMIT 11")) {
                while (results.next()) plan.append(results.getString(1)).append('\n');
            }
            assertThat(plan.toString()).contains("idx_todos_title_trgm");
            System.out.println("Isolated 100000-row selective search plan:\n" + plan);
            StringBuilder deepPlan = new StringBuilder();
            try (var results = statement.executeQuery("EXPLAIN (ANALYZE, BUFFERS) SELECT id, todo, created_at FROM todos "
                    + "WHERE (COALESCE(created_at, '-infinity'::timestamp), id) < (TIMESTAMP '2026-10-14', 1000) "
                    + "ORDER BY COALESCE(created_at, '-infinity'::timestamp) DESC, id DESC LIMIT 11")) {
                while (results.next()) deepPlan.append(results.getString(1)).append('\n');
            }
            assertThat(deepPlan.toString()).contains("Index Cond:", "idx_todos_created_id")
                    .doesNotContain("Rows Removed by Filter");
            System.out.println("Isolated deep cursor plan:\n" + deepPlan);
        }
        assertThat(service.search("특별검색어", null, false).items()).extracting(Todo::getTitle).containsExactly("특별검색어 제목");
    }

    private int insert(String title, String detail, LocalDateTime createdAt) {
        TodoRow row = new TodoRow();
        row.setTitle(title);
        row.setDetail(detail);
        row.setCreatedAt(createdAt);
        mapper.insert(row);
        return row.getId();
    }

    private static String property(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) return null;
        return new PropertyPlaceholderHelper("${", "}")
                .replacePlaceholders(value, name -> properties.getProperty(name, System.getenv(name)));
    }
}
