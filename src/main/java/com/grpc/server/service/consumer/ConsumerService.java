package com.grpc.server.service.consumer;

import com.grpc.server.interceptor.ByteArrayToGenericRecordMessageConverter;
import com.grpc.server.proto.KafkaConsumerServiceGrpc;
import com.grpc.server.proto.MessagesConsumer;
import com.grpc.server.util.Utils;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.annotation.StreamMessageConverter;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.cloud.stream.messaging.Sink;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import java.io.IOException;

@Slf4j
@Service
@ConditionalOnProperty(name = "consumerBinding", havingValue = "true")
public class ConsumerService extends KafkaConsumerServiceGrpc.KafkaConsumerServiceImplBase {


    private StreamObserver<MessagesConsumer.Response> responseObserver;
    private MessagesConsumer.GetAllMessages request;

    @Override
    public void getAll(MessagesConsumer.GetAllMessages request,
                       StreamObserver<MessagesConsumer.Response> responseObserver) {

        this.request = request;
        this.responseObserver = responseObserver;
    }

    @EnableBinding(Sink.class)
    @Configuration
    public class CloudStreamProcessor {

        @StreamListener(Processor.INPUT)
        public void input(GenericRecord input) {
            System.out.println("Input " + input);
            try {
                log.debug("Message Read " + input);
                responseObserver.onNext(MessagesConsumer.Response.newBuilder()
                        .setEvent(MessagesConsumer.Event.newBuilder().setValue(input.toString()).build()
                        ).build());
            } catch (Exception ex) {
                log.error("Exception while consuming the records - " + ex.getCause());
                ex.printStackTrace();
                responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage())
                        .augmentDescription(ex.getCause().getMessage())
                        .withCause(ex.getCause())
                        .asRuntimeException());
                return;
            }
        }

    }



}