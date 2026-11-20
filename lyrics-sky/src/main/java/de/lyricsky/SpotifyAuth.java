package de.lyricsky;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** OAuth 2.0 Authorization Code Flow mit PKCE (kein Client Secret nötig). */
public final class SpotifyAuth {
    public static final int PORT = 8888;
    public static final String REDIRECT = "http://127.0.0.1:" + PORT + "/callback";
    static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static HttpServer server;

    private SpotifyAuth() {}

    /** Startet den lokalen Callback-Server und gibt die Login-URL zurück. */
    public static synchronized String startLogin() throws Exception {
        Config cfg = Config.get();
        if (cfg.clientId.isBlank()) throw new IllegalStateException("Erst /lyrics clientid <ID> setzen.");
        stop(server);

        byte[] vb = new byte[48];
        RANDOM.nextBytes(vb);
        String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(vb);
        String challenge = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        String state = Long.toHexString(RANDOM.nextLong());

        String url = "https://accounts.spotify.com/authorize"
                + "?response_type=code"
                + "&client_id=" + enc(cfg.clientId)
                + "&scope=" + enc("user-read-currently-playing user-read-playback-state")
                + "&redirect_uri=" + enc(REDIRECT)
                + "&code_challenge_method=S256"
                + "&code_challenge=" + challenge
                + "&state=" + state;

        HttpServer srv = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT), 0);
        server = srv;
        srv.createContext("/callback", ex -> {
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            String msg;
            if (q.containsKey("code") && state.equals(q.get("state"))) {
                try {
                    exchange(q.get("code"), verifier);
                    msg = "Login erfolgreich! Du kannst dieses Fenster schliessen.";
                    LyricsSkyMod.chat("Spotify verbunden.");
                } catch (Exception e) {
                    msg = "Fehler beim Login: " + e.getMessage();
                    LyricsSkyMod.chat(msg);
                }
            } else {
                msg = "Login abgebrochen.";
            }
            byte[] body = ("<html><head><meta charset='utf-8'></head><body style='font-family:sans-serif'><h2>"
                    + msg + "</h2></body></html>").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
            new Thread(() -> stop(srv)).start();
        });
        srv.start();

        Thread timeout = new Thread(() -> {
            try {
                Thread.sleep(5 * 60_000L);
            } catch (InterruptedException ignored) {}
            stop(srv);
        });
        timeout.setDaemon(true);
        timeout.start();
        return url;
    }

    private static void stop(HttpServer s) {
        if (s != null) {
            try {
                s.stop(1);
            } catch (Exception ignored) {}
        }
    }

    private static void exchange(String code, String verifier) throws Exception {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("grant_type", "authorization_code");
        f.put("code", code);
        f.put("redirect_uri", REDIRECT);
        f.put("client_id", Config.get().clientId);
        f.put("code_verifier", verifier);
        storeTokens(postToken(f));
    }

    private static synchronized void refresh() throws Exception {
        Config cfg = Config.get();
        Map<String, String> f = new LinkedHashMap<>();
        f.put("grant_type", "refresh_token");
        f.put("refresh_token", cfg.refreshToken);
        f.put("client_id", cfg.clientId);
        try {
            storeTokens(postToken(f));
        } catch (IllegalStateException e) {
            // Refresh-Token ungültig -> neu einloggen nötig
            logout();
            throw e;
        }
    }

    private static JsonObject postToken(Map<String, String> form) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (var e : form.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(sb.toString()))
                .build();
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IllegalStateException("Token-Fehler " + r.statusCode() + ": " + r.body());
        return JsonParser.parseString(r.body()).getAsJsonObject();
    }

    private static void storeTokens(JsonObject o) {
        Config cfg = Config.get();
        cfg.accessToken = o.get("access_token").getAsString();
        if (o.has("refresh_token")) cfg.refreshToken = o.get("refresh_token").getAsString();
        cfg.expiresAtMs = System.currentTimeMillis() + o.get("expires_in").getAsLong() * 1000L;
        Config.save();
    }

    /** Gibt ein gültiges Access-Token zurück (refresht bei Bedarf) oder null, wenn nicht eingeloggt. */
    public static synchronized String accessToken() throws Exception {
        Config cfg = Config.get();
        if (cfg.refreshToken.isBlank()) return null;
        if (cfg.accessToken.isBlank() || System.currentTimeMillis() > cfg.expiresAtMs - 60_000L) refresh();
        return Config.get().accessToken;
    }

    public static synchronized void invalidateAccessToken() {
        Config.get().expiresAtMs = 0;
    }

    public static synchronized void logout() {
        Config cfg = Config.get();
        cfg.accessToken = "";
        cfg.refreshToken = "";
        cfg.expiresAtMs = 0;
        Config.save();
    }

    static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null) return m;
        for (String part : raw.split("&")) {
            int i = part.indexOf('=');
            if (i > 0) m.put(URLDecoder.decode(part.substring(0, i), StandardCharsets.UTF_8),
                    URLDecoder.decode(part.substring(i + 1), StandardCharsets.UTF_8));
        }
        return m;
    }
}
