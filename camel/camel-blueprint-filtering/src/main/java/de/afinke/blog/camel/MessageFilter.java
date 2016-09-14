package de.afinke.blog.camel;

import java.util.Arrays;
import java.util.List;

public class MessageFilter {

    private List<String> countries;

    public String routeTo(String country) {
        if (countries.contains(country)) {
            return "direct:output";
        } else {
            return "direct:trash";
        }

    }

    public void setCountries(String countries) {
        this.countries = Arrays.asList(countries.split(","));
    }

}
