package io.zhijun.ratelimit.mybatis;

import static org.assertj.core.api.Assertions.*;

import io.zhijun.ratelimit.autoconfigure.RateLimitAutoConfiguration;
import io.zhijun.ratelimit.autoconfigure.RateLimitSharedAutoConfiguration;
import io.zhijun.ratelimit.bucket4j.autoconfigure.Bucket4jRateLimitAutoConfiguration;
import io.zhijun.ratelimit.mybatis.autoconfigure.RateLimitMybatisAutoConfiguration;
import io.zhijun.ratelimit.mybatis.autoconfigure.RateLimitMybatisResolverAutoConfiguration;
import io.zhijun.ratelimit.web.RateLimitRules;
import java.sql.SQLException;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class MybatisRateLimitIntegrationTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    Bucket4jRateLimitAutoConfiguration.class,
                    RateLimitMybatisResolverAutoConfiguration.class,
                    RateLimitSharedAutoConfiguration.class,
                    RateLimitAutoConfiguration.class,
                    RateLimitMybatisAutoConfiguration.class));

    @Test
    void mappedStatementIsRateLimited() {
        String statementId = DemoMapper.class.getName() + ".count";
        runner.withUserConfiguration(MybatisApp.class)
                .withPropertyValues(
                        "spring.application.name=test",
                        "rose.ratelimit.filter-enabled=false",
                        "rose.ratelimit.rules[0].id=stmt",
                        "rose.ratelimit.rules[0].stage=MYBATIS",
                        "rose.ratelimit.rules[0].target=mybatis:*DemoMapper.count",
                        "rose.ratelimit.rules[0].capacity=1",
                        "rose.ratelimit.rules[0].refill-period=1d")
                .run(context -> {
                    assertThat(context.getBean(RateLimitRules.class).mybatisIndexReady())
                            .isTrue();
                    assertThat(context.getBean(RateLimitRules.class).externalMybatisRule(statementId))
                            .isNotNull();
                    SqlSessionFactory factory = context.getBean(SqlSessionFactory.class);
                    try (var session = factory.openSession(true)) {
                        DemoMapper mapper = session.getMapper(DemoMapper.class);
                        assertThat(mapper.count()).isEqualTo(1);
                        assertThatThrownBy(mapper::count).rootCause().isInstanceOf(SQLException.class);
                    }
                });
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    static class MybatisApp {
        @Bean
        SqlSessionFactory sqlSessionFactory() {
            UnpooledDataSource dataSource = new UnpooledDataSource();
            dataSource.setDriver("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:ratelimit;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
            org.apache.ibatis.session.Configuration configuration =
                    new org.apache.ibatis.session.Configuration(environment);
            configuration.addMapper(DemoMapper.class);
            return new SqlSessionFactoryBuilder().build(configuration);
        }
    }

    interface DemoMapper {
        @Select("select 1")
        int count();
    }
}
