package de.lyricsky;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fragt Spotify nach dem aktuellen Song, holt Lyrics von LRCLIB und liefert die aktuelle Zeile. */
public final class LyricsService {
    private static final LyricsService INSTANCE = new LyricsService();
    private static final Pattern TS = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]");

    record Line(long timeMs, String text) {}

    private volatile List<Line> lines = List.of();
    private volatile String trackKey = "";
    private volatile long baseProgressMs;
    private volatile long baseNanos;
    private volatile boolean playing;
    private volatile long pauseUntilMs;

    public static LyricsService get() {
        return INSTANCE;
    }

    public void start() {
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lyricssky-poll");
            t.setDaemon(true);
            return t;
        });
        exec.scheduleWithFixedDelay(() -> {
            try {
                poll();
            } catch (Exception e) {
                LyricsSkyMod.LOGGER.warn("Poll fehlgeschlagen: {}", e.toString());
            }
        }, 1, 2, TimeUnit.SECONDS);
    }

    private void poll() throws Exception {
        Config cfg = Config.get();
        if (!cfg.enabled || System.currentTimeMillis() < pauseUntilMs) return;
        String token = SpotifyAuth.accessToken();
        if (token == null) return;

        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1/me/player/currently-playing"))
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(10))
                .GET().build();
        HttpResponse<String> r = SpotifyAuth.HTTP.send(req, HttpResponse.BodyHandlers.ofString());

        switch (r.statusCode()) {
            case 200 -> {}
            case 204 -> { playing = false; return; }
            case 401 -> { SpotifyAuth.invalidateAccessToken(); return; }
            case 429 -> {
                long wait = r.headers().firstValueAsLong("Retry-After").orElse(5);
                pauseUntilMs = System.currentTimeMillis() + wait * 1000L;
                return;
            }
            default -> { return; }
        }

        JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
        JsonElement itemEl = o.get("item");
        if (itemEl == null || itemEl.isJsonNull()) { playing = false; return; }
        JsonObject item = itemEl.getAsJsonObject();
        if (item.has("type") && !"track".equals(item.get("type").getAsString())) { playing = false; return; }

        baseProgressMs = o.get("progress_ms").getAsLong();
        baseNanos = System.nanoTime();
        playing = o.has("is_playing") && o.get("is_playing").getAsBoolean();

        String title = item.get("name").getAsString();
        String artist = item.getAsJsonArray("artists").get(0).getAsJsonObject().get("name").getAsString();
        String album = item.has("album") && item.get("album").isJsonObject()
                ? item.getAsJsonObject("album").get("name").getAsString() : "";
        int durSec = Math.round(item.get("duration_ms").getAsLong() / 1000f);
        String key = artist + " - " + title + " - " + durSec;

        if (!key.equals(trackKey)) {
            trackKey = key;
            lines = List.of();
            try {
                List<Line> fetched = fetchLyrics(title, artist, album, durSec);
                if (key.equals(trackKey)) lines = fetched;
                LyricsSkyMod.LOGGER.info("{} -> {} Lyrics-Zeilen", key, fetched.size());
            } catch (Exception e) {
                LyricsSkyMod.LOGGER.warn("Lyrics-Abfrage fehlgeschlagen: {}", e.toString());
            }
        }
    }

    private List<Line> fetchLyrics(String title, String artist, String album, int durSec) throws Exception {
        HttpResponse<String> r = get("https://lrclib.net/api/get?track_name=" + SpotifyAuth.enc(title)
                + "&artist_name=" + SpotifyAuth.enc(artist)
                + "&album_name=" + SpotifyAuth.enc(album)
                + "&duration=" + durSec);
        if (r.statusCode() == 200) {
            String s = synced(JsonParser.parseString(r.body()).getAsJsonObject());
            if (s != null) return parse(s);
        }
        r = get("https://lrclib.net/api/search?track_name=" + SpotifyAuth.enc(title)
                + "&artist_name=" + SpotifyAuth.enc(artist));
        if (r.statusCode() == 200) {
            JsonArray arr = JsonParser.parseString(r.body()).getAsJsonArray();
            for (JsonElement e : arr) {
                String s = synced(e.getAsJsonObject());
                if (s != null) return parse(s);
            }
        }
        return List.of();
    }

    private static HttpResponse<String> get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "LyricsSky/1.0 (Minecraft Fabric mod)")
                .timeout(Duration.ofSeconds(10))
                .GET().build();
        return SpotifyAuth.HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static String synced(JsonObject o) {
        JsonElement e = o.get("syncedLyrics");
        if (e == null || e.isJsonNull()) return null;
        String s = e.getAsString();
        return s.isBlank() ? null : s;
    }

    static List<Line> parse(String lrc) {
        List<Line> out = new ArrayList<>();
        for (String raw : lrc.split("\n")) {
            Matcher m = TS.matcher(raw);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {
                long min = Long.parseLong(m.group(1));
                long sec = Long.parseLong(m.group(2));
                long ms = 0;
                if (m.group(3) != null) {
                    String f = m.group(3);
                    ms = f.length() == 1 ? Long.parseLong(f) * 100
                            : f.length() == 2 ? Long.parseLong(f) * 10
                            : Long.parseLong(f.substring(0, 3));
                }
                times.add(min * 60_000L + sec * 1000L + ms);
                end = m.end();
            }
            String text = raw.substring(end).trim();
            for (long t : times) out.add(new Line(t, text));
        }
        out.sort(Comparator.comparingLong(Line::timeMs));
        return out;
    }

    private long position() {
        long p = baseProgressMs;
        if (playing) p += (System.nanoTime() - baseNanos) / 1_000_000L;
        return p + Config.get().offsetMs;
    }

    private int currentIndex() {
        List<Line> l = lines;
        if (l.isEmpty()) return -1;
        long pos = position();
        int lo = 0, hi = l.size() - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (l.get(mid).timeMs() <= pos) { ans = mid; lo = mid + 1; } else hi = mid - 1;
        }
        return ans;
    }

    /** Aktuelle Zeile oder null (nichts läuft / keine Lyrics). */
    public String currentLine() {
        if (!playing) return null;
        int i = currentIndex();
        return i < 0 ? null : lines.get(i).text();
    }

    public String nextLine() {
        if (!playing) return null;
        List<Line> l = lines;
        int i = currentIndex() + 1;
        return i < l.size() && i >= 0 ? l.get(i).text() : null;
    }
}
