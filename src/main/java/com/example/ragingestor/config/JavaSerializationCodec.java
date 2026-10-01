package com.example.ragingestor.config;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;

import java.io.*;

/**
 * Vert.x requires a MessageCodec for any non-primitive payload, even for purely local
 * (same-JVM) event bus delivery. Our ingestion DTOs are simple `Serializable` records,
 * so a generic Java-serialization codec is the least amount of ceremony; if this ever
 * needs to run clustered across JVMs, replace with a Jackson/JSON codec instead.
 */
public class JavaSerializationCodec<T extends Serializable> implements MessageCodec<T, T> {

    private final Class<T> type;

    public JavaSerializationCodec(Class<T> type) {
        this.type = type;
    }

    @Override
    public void encodeToWire(Buffer buffer, T t) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(t);
            byte[] bytes = bos.toByteArray();
            buffer.appendInt(bytes.length);
            buffer.appendBytes(bytes);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T decodeFromWire(int pos, Buffer buffer) {
        int length = buffer.getInt(pos);
        byte[] bytes = buffer.getBytes(pos + 4, pos + 4 + length);
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (T) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public T transform(T t) {
        return t;
    }

    @Override
    public String name() {
        return type.getName() + "-codec";
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }
}
