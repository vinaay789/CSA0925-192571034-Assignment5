import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class DamagedPassDemo {
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("pass-damage", ".csv");
        Files.writeString(file,
            "P1001,192311001,Arun,12,4500.0\n" +
            "P1002,DAMAGED LINE\n" +
            "P1003,192311003,Divya,7,3800.0\n");

        PassStore store = new PassStore(file);
        System.out.println("Find valid record: " + Arrays.toString(store.find("P1003")));
        System.out.println("Find damaged record: " + Arrays.toString(store.find("P1002")));
        Files.deleteIfExists(file);
    }
}
