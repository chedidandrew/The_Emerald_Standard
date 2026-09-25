package com.chedidandrew.emeraldstandard.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32C;

/** Length/checksum framed, forced write-ahead records. Only an incomplete final frame is ignored. */
public final class DurableJournal {
    private static final int MAX_RECORD = 64 * 1024 * 1024;
    private final Path path;
    private long end = -1;
    public DurableJournal(Path path) { this.path = path; }

    public List<byte[]> read() throws IOException {
        List<byte[]> records = new ArrayList<>();
        end = 0;
        if (!Files.exists(path)) return records;
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            while (channel.size() - channel.position() >= 8) {
                ByteBuffer header = ByteBuffer.allocate(8);
                readFully(channel, header); header.flip();
                int size = header.getInt(), checksum = header.getInt();
                if (size < 0 || size > MAX_RECORD) throw new IOException("Invalid journal frame: " + path);
                if (channel.size() - channel.position() < size) break;
                ByteBuffer data = ByteBuffer.allocate(size); readFully(channel, data);
                if (checksum(data.array()) != checksum) throw new IOException("Journal checksum mismatch: " + path);
                records.add(data.array()); end = channel.position();
            }
        }
        return records;
    }

    public void append(byte[] data) throws IOException {
        if (data.length > MAX_RECORD) throw new IOException("Journal record too large");
        if (end < 0) read();
        Files.createDirectories(path.toAbsolutePath().getParent());
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.truncate(end); channel.position(end);
            ByteBuffer frame = ByteBuffer.allocate(8 + data.length).putInt(data.length)
                    .putInt(checksum(data)).put(data); frame.flip();
            try {
                while (frame.hasRemaining()) channel.write(frame);
                channel.force(true); end = channel.position();
            } catch (IOException failure) {
                // Do not let an acknowledged failure replay as a successful transaction.
                try { channel.truncate(end); channel.force(true); }
                catch (IOException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
    }

    /** Force the replacement before renaming; atomic replacement is used when supported. */
    public void replace(List<byte[]> records) throws IOException {
        Path temporary=path.resolveSibling(path.getFileName()+".tmp");
        Files.createDirectories(path.toAbsolutePath().getParent());
        long length=0;
        try (FileChannel channel=FileChannel.open(temporary,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)) {
            for (byte[] record:records) {
                ByteBuffer frame=ByteBuffer.allocate(8+record.length).putInt(record.length).putInt(checksum(record)).put(record); frame.flip();
                while(frame.hasRemaining()) channel.write(frame);
            }
            channel.force(true); length=channel.position();
        }
        try { Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
        catch(AtomicMoveNotSupportedException unsupported) { Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING); }
        end=length;
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) if (channel.read(buffer) < 0) throw new IOException("Truncated journal");
    }
    private static int checksum(byte[] bytes) { CRC32C crc = new CRC32C(); crc.update(bytes); return (int) crc.getValue(); }
}
