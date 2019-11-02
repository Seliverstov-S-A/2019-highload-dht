package ru.mail.polis.dao;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rocksdb.RocksDBException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.List;

public class ValueTm {

    private final long timestamp;
    private final ByteBuffer value;
    private final RecordType recordType;

    private enum RecordType {
        VALUE((byte) 1),
        DELETED((byte) -1),
        ABSENT((byte) 0);

        final byte value;

        RecordType(final byte value) {
            this.value = value;
        }

        static RecordType fromValue(final byte value) {
            if (value == VALUE.value) {
                return VALUE;
            } else if (value == DELETED.value) {
                return DELETED;
            } else {
                return ABSENT;
            }
        }
    }

    public ValueTm(final long timestamp, final ByteBuffer value,
                   final RecordType type) {
        this.timestamp = timestamp;
        this.recordType = type;
        this.value = value;
    }

    public static ValueTm getEmpty() {
        return new ValueTm(-1, null, RecordType.ABSENT);
    }

    public static ValueTm fromBytes(@Nullable final byte[] bytes) {
        if (bytes == null) {
            return new ValueTm(-1, null, RecordType.ABSENT);
        }
        final var buffer = ByteBuffer.wrap(bytes);
        final var recordType = RecordType.fromValue(buffer.get());
        final var timestamp = buffer.getLong();
        return new ValueTm(timestamp, buffer, recordType);
    }

    public byte[] toBytes() {
        var valueLength = 0;
        if(isValue()) {
            valueLength = value.remaining();
        }
        final var byteBuff = ByteBuffer.allocate(1 + Long.BYTES + valueLength);
        byteBuff.put(recordType.value);
        byteBuff.putLong(getTimestamp());
        if (isValue()) {
            byteBuff.put(value.duplicate());
        }
        return byteBuff.array();
    }

    public static ValueTm fromValue(@NotNull final ByteBuffer value,
                                    final long timestamp) {
        return new ValueTm(timestamp, value, RecordType.VALUE);
    }

    public static boolean isEmptyRecord(@NotNull final byte[] bytes) {
        return bytes[0] != RecordType.VALUE.value;
    }

    public static ValueTm tombstone(final long timestamp) {
        return new ValueTm(timestamp, null, RecordType.DELETED);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isValue() {
        return recordType == RecordType.VALUE;
    }

    public boolean isEmpty() {
        return recordType == RecordType.ABSENT;
    }

    public boolean isDeleted() {
        return recordType == RecordType.DELETED;
    }

    /**
     * Get the value only.
     *
     * @return value of the timestamp instance
     */
    public ByteBuffer getValue() throws IOException {
        if (!isValue()) {
            throw new IOException("Empty record has no value",
                    new RocksDBException("Failed to get value from TimestampRecord!"));
        }
        return value;
    }

    /**
     * Get the value only as bytes.
     *
     * @return value of the timestamp instance as bytes
     */
    public byte[] getValueAsBytes() throws IOException {
        final var val = getValue().duplicate();
        final byte[] ret = new byte[val.remaining()];
        val.get(ret);
        return ret;
    }

    /**
     * Merge multiple records into one according to their timestamps.
     *
     * @param responses to define the input records
     * @return latest timestamp record instance
     */
    public static ValueTm merge(final List<ValueTm> responses) {
        if (responses.size() == 1) return responses.get(0);
        else {
            return responses.stream()
                    .filter(valueTmRecord -> !valueTmRecord.isEmpty())
                    .max(Comparator.comparingLong(ValueTm::getTimestamp))
                    .orElseGet(ValueTm::getEmpty);
        }
    }
}
