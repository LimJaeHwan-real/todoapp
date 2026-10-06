package com.example.todoapp.todo.domain.service;

import com.example.todoapp.todo.domain.model.Todo;
import com.example.todoapp.todo.domain.repository.TodoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TodoServiceTransactionTests {

    private AnnotationConfigApplicationContext context;
    private TodoService service;
    private TodoRepository repository;
    private RecordingTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        service = context.getBean(TodoService.class);
        repository = context.getBean(TodoRepository.class);
        transactionManager = context.getBean(RecordingTransactionManager.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void repositoryReadsRunInsideReadOnlyTransactions() {
        when(repository.findAll()).thenAnswer(invocation -> {
            assertTransaction(true);
            return List.of(existingTodo());
        });
        when(repository.findById(7)).thenAnswer(invocation -> {
            assertTransaction(true);
            return Optional.of(existingTodo());
        });

        assertThat(service.findAll()).extracting(Todo::getId).containsExactly(7);
        assertThat(service.findById(7).getId()).isEqualTo(7);
        assertThat(transactionManager.commits).isEqualTo(2);
    }

    @Test
    void createUpdateAndDeleteRunInsideWriteTransactions() {
        when(repository.insert(any(Todo.class))).thenAnswer(invocation -> {
            assertTransaction(false);
            return 42;
        });
        when(repository.findById(7)).thenAnswer(invocation -> {
            assertTransaction(false);
            return Optional.of(existingTodo());
        });
        when(repository.update(any(Todo.class))).thenAnswer(invocation -> {
            assertTransaction(false);
            return 1;
        });
        when(repository.deleteById(7)).thenAnswer(invocation -> {
            assertTransaction(false);
            return 1;
        });

        assertThat(service.create("등록 제목", null)).isEqualTo(42);
        service.update(7, "수정 제목", null);
        service.deleteById(7);

        assertThat(transactionManager.commits).isEqualTo(3);
        assertThat(transactionManager.rollbacks).isZero();
    }

    @Test
    void databaseFailureRollsBackWriteTransactionAndPropagatesOriginalException() {
        var failure = new DataAccessResourceFailureException("Database unavailable");
        when(repository.insert(any(Todo.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.create("등록 제목", null)).isSameAs(failure);
        assertThat(transactionManager.rollbacks).isEqualTo(1);
        assertThat(transactionManager.commits).isZero();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    private static void assertTransaction(boolean readOnly) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isEqualTo(readOnly);
    }

    private static Todo existingTodo() {
        LocalDateTime date = LocalDateTime.of(2026, 10, 5, 9, 0);
        return new Todo(7, "기존 제목", null, date, date);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import(TodoServiceImpl.class)
    static class TestConfiguration {

        @Bean
        TodoRepository todoRepository() {
            return mock(TodoRepository.class);
        }

        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }
    }

    private static class RecordingTransactionManager extends AbstractPlatformTransactionManager {

        private int commits;
        private int rollbacks;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }
}
