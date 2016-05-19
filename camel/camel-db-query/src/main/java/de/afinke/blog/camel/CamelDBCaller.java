package de.afinke.blog.camel;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.apache.commons.dbcp2.BasicDataSource;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

public class CamelDBCaller extends Main {

    public static void main(String[] args) throws Exception {
        CamelDBCaller main = new CamelDBCaller();
        main.addRouteBuilder(createRouteBuilder());
        main.bind("worldDS", createWorldDataSource());
        main.run(args);
    }

    static RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer://foo?fixedRate=true&period=5s")
                        .setHeader("cc", constant("DEU"))
                        .setBody(simple("select * from City where CountryCode = :?cc"))
                        .to("jdbc:worldDS?useHeadersAsParameters=true")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                // get results
                                List<Map<String, Object>> data = exchange.getIn().getBody(List.class);
                                // get the first row
                                Map<String, Object> row = data.get(0);
                                System.out.println(row.get("Name"));
                            }
                        });
            }
        };
    }

    /*
        World database can be downloaded at https://dev.mysql.com/doc/index-other.html.
     */
    private static DataSource createWorldDataSource() {
        BasicDataSource ds = new BasicDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUsername("root");
        ds.setPassword("");
        ds.setUrl("jdbc:mysql://localhost:3306/world?serverTimezone=UTC&useSSL=false");
        return ds;
    }

}
