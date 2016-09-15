package de.afinke.blog.camel;

import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

import java.util.Arrays;
import java.util.Dictionary;
import java.util.List;

public class MessageFilter implements ManagedService {

    private List<String> countries = Arrays.asList("GER", "LUX");

    public String routeTo(String country) {
        if (countries.contains(country)) {
            return "vm:output";
        } else {
            return "vm:trash";
        }

    }

    public void updated(Dictionary<String, ?> dictionary) throws ConfigurationException {
        if(null == dictionary) {
            return;
        }
        countries = Arrays.asList(String.valueOf(dictionary.get("countries")).replaceAll("\\s+", "").split(","));
    }

}
