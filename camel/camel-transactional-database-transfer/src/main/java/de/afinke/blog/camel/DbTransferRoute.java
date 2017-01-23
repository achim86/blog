package de.afinke.blog.camel;

import org.apache.camel.builder.RouteBuilder;

import java.util.Map;

public class DbTransferRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("sqlWorldOne:select * from city where processed = 0?delay=10000&onConsume=update city set processed = 1 where id = :#id")
                .transacted()
                .to("sqlWorldTwo:insert into city (id, name) values (:#id, :#name)")
                .process(exchange -> {
                    @SuppressWarnings("rawtypes")
                    Map values = exchange.getIn().getBody(Map.class);
                    int id = (int) values.get("id");
                    if (1 == id) {
                        throw new RuntimeException("Testing transactions!");
                    }
                });
    }

}
