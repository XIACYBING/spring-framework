package spring.test.transaction;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * @author wang.yubin
 * @since 2023/3/7
 */
@Configuration
@EnableTransactionManagement
public class TransactionConfiguration {

	@Bean
	public DataSource dataSource() throws ClassNotFoundException {
		// 初始化驱动
		Class.forName("com.mysql.cj.jdbc.Driver");

		return new SingleConnectionDataSource("jdbc:mysql://localhost:3306/test?createDatabaseIfNotExist=true", "root",
				"root@root", true);
	}

	@Bean
	public TransactionManager transactionManager(DataSource dataSource) {
		return new DataSourceTransactionManager(dataSource);
	}

}
