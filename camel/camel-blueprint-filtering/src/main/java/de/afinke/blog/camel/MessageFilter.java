package de.afinke.blog.camel;

import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

import java.util.Arrays;
import java.util.Dictionary;
import java.util.List;

public class MessageFilter implements ManagedService {

    List<String> countries = Arrays.asList("GER", "LUX");

    public void updated(Dictionary<String, ?> dictionary) throws ConfigurationException {
        if (dictionary == null) {
            return;
        }
        String tmpCountries = String.valueOf(dictionary.get("countries"));
        countries = Arrays.asList(tmpCountries.split(","));
    }

    public String routeTo(String country) {
        if (countries.contains(country)) {
            return "direct:output";
        }
        else {
            return "direct:trash";
        }

    }

}
