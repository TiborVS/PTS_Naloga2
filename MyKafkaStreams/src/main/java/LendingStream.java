import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;

public class LendingStream {
    private static Properties setup() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "LendingStream");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.Integer().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, GenericAvroSerde.class.getName());
        props.put("schema.registry.url", "http://127.0.0.1:8081");
        props.put("input.topic.name", "kafka-lending");
        props.put("default.deserialization.exception.handler", "org.apache.kafka.streams.errors.LogAndContinueExceptionHandler");

        return props;
    }

    public static void main(String[] args) throws Exception {
        final Map<String, String> serdeConfig = Collections.singletonMap("schema.registry.url", "http://127.0.0.1:8081");
        final Serde<GenericRecord> valueGenericAvroSerde = new GenericAvroSerde();
        valueGenericAvroSerde.configure(serdeConfig, false);

        StreamsBuilder builder = new StreamsBuilder();

        KStream<Integer, GenericRecord> inputStream = builder.stream("kafka-lending", Consumed.with(Serdes.Integer(), valueGenericAvroSerde));

        // stream za grupiranje izposoj po knjigah

        inputStream.map((k,v) -> new KeyValue<>(
                        Integer.valueOf(v.get("book_id").toString()),
                        v.get("lend_id").toString()
                ))
                .groupByKey(Grouped.with(
                        Serdes.Integer(),
                        Serdes.String()
                ))
                .count()
                .toStream()
                .mapValues(value -> value.toString())
                .to("kafka-lending-by-book", Produced.with(
                        Serdes.Integer(),
                        Serdes.String()
                ));

        // stream za grupiranje izposoj po članih

        inputStream.map((k,v) -> new KeyValue<>(
                        Integer.valueOf(v.get("member_id").toString()),
                        v.get("lend_id").toString()
                ))
                .groupByKey(Grouped.with(
                        Serdes.Integer(),
                        Serdes.String()
                ))
                .count()
                .toStream()
                .mapValues(value -> value.toString())
                .to("kafka-lending-by-member", Produced.with(
                        Serdes.Integer(),
                        Serdes.String()
                ));

        // stream za filtiranje bolj izposojenih knjig

        KTable<Integer, String> bookLendingCountsTable = builder.table("kafka-lending-by-book", Consumed.with(Serdes.Integer(), Serdes.String()));
        bookLendingCountsTable
                        .filter((k, v) -> Integer.valueOf(v) > 10)
                        .toStream()
                        .to("kafka-lending-filter-top-books", Produced.with(Serdes.Integer(), Serdes.String()));

        // stream za filtriranje bolj aktivnih uporabnikov

        KTable<Integer, String> memberLendingCountsTable = builder.table("kafka-lending-by-member", Consumed.with(Serdes.Integer(), Serdes.String()));
        bookLendingCountsTable
                .filter((k, v) -> Integer.valueOf(v) > 10)
                .toStream()
                .to("kafka-lending-filter-top-members", Produced.with(Serdes.Integer(), Serdes.String()));

        inputStream.print(Printed.toSysOut());

        Topology topology = builder.build();
        final KafkaStreams streams = new KafkaStreams(topology, setup());
        streams.start();

        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }
}
