import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.Random;

public class LendingRecordProducer {
    private final static String TOPIC = "kafka-lending";

    private static KafkaProducer createProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "AvroProducer");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
        props.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://localhost:8081");
        return new KafkaProducer(props);
    }

    private static ProducerRecord<Object, Object> generateRecord(Schema schema) {
        Random rand = new Random();
        GenericRecord record = new GenericData.Record(schema);

        String lendingId = String.valueOf((int)(Math.random()*10001) + 1);
        record.put("lend_id", lendingId);
        record.put("timestamp", System.currentTimeMillis());
        record.put("book_id", Integer.valueOf(rand.nextInt(10) + 1)); // id knjige med 1 in 10
        record.put("member_id", Integer.valueOf(rand.nextInt(10) + 1)); // id člana med 1 in 10
        record.put("due_date", System.currentTimeMillis() + 31L *24*60*60*1000); // rok vrnitve je 31 dni po času izposoje

        return new ProducerRecord<Object, Object>(TOPIC, lendingId, record);
    }

    public static void main(String[] args) throws Exception {
        Schema schema = SchemaBuilder.record("Lending")
                .fields()
                .requiredString("lend_id")
                .requiredLong("timestamp")
                .requiredInt("book_id")
                .requiredInt("member_id")
                .requiredLong("due_date")
                .endRecord();

        KafkaProducer producer = createProducer();

        while(true) {
            producer.send(generateRecord(schema));
            System.out.println("Sent new lending object.");
            Thread.sleep(10000);
        }
    }
}
