package com.example.todoapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.sql.init.mode=never")
class TodoappApplicationTests {

	@Autowired
	private SqlSessionFactory sqlSessionFactory;

	@Test
	void contextLoads() {
		assertThat(sqlSessionFactory.getConfiguration().getLogImpl()).isEqualTo(NoLoggingImpl.class);
	}

}
