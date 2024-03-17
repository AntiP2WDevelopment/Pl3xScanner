package hu.coderkid;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    // Szia!
    // Ha analizálod a kódot, akkor azért néz így ki minden, mert szórakoztam a multithreadingel, sajnos sikertelenül

    private static final File CONFIG_FILE = new File("config.rivals");
    private static String cfToken;
    private static String userAgent;
    private static byte opKd;
    private static HttpRequest.Builder httpRequestBuilder;
    private static ArrayDeque<String> string = new ArrayDeque<>();
    private static FileOutputStream writer;
    private static AtomicInteger threads = new AtomicInteger();
    private static int shulkerCount = 0;
    private static final Set<Integer> searchList = Set.of(88, 109, 136, 255, 392, 406, 477, 491, 510, 526, 615, 643, 736, 774, 808, 972, 990);
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    public static int getY(int magic) {
        return (magic & 0b0000000000_0000000000_111111111111) - 64;
    }

    public static int getMagicNumber(byte[] map, short x, short z) {
        int number = 0;
        int index = z*512 + x; //(tileZ&511)*512 + (tileX&511) 938

        for (int i = 0; i < 4; i++) {
            number |= (map[12 + index * 4 + i] & 0xFF) << (i * 8);
        }
        return number;
    }

    public static void scanRegionAsync(byte regionX, byte regionZ) {
        CompletableFuture<HttpResponse<byte[]>> asyncResponse;
        threads.getAndIncrement();

        try {
            HttpRequest httpRequest = httpRequestBuilder
                .uri(new URI("https://opkd" + (opKd == 1 ? "" : opKd) + ".rivalsnetwork.hu/tiles/world/0/blockinfo/" + regionX + "_" + regionZ + ".pl3xmap.gz"))
                .build();
            asyncResponse = httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        } catch (URISyntaxException e) {
            System.out.println("region error: "+regionX+"; "+regionZ);
            threads.getAndDecrement();
            scanRegionAsync(regionX, regionZ);
            return;
        }

        asyncResponse.whenCompleteAsync((response, throwable) -> {

            if (throwable != null) {
                System.out.println(throwable.getMessage()+": "+regionX+"; "+regionZ);
                threads.getAndDecrement();
                scanRegionAsync(regionX, regionZ);
                return;
            }

            if (response.statusCode() != 200) {
                System.out.println("Failed!!: "+response.statusCode());
                if (response.statusCode() == 403) System.out.println("Maybe expired or invalid CFToken.");
                System.exit(0);
                return;
            }

            byte[] result = response.body();

            if (result.length != 1048588) {
                System.out.println("Retry because length is incorrect: "+result.length);
                threads.getAndDecrement();
                scanRegionAsync(regionX, regionZ);
                return;
            }

            int count = 0;
            int magic;
            for (short x = 0; x < 512; x++)
                for (short z = 0; z < 512; z++) {
                    magic = getMagicNumber(result, x, z);
                    if (searchList.contains(magic >>> 22)) {
                        // a stringbuilder nem szereti a sok threadet, deque az jó
                        string.add(String.format("waypoint:SHULKER %d:S:%d:%d:%d:6:false:0:gui.xaero_default:false:0:0:false%s\n", shulkerCount, x+regionX*512, getY(magic), z+regionZ*512, count > 10 ? "       # ---------- "+count : ""));
                        count++;
                        shulkerCount++;
                    }
                }
            threads.getAndDecrement();
        });
    }

    /*

    minek method, ha csak egy helyen van meghívva?

    public static void scanRegion(byte[] regionContent, byte regionX, byte regionZ) {
        int count = 0;
        for (short x = 0; x < 512; x++)
            for (short z = 0; z < 512; z++) {
                int magic = getMagicNumber(regionContent, x, z);
                if (searchList.contains(magic >>> 22)) {
                    // a stringbuilder nem szereti a sok threadet, deque az jó
                    string.add(String.format("waypoint:SHULKER %d:S:%d:%d:%d:6:false:0:gui.xaero_default:false:0:0:false%s\n", shulkerCount, x+regionX*512, getY(magic), z+regionZ*512, count > 10 ? "       # ---------- "+count : "");
                    count++;
                    shulkerCount++;
                }
            }
        System.out.println("region successfully scanned: "+regionX+"; "+regionZ);
    }*/

    private static boolean readConfig() throws IOException {
        if (CONFIG_FILE.createNewFile()) {
            Files.writeString(CONFIG_FILE.toPath(), "# KingdomsNOCOM made by AntiP2WDevs\n\n# Cloudflare stuff, you can steal from browser cookies\nCFToken:\n\n# You can steal this from the browser...\n# NOTE: You should update it when you steal the CFToken from an another browser.\nUserAgent:\n\n# Kingdoms ID (a number): 1 = opkd, 2 = opkd2, 3 = opkd3\nKingdoms:");
            System.out.println("Szia! Nem volt config fájlod szóval csináltunk ide: ");
            System.out.println(CONFIG_FILE.toPath().toAbsolutePath());
            return false;
        }
        List<String> content = Files.readAllLines(CONFIG_FILE.toPath());
        String[] value;
        for (String line : content) {
            if (line.startsWith("#")) continue;
            if ((value = line.split("CFToken:")).length > 1) cfToken = value[1].trim();
            else if ((value = line.split("UserAgent:")).length > 1) userAgent = value[1].trim();
            else if ((value = line.split("Kingdoms:")).length > 1 && !value[1].isEmpty()) opKd = Byte.parseByte(value[1].trim());
        }

        return cfToken != null && !cfToken.isEmpty() && userAgent != null && !userAgent.isEmpty() && opKd <= 3 && opKd >= 1;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (!readConfig()) {
            System.out.println("Config failed to read...");
            return;
        }
        httpRequestBuilder = HttpRequest.newBuilder()
            .GET()
            .header("User-Agent", userAgent)
            .header("Cookie", "cf_clearance=" + cfToken);
        File shulkerFile = new File("shulkers_"+opKd+".txt");
        writer = new FileOutputStream(shulkerFile);
        shulkerFile.createNewFile();
        if (!shulkerFile.canWrite()) return;
        writer.write(("# Genereted by KingdomsNOCOM\n# Shulkers from kingdoms map:"+opKd+"\n").getBytes(StandardCharsets.UTF_8));

        for (byte regionZ = -6; regionZ < 6; regionZ++)
            for (byte regionX = -6; regionX < 6; regionX++)
                scanRegionAsync(regionX, regionZ);
        while (threads.get() > 0) {
            Thread.sleep(500);
            System.out.print(100-Math.round(threads.get()*0.6944444)+"%          \r");
        }
        writer.write(("# Shulkers: "+shulkerCount+"\n").getBytes());
        for (String s : string) writer.write(s.getBytes(StandardCharsets.UTF_8));
    }
}
