package ru.mail.polis.dao;

import org.jetbrains.annotations.NotNull;
import org.rocksdb.*;
import ru.mail.polis.Record;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;

import static java.lang.Byte.MIN_VALUE;
import static org.rocksdb.BuiltinComparator.BYTEWISE_COMPARATOR;


public final class DAOImpl implements DAO {

    private final RocksDB rocksDB;

    private DAOImpl(final RocksDB db) {
        this.rocksDB = db;
    }

    public class MyIterator implements Iterator<Record>, AutoCloseable {
        private final RocksIterator iterator;

        MyIterator(RocksIterator iterator) {
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext() {
            return iterator.isValid();
        }

        @Override
        public Record next() throws IllegalStateException {
            if (!hasNext()) {
                throw new IllegalStateException("");
            }

            final var key = revertShiftByte(iterator.key());
            final var record = Record.of(key, ByteBuffer.wrap(iterator.value()));
            iterator.next();
            return record;
        }

        @Override
        public void close() {
            iterator.close();
        }
    }

    @NotNull
    @Override
    public Iterator<Record> iterator(@NotNull final ByteBuffer from) {
        final var iterator = rocksDB.newIterator();
        iterator.seek(shiftByte(from));
        return new MyIterator(iterator);
    }

    @NotNull
    @Override
    public ByteBuffer get(@NotNull final ByteBuffer key) throws IOException {
        try {
            final var result = rocksDB.get(shiftByte(key));
            if (result == null) {
                throw new NoSuchElementExceptionLite("cant find element " + key.toString());
            }
            return ByteBuffer.wrap(result);
        } catch (RocksDBException exception) {
            throw new IOException(exception);
        }
    }

    @Override
    public void upsert(@NotNull final ByteBuffer key, @NotNull final ByteBuffer value) throws IOException {
        try {
            rocksDB.put(shiftByte(key), toArray(value));
        } catch (RocksDBException exception) {
            throw new IOException(exception);
        }
    }

    @Override
    public void remove(@NotNull final ByteBuffer key) throws IOException {
        try {
            rocksDB.delete(shiftByte(key));
        } catch (RocksDBException exception) {
            throw new IOException(exception);
        }
    }

    @Override
    public void compact() throws IOException {
        try {
            rocksDB.compactRange();
        } catch (RocksDBException exception) {
            throw new IOException(exception);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            rocksDB.syncWal();
            rocksDB.closeE();
        } catch (RocksDBException exception) {
            throw new IOException(exception);
        }
    }

    static DAO init(final File data) throws IOException {
        RocksDB.loadLibrary();
        try {
            final var options = new Options()
                    .setCreateIfMissing(true)
                    .setComparator(BYTEWISE_COMPARATOR);
            final var db = RocksDB.open(options, data.getAbsolutePath());
            return new DAOImpl(db);
        } catch (RocksDBException exception) {
            throw new IOException(exception);
        }
    }

    private byte[] toArray(@NotNull final ByteBuffer buffer) {
        final var copy = buffer.duplicate();
        final var arr = new byte[copy.remaining()];
        copy.get(arr);
        return arr;
    }

    private byte[] shiftByte(@NotNull final ByteBuffer byteBuffer) {
        final var arr = toArray(byteBuffer);
        for (int i = 0; i < arr.length; i++) {
            arr[i] -= MIN_VALUE;
        }
        return arr;
    }

    private ByteBuffer revertShiftByte(@NotNull final byte[] array) {
        final var clone = Arrays.copyOf(array, array.length);
        for (int i = 0; i < array.length; i++) {
            clone[i] += MIN_VALUE;
        }
        return ByteBuffer.wrap(clone);
    }
}