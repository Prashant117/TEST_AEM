/**
reprocess asset for dynamic media
*/

package com.example.core.servlets;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;
import org.osgi.framework.Constants;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component(service = Servlet.class,
        property = {
                Constants.SERVICE_DESCRIPTION + "=Reprocess Assets Servlet",
                "sling.servlet.methods=POST",
                "sling.servlet.paths=/bin/trigger-reprocess"
        })
public class ReprocessServlet extends SlingAllMethodsServlet {

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        String host = request.getParameter("host");
        String filePath = request.getParameter("filePath");
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        String profile = request.getParameter("profile") != null ? request.getParameter("profile") : "full-process";

        if (host == null || filePath == null || username == null || password == null) {
            response.setStatus(400);
            response.getWriter().write("Missing required parameters.");
            return;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            response.setStatus(404);
            response.getWriter().write("File not found: " + filePath);
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

        // Send request to /bin/asynccommand
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
            responseMessage = extractMessage(fullResponse); // Parse <div id="Message">
        }

        response.setContentType("application/json");
        JSONObject json = new JSONObject();
        json.put("status", "success");
        json.put("message", responseMessage);
        response.getWriter().write(json.toString());
    }

    private String extractMessage(String html) {
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
