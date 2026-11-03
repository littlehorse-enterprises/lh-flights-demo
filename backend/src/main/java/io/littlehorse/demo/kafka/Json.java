package io.littlehorse.demo.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;

/** JSON is used only at database, HTTP, and Kafka boundaries. */
public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private Json() {}

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot encode business record", e);
        }
    }

    public static <T> T read(String value, Class<T> type) {
        try {
            return MAPPER.readValue(value, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot decode " + type.getSimpleName(), e);
        }
    }
}
