package de.afinke.blog.camel;

import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

import java.util.Dictionary;

public class MessageRouter implements ManagedService {

    private String recipient = "activemq:queue:output";

    public void updated(Dictionary<String, ?> dictionary) throws ConfigurationException {
        if (dictionary == null) {
            return;
        }
        recipient = String.valueOf(dictionary.get("queueName"));
    }

    public String routeTo() {
        return recipient;
    }

}
