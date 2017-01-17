package de.afinke.blog.camel;

import org.apache.camel.builder.RouteBuilder;

import java.util.Map;

public class DbTransferRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("sqlWorldOne:select * from city where processed = 0?onConsume=update City set processed = 1 where id = :#id")
                .transacted()
                .to("sqlWorldTwo:insert into city (id, name, countrycode, district, population) values (:#id, :#name, :#countrycode, :#district, :#population)")
                .process(exchange -> {
                    Map values = exchange.getIn().getBody(Map.class);
                    int id = (int) values.get("id");
                    if (1 == id) {
                        throw new RuntimeException("Testing transactions!");
                    }
                });
    }

}
