package de.afinke.blog.camel.main;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.blueprint.Main;

public class MessageProducer {

    public static void main(String[] args) throws Exception {
        System.out.println("### STARTING MAIN (BE PATIENT TAKES A WHILE) ###");
        Main main = new Main();
        main.start();

        ProducerTemplate template = main.getCamelTemplate();
        template.sendBodyAndHeader("direct:input", "Hello World", "country", "GER");

        Thread.sleep(1000);
        System.out.println("### STOPPING MAIN ###");
        main.stop();
    }

}
