package io.zhijun.ratelimit.druid;

import static org.assertj.core.api.Assertions.*;

import com.alibaba.druid.pool.DruidDataSource;
import io.zhijun.ratelimit.autoconfigure.RateLimitAutoConfiguration;
import io.zhijun.ratelimit.autoconfigure.RateLimitSharedAutoConfiguration;
import io.zhijun.ratelimit.bucket4j.autoconfigure.Bucket4jRateLimitAutoConfiguration;
import io.zhijun.ratelimit.druid.autoconfigure.RateLimitDruidAutoConfiguration;
import io.zhijun.ratelimit.druid.autoconfigure.RateLimitDruidResolverAutoConfiguration;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class DruidJdbcRateLimitIntegrationTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    Bucket4jRateLimitAutoConfiguration.class,
                    RateLimitDruidResolverAutoConfiguration.class,
                    RateLimitSharedAutoConfiguration.class,
                    RateLimitAutoConfiguration.class,
                    RateLimitDruidAutoConfiguration.class));

    @Test
    void druidStatementIsRateLimited() {
        runner.withUserConfiguration(DataSourceApp.class)
                .withPropertyValues(
                        "spring.application.name=test",
                        "rose.ratelimit.filter-enabled=false",
                        "rose.ratelimit.rules[0].id=orders",
                        "rose.ratelimit.rules[0].stage=JDBC",
                        "rose.ratelimit.rules[0].target=jdbc:sql-table:orders",
                        "rose.ratelimit.rules[0].capacity=1",
                        "rose.ratelimit.rules[0].refill-period=1d")
                .run(context -> {
                    DruidDataSource dataSource = context.getBean(DruidDataSource.class);
                    try (Statement statement = dataSource.getConnection().createStatement()) {
                        statement.execute("CREATE TABLE IF NOT EXISTS orders (id INT PRIMARY KEY)");
                        statement.execute("INSERT INTO orders (id) VALUES (1)");
                        assertThatThrownBy(() -> statement.execute("INSERT INTO orders (id) VALUES (2)"))
                                .isInstanceOf(SQLException.class)
                                .extracting(ex -> ((SQLException) ex).getErrorCode())
                                .isEqualTo(429);
                    }
                });
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    static class DataSourceApp {
        @Bean(destroyMethod = "close")
        DruidDataSource dataSource() throws SQLException {
            DruidDataSource dataSource = new DruidDataSource();
            dataSource.setUrl("jdbc:h2:mem:druid-rl;DB_CLOSE_DELAY=-1");
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }
    }
}
