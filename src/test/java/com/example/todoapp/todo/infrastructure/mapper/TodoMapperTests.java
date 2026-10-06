package com.example.todoapp.todo.infrastructure.mapper;

import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.boot.autoconfigure.SqlSessionFactoryBeanCustomizer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TodoMapperTests {

    private Configuration configuration;

    @BeforeEach
    void loadMapperUsingInfrastructureConfigurationWithoutConnectingToDatabase() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(TodoMyBatisConfiguration.class)) {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(mock(DataSource.class));
            context.getBean(SqlSessionFactoryBeanCustomizer.class).customize(factory);
            configuration = factory.getObject().getConfiguration();
        }
    }

    @Test
    void findAllUsesPlannedSortOrderAndExplicitColumnMapping() {
        MappedStatement statement = statement("findAll");

        assertThat(sql(statement.getBoundSql(null)))
                .isEqualTo("SELECT id, todo, detail, created_at, updated_at FROM todos "
                        + "ORDER BY created_at DESC NULLS LAST, id DESC");
        assertThat(statement.getResultMaps().getFirst().getResultMappings())
                .extracting(ResultMapping::getColumn, ResultMapping::getProperty)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("id", "id"),
                        org.assertj.core.groups.Tuple.tuple("todo", "title"),
                        org.assertj.core.groups.Tuple.tuple("detail", "detail"),
                        org.assertj.core.groups.Tuple.tuple("created_at", "createdAt"),
                        org.assertj.core.groups.Tuple.tuple("updated_at", "updatedAt"));
    }

    @Test
    void findByIdBindsIdInsteadOfInterpolatingIt() {
        BoundSql boundSql = statement("findById").getBoundSql(Map.of("id", 7));

        assertThat(sql(boundSql)).isEqualTo(
                "SELECT id, todo, detail, created_at, updated_at FROM todos WHERE id = ?");
        assertThat(boundSql.getParameterMappings()).extracting(ParameterMapping::getProperty)
                .containsExactly("id");
    }

    @Test
    void insertBindsUserInputAndRetrievesDatabaseGeneratedId() {
        TodoRow row = new TodoRow();
        row.setTitle("'; DROP TABLE todos; --");
        row.setCreatedAt(LocalDateTime.of(2026, 10, 6, 9, 0));
        MappedStatement statement = statement("insert");
        BoundSql boundSql = statement.getBoundSql(row);

        assertThat(sql(boundSql)).isEqualTo(
                "INSERT INTO todos (todo, detail, created_at, updated_at) VALUES (?, ?, ?, ?)");
        assertThat(boundSql.getParameterMappings()).extracting(ParameterMapping::getProperty)
                .containsExactly("title", "detail", "createdAt", "updatedAt");
        assertThat(statement.getKeyGenerator()).isInstanceOf(Jdbc3KeyGenerator.class);
        assertThat(statement.getKeyProperties()).containsExactly("id");
        assertThat(statement.getKeyColumns()).containsExactly("id");
    }

    @Test
    void updateOnlyChangesTitleDetailAndUpdatedAtForSelectedId() {
        BoundSql boundSql = statement("update").getBoundSql(new TodoRow());

        assertThat(sql(boundSql)).isEqualTo(
                "UPDATE todos SET todo = ?, detail = ?, updated_at = ? WHERE id = ?");
        assertThat(boundSql.getParameterMappings()).extracting(ParameterMapping::getProperty)
                .containsExactly("title", "detail", "updatedAt", "id");
    }

    @Test
    void deleteOnlyTargetsBoundId() {
        BoundSql boundSql = statement("deleteById").getBoundSql(Map.of("id", 7));

        assertThat(sql(boundSql)).isEqualTo("DELETE FROM todos WHERE id = ?");
        assertThat(boundSql.getParameterMappings()).extracting(ParameterMapping::getProperty)
                .containsExactly("id");
    }

    private MappedStatement statement(String method) {
        return configuration.getMappedStatement(TodoMapper.class.getName() + "." + method);
    }

    private String sql(BoundSql boundSql) {
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
    }
}
