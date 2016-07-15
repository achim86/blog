#Spark Streaming and Elasticsearch combined with Twitter sentiment analyses and Google Maps services

##Abstract
This application reads tweets published for #Election2016, process them by doing sentiment (three different algorithms) and location analyzes and finally publishes the results to elasticsearch.

##Installation Guide
1. Create your own [Twitter OAuth access token](https://dev.twitter.com/oauth/overview/application-owner-access-tokens) and put it into ```twitter4j.properties```.
2. Create your own [Google API Key](https://developers.google.com/maps/documentation/geocoding/get-api-key) and put it into ```googleapi.properties```.
3. Download and [Elasticsearch 2.3.3](https://www.elastic.co/downloads/past-releases/elasticsearch-2-3-3)
4. Create Elasticsearch Index and Mappings using ```
curl -XPOST localhost:9200/spark -d '{
    "settings" : {
        "number_of_shards" : 1
    },
    "mappings": {
        "tweets": {
            "properties": {
                "created_at": {
                    "type": "date",
                    "format": "yyyy-MM-dd HH:mm:ss"
                },
                "geolocation": {
                    "type": "geo_point"
                },
                "hashtags": {
                    "type": "string"
                },
                "location": {
                    "type": "string"
                },
                "sentiment_afinn": {
                    "type": "string"
                },
                "sentiment_corenlp": {
                    "type": "string"
                },
                "sentiment_liub": {
                    "type": "string"
                },
                "text": {
                    "type": "string"
                },
                "user": {
                    "type": "string"
                }
            }
        }
    }
}'
```
5. Run ElectionAnalyzesRunner
6. Download [Kibana 4.5.x](https://www.elastic.co/downloads/kibana) to visualize and analyze your data.

##Useful Links
- Elasticsearch http://localhost:9200, http://localhost:9200/spark/_search?pretty
- Kibana http://localhost:5601

##Possible Improvements
- Include hashtags like ```#CrookedHillary``` or ```#NeverTrump``` into sentiment analyzes.

##References
- http://spark.apache.org/docs/1.6.2/streaming-programming-guide.html
- https://finnaarupnielsen.wordpress.com/2011/03/16/afinn-a-new-word-list-for-sentiment-analysis/
- https://www.cs.uic.edu/~liub/FBS/sentiment-analysis.html
- http://stanfordnlp.github.io/CoreNLP/
- https://github.com/googlemaps/google-maps-services-java
