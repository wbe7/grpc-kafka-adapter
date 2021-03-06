package com.grpc.server.service.producer;

import com.grpc.server.avro.Message;
import com.grpc.server.config.KafkaProducerConfig;
import com.grpc.server.proto.KafkaServiceGrpc;
import com.grpc.server.proto.Messages;
import com.grpc.server.util.Utils;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "producerBinding", havingValue = "true")
public class ProducerService extends KafkaServiceGrpc.KafkaServiceImplBase {

    @Autowired
    private KafkaProducerConfig kafkaProducerConfig;

    private static final String AVRO_SCHEMA = "avroSchema";

    @Override
    public void save(Messages.ProducerRequest request, StreamObserver<Messages.OkResponse> responseObserver) {
        if (request.getTopicList().isEmpty()) {
            Exception ex = new Exception("Topic name missing");
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage())
                    .augmentDescription("Topic name missing")
                    .withCause(ex)
                    .asRuntimeException());
            return;
        }

        if (request.getHeaderMap().size() == 0 ||
                !request.getHeaderMap().containsKey(AVRO_SCHEMA) ||
                !request.getHeaderMap().containsKey("correlationId")) {
            responseObserver.onError(Status.CANCELLED
                    .withDescription("Missing Mandataory headers")
                    .asRuntimeException());
            return;
        }

        try {
            kafkaProducerConfig.kafkaTemplateTranscational().executeInTransaction(template -> {
                for (String topic : request.getTopicList()) {
                    Message message = Message.newBuilder().setValue(request.getValue()).build();
                    ProducerRecord<String, byte[]> producerRecord = new ProducerRecord
                            (topic, request.getPartition(), request.getKey(), message,
                                    Utils.getRecordHaders(request));
                    template.send(producerRecord);
                    log.info("Message Sent => "+ message);
                }
                return null;
            });

            Messages.OkResponse response = Messages.OkResponse.newBuilder()
                    .setIsOk(true)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            return;
        } catch (Exception ex) {
            log.error("Exception while persisting data - " + ex.getCause());
            ex.printStackTrace();
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage())
                    .augmentDescription(ex.getCause().getMessage())
                    .withCause(ex.getCause())
                    .asRuntimeException());
            return;
        }

    }



}
