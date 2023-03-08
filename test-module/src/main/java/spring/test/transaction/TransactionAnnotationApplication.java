package spring.test.transaction;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author wang.yubin
 * @since 2023/3/7
 */
@ComponentScan
@EnableTransactionManagement
public class TransactionAnnotationApplication {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext context =
				new AnnotationConfigApplicationContext(TransactionAnnotationApplication.class);

		TransactionService transactionService = context.getBean(TransactionService.class);
		transactionService.test1();
	}

	@Override
	public String toString() {
		return getClass().getName();
	}
}
