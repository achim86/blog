package de.afinke.blog;

import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.LatLng;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;
import org.apache.commons.collections.map.HashedMap;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.VoidFunction;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.twitter.TwitterUtils;
import org.codehaus.jettison.json.JSONObject;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.spark.rdd.api.java.JavaEsSpark;
import twitter4j.Status;
import twitter4j.auth.Authorization;
import twitter4j.auth.AuthorizationFactory;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationContext;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads tweets published for #Election2016, process them by doing sentiment and location analyzes and finally publishes
 * the results to elasticsearch.
 * <p/>
 * PREREQUISITES: Running Elasticsearch Server in version 2.3.3 (default configuration).
 */
public class ElectionAnalyzesRunner {

    static Properties googleApiProperties;
    static Set<String> negativeWords;
    static Set<String> positiveWords;
    static Map<String, Integer> words;
    static StanfordCoreNLP pipeline;

    public static void main(String[] args) throws IOException {

        // some initialization
        initGoogleApi();
        initSentimentAnalysesSources();

        // configure twitter connection
        Configuration twitterConf = ConfigurationContext.getInstance();
        Authorization twitterAuth = AuthorizationFactory.getInstance(twitterConf);

        // configure spark
        SparkConf sparkConf = new SparkConf()
                .setAppName("Election 2016")
                .setMaster("local[2]");
        sparkConf.set("es.index.auto.create", "false");

        JavaStreamingContext streamingContext = new JavaStreamingContext(sparkConf, new Duration(60000));

        String[] filters = {"#Election2016"};
        JavaDStream<Status> tweets = TwitterUtils.createStream(streamingContext, twitterAuth, filters);
        JavaDStream<Map<String, Object>> statuses = tweets
                // create tweets objects from incoming tweets
                .map((Function<Status, Map<String, Object>>) status -> {
                    Map<String, Object> tweet = new HashMap<>();
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    tweet.put("user", status.getUser().getName());
                    tweet.put("text", status.getText());
                    tweet.put("location", status.getUser().getLocation());
                    tweet.put("hashtags", Arrays.stream(status.getHashtagEntities())
                            .map(hashtagEntity -> hashtagEntity.getText()).collect(Collectors.joining(", ")));
                    tweet.put("created_at", sdf.format(status.getCreatedAt()));
                    return tweet;
                })
                // filter out tweets which are not related to Hillary Clinton or Donald Trump
                .filter((Function<Map<String, Object>, Boolean>) tweet -> {
                    String hashtags = tweet.get("hashtags").toString().toLowerCase();
                    return hashtags.contains("hillary") || hashtags.contains("clinton") ||
                            hashtags.contains("donald") || hashtags.contains("trump");
                })
                // detect and add tweet sentiment
                .map((Function<Map<String, Object>, Map<String, Object>>) tweet -> {
                    String text = tweet.get("text").toString();
                    tweet.put("sentiment_liub", detectSentimentLiub(text));
                    tweet.put("sentiment_afinn", detectSentimentAfinn(text));
                    tweet.put("sentiment_corenlp", detectSentimentCoreNlp(text));
                    return tweet;
                })
                // most users don't provide their geo location, use user profile location to calculate geo location
                .map((Function<Map<String, Object>, Map<String, Object>>) tweet -> {
                    tweet.put("geolocation", null);
                    Object location = tweet.get("location");
                    if (null != location) {
                        GeoApiContext context = new GeoApiContext().setApiKey(googleApiProperties.getProperty("key"));
                        GeocodingResult[] results = GeocodingApi.geocode(context, location.toString()).await();
                        if (0 != results.length) {
                            LatLng latLngs = results[0].geometry.location;
                            tweet.put("geolocation", new GeoPoint(latLngs.lat, latLngs.lng));
                        }
                    }
                    return tweet;
                });

        // convert the map to a json string
        JavaDStream<String> json = statuses.map((Function<Map<String, Object>, String>) tweet -> {
            JSONObject jsonObject = new JSONObject(tweet);
            return jsonObject.toString();
        });

        // print to console
        json.print();


        // save json to elasticsearch (comment this part if you don't run an elasticsearch server)
        json.foreachRDD((VoidFunction<JavaRDD<String>>) rdd -> {
            JavaEsSpark.saveJsonToEs(rdd, "spark/tweets");
        });

        // start streaming context
        streamingContext.start();
        streamingContext.awaitTermination();
    }

