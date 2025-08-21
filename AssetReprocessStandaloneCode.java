import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ReprocessAssetsTool {

    public static void main(String[] args) {

        // Hardcoded parameters
        String host = "http://localhost:4502";
        String filePath = "C:/assets/asset-list.txt";
        String username = "admin";
        String password = "admin";
        String profile = "full-process";

        try {
            File file = new File(filePath);
            if (!file.exists()) {
                System.err.println("File not found: " + filePath);
                return;
            }

            List<String> assetParams;
            try (Stream<String> lines = new BufferedReader(new FileReader(file)).lines()) {
                assetParams = lines
                        .filter(line -> !line.trim().isEmpty())
                        .map(line -> {
                            try {
                                return "asset=" + URLEncoder.encode(line.trim(), "UTF-8");
                            } catch (UnsupportedEncodingException e) {
                                return null;
                            }
                        })
                        .collect(Collectors.toList());
            }

            StringBuilder postData = new StringBuilder();
            postData.append("profile-select=").append(URLEncoder.encode(profile, "UTF-8"));
            postData.append("&runPostProcess=true");
            postData.append("&operation=PROCESS");
            postData.append("&description=").append(URLEncoder.encode("Bulk Reprocess", "UTF-8"));

            for (String asset : assetParams) {
                postData.append("&").append(asset);
            }

            URL url = new URL(host + "/bin/asynccommand");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
            conn.setRequestProperty("Authorization", "Basic " + auth);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(postData.toString().getBytes());
            }

            String responseMessage;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String fullResponse = in.lines().collect(Collectors.joining("\n"));
                responseMessage = extractMessage(fullResponse);
            }

            System.out.println("Status: success");
            System.out.println("Message: " + responseMessage);

        } catch (Exception e) {
            System.err.println("Error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String extractMessage(String html) {
        String tagStart = "<div id=\"Message\">";
        String tagEnd = "</div>";
        int startIdx = html.indexOf(tagStart);
        int endIdx = html.indexOf(tagEnd, startIdx);
        if (startIdx >= 0 && endIdx > startIdx) {
            return html.substring(startIdx + tagStart.length(), endIdx).trim();
        }
        return "No message found";
    }
}
