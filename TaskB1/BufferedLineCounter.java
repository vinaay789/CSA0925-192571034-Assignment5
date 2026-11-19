import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class BufferedLineCounter {
    private static Path makeFile(String name, int sizeMb) throws IOException {
        Path path = Files.createTempFile(name, ".txt");
        try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            String line = "This is a sample attendance log line for efficient buffering measurements.\n";
            int targetBytes = sizeMb * 1024 * 1024;
            int bytesWritten = 0;
            while (bytesWritten < targetBytes) {
                writer.write(line);
                bytesWritten += line.getBytes(StandardCharsets.UTF_8).length;
            }
        }
        return path;
    }

    public static long countUnbuffered(Path file) throws IOException {
        long count = 0;
        try (FileInputStream in = new FileInputStream(file.toFile())) {
            int b;
            while ((b = in.read()) != -1) {
                if (b == '\n') {
                    count++;
                }
            }
        }
        return count;
    }

    public static long countBuffered(Path file) throws IOException {
        long count = 0;
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file.toFile()))) {
            int b;
            while ((b = in.read()) != -1) {
                if (b == '\n') {
                    count++;
                }
            }
        }
        return count;
    }

    public static long countBufferedReader(Path file) throws IOException {
        long count = 0;
        try (BufferedReader in = new BufferedReader(new FileReader(file.toFile()))) {
            while (in.readLine() != null) {
                count++;
            }
        }
        return count;
    }

    public static void main(String[] args) throws Exception {
        int[] sizes = {1, 10, 50};
        System.out.println("Benchmarking line counting with and without buffering\n");
        for (int size : sizes) {
            Path file = makeFile("bench-" + size + "MB", size);
            long start = System.nanoTime();
            long unbuffered = countUnbuffered(file);
            long unbufferedMs = (System.nanoTime() - start) / 1_000_000;

            start = System.nanoTime();
            long buffered = countBuffered(file);
            long bufferedMs = (System.nanoTime() - start) / 1_000_000;

            start = System.nanoTime();
            long readAllLines = countBufferedReader(file);
            long readAllLinesMs = (System.nanoTime() - start) / 1_000_000;

            System.out.printf("File size: %d MB%n", size);
            System.out.printf("Unbuffered calls -> %d lines in %d ms%n", unbuffered, unbufferedMs);
            System.out.printf("Buffered calls -> %d lines in %d ms%n", buffered, bufferedMs);
            System.out.printf("BufferedReader -> %d lines in %d ms%n", readAllLines, readAllLinesMs);
            System.out.println();
            Files.deleteIfExists(file);
        }
    }
}
