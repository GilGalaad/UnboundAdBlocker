package main;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UnboundBlacklister {

    private static final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private static final String BLACKLIST_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts";
    private static final String WHITE_LIST_FILENAME = "whitelist.conf";
    private static final int HTTP_STATUS_OK = 200;
    private static final int BUFFER_SIZE = 8 * 1024;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:59.0) Gecko/20100101 Firefox/59.0";
    private static final String IPV4_REGEX = "((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";
    private static final String DOMAIN_REGEX = ".*[a-zA-Z_0-9]{1}\\.[a-zA-Z_0-9]{2,}";

    private static HashSet<String> blackList = new HashSet<>();
    private static HashSet<String> whiteList = new HashSet<>();

    public static void main(String[] args) {
        System.out.println("# " + sdf.format(new Date()));

        // determining file location
        Path jarPath = null;
        try {
            jarPath = getJarLocation();
        } catch (URISyntaxException ex) {
            // should never happen because URL is generated by JDK itself
            log("# Unexpected URISyntaxException while getting jar location - " + ex.getMessage());
            System.exit(1);
        }

        // locating optional whitelist
        Path whiteListPath = jarPath.resolve(WHITE_LIST_FILENAME);
        if (Files.exists(whiteListPath) && Files.isRegularFile(whiteListPath) && Files.isReadable(whiteListPath)) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(whiteListPath.toFile()), UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!isEmpty(line) && !line.trim().startsWith("#")) {
                        whiteList.add(line.trim());
                    }
                }
                log("# Found a total of " + whiteList.size() + " whitelisted unique domains");
            } catch (IOException ex) {
                log("# IOException while parsing whitelist file - " + ex.getMessage());
                System.exit(1);
            }
        } else {
            log("# Optional whitelist file not found");
        }

        // fetching remote blacklist
        System.out.println("# Processing URL: " + BLACKLIST_URL);
        try (BufferedReader br = getResourceBufferedReader(BLACKLIST_URL)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (isValidLine(line.trim())) {
                    String[] split = line.trim().split("\\s", -1);
                    String domain = split[1].toLowerCase().trim();
                    if (isValidDomain(domain) && !whiteList.contains(domain)) {
                        blackList.add(domain);
                    }
                }
            }
            log("# Found a total of " + blackList.size() + " blacklisted unique domains");
        } catch (MalformedURLException ex) {
            log("# Malformed URL - " + ex.getMessage());
        } catch (IOException ex) {
            log("# IOException while parsing blacklist URL - " + ex.getMessage());
        }

        // ordering and printing
        ArrayList<String> orderedBlackList = new ArrayList<>();
        orderedBlackList.addAll(blackList);
        Collections.sort(orderedBlackList);
        for (String domain : orderedBlackList) {
            log("local-data: \"" + domain + ". A 127.0.0.1\"");
        }
    }

    public static boolean isEmpty(String str) {
        return (str == null || str.trim().equals(""));
    }

    public static void log(String msg) {
        System.out.println(msg);
    }

    public static Path getJarLocation() throws URISyntaxException {
        Path jarPath = Paths.get(UnboundBlacklister.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (Files.isRegularFile(jarPath)) {
            return jarPath.getParent();
        } else {
            return jarPath;
        }
    }

    public static String fetchResource(String urlString) throws MalformedURLException, IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.connect();
        if (conn.getResponseCode() != HTTP_STATUS_OK) {
            throw new IOException("HTTP status code: " + conn.getResponseCode());
        }
        Charset cs = (!isEmpty(conn.getContentType()) && conn.getContentType().toLowerCase().contains("charset=utf-8")) ? UTF_8 : ISO_8859_1;
        StringBuilder sb = new StringBuilder();
        try (InputStreamReader isr = new InputStreamReader(conn.getInputStream(), cs)) {
            char[] cbuf = new char[BUFFER_SIZE];
            int len;
            while ((len = isr.read(cbuf, 0, BUFFER_SIZE)) != -1) {
                sb.append(cbuf, 0, len);
            }
        }
        conn.disconnect();
        return sb.toString();
    }

    public static BufferedReader getResourceBufferedReader(String urlString) throws MalformedURLException, IOException {
        String res = fetchResource(urlString);
        return new BufferedReader(new StringReader(res));
    }

    private static boolean isValidLine(String str) {
        if (isEmpty(str) || str.startsWith("#")
                || str.startsWith("127.0.0.1") || str.startsWith("255.255.255.255")
                || str.startsWith("::1") || str.startsWith("fe80:") || str.startsWith("ff02::1") || str.startsWith("ff02::2")) {
            return false;
        }
        return true;
    }

    private static boolean isValidDomain(String str) {
        if (str.length() <= 3) {
            return false;
        }
        Pattern p = Pattern.compile(IPV4_REGEX);
        Matcher m = p.matcher(str);
        if (m.matches()) {
            return false;
        }
        if (str.startsWith("xn--")) {
            return true;
        }
        p = Pattern.compile(DOMAIN_REGEX);
        m = p.matcher(str);
        if (!m.matches()) {
            return false;
        }
        return true;
    }

}
