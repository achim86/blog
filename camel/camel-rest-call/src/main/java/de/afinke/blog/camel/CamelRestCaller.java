package de.afinke.blog.camel;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;

public class CamelRestCaller extends Main {

    public static void main(String[] args) throws Exception {
        CamelRestCaller main = new CamelRestCaller();
        main.addRouteBuilder(createRouteBuilder());
        main.run(args);
    }

    static RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer://foo?fixedRate=true&period=5s")
                    .setHeader(Exchange.HTTP_QUERY, constant("userId=1"))
                    .to("http4://jsonplaceholder.typicode.com/posts")
                    .to("stream:out");
            }
        };
    }

}