    private static void initGoogleApi() throws IOException {
        InputStream in = new FileInputStream("src/main/resources/googleapi.properties");
        googleApiProperties = new Properties();
        googleApiProperties.load(in);
    }

    private static void initSentimentAnalysesSources() throws IOException {
        // LIUB
        Stream<String> negativeLines = Files.lines(Paths.get("src/main/resources/negative-words.txt"));
        negativeWords = negativeLines.skip(35).collect(Collectors.toSet());
        Stream<String> positiveLines = Files.lines(Paths.get("src/main/resources/positive-words.txt"));
        positiveWords = positiveLines.skip(35).collect(Collectors.toSet());
        // AFINN
        Stream<String> wordLines = Files.lines(Paths.get("src/main/resources/AFINN-111.txt"));
        words = new HashedMap();
        wordLines.skip(44).forEach(word -> {
            String[] splittedWords = word.split("\t");
            words.put(splittedWords[0], Integer.valueOf(splittedWords[1]));
        });
        // CORE-NLP
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, parse, sentiment");
        pipeline = new StanfordCoreNLP(props);
    }

    private static String detectSentimentLiub(String text) {
        int negativeCount = 0;
        int positiveCount = 0;
        Set<String> textToAnalyze = removePunctuationHashtagsUsersAndLinks(text);
        for (String word : textToAnalyze) {
            if (negativeWords.contains(word)) {
                negativeCount++;
            }
            if (positiveWords.contains(word)) {
                positiveCount++;
            }
        }
        if (negativeCount < positiveCount) {
            return "positive";
        } else if (positiveCount < negativeCount) {
            return "negative";
        } else {
            return "neutral";
        }
    }

    private static String detectSentimentAfinn(String text) {
        int rating = 0;
        Set<String> textToAnalyze = removePunctuationHashtagsUsersAndLinks(text);
        for (String word : textToAnalyze) {
            if (null != words.get(word)) {
                rating += words.get(word);
            }
        }
        if (0 < rating) {
            return "positive";
        } else if (0 > rating) {
            return "negative";
        } else {
            return "neutral";
        }
    }

    private static String detectSentimentCoreNlp(String text) {
        int mainSentiment = 0;
        int longest = 0;
        Annotation annotation = pipeline.process(text);
        for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
            Tree tree = sentence.get(SentimentCoreAnnotations.SentimentAnnotatedTree.class);
            int sentiment = RNNCoreAnnotations.getPredictedClass(tree);
            String partText = sentence.toString();
            // take the result of the longest sentence
            if (partText.length() > longest) {
                mainSentiment = sentiment;
                longest = partText.length();
            }
        }
        if (0 == mainSentiment || 1 == mainSentiment) {
            return "negative";
        } else if (3 == mainSentiment || 4 == mainSentiment) {
            return "positive";
        } else {
            return "neutral";
        }
    }

    private static Set<String> removePunctuationHashtagsUsersAndLinks(String text) {
        String[] splittedTextWithoutPunctuation = text
                .replaceAll("(?!@)(?!#)(?!-)\\p{P}", "")
                .toLowerCase()
                .split("\\s+");
        Set<String> wordsToAnalyze = new HashSet<>(Arrays.asList(splittedTextWithoutPunctuation));
        for (Iterator<String> iterator = wordsToAnalyze.iterator(); iterator.hasNext(); ) {
            String word = iterator.next();
            if (word.startsWith("#") || word.startsWith("@") || word.startsWith("http")) {
                iterator.remove();
            }
        }
        return wordsToAnalyze;
    }

}
