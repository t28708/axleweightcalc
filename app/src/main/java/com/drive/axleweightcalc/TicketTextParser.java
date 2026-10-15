package com.drive.axleweightcalc;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text regex parser for scale tickets.
 * Searches specifically for "STEER AXLE", "DRIVE AXLE", and "TRAILER AXLE" labels,
 * extracts their weights, and calculates Gross = Steer + Drive + Trailer.
 */
public class TicketTextParser {

    private static final String TAG = "TicketTextParser";

    // ---- STEER ----
    private static final Pattern[] STEER_PATTERNS = {
        Pattern.compile("(?i)steer(?:\\s+axle)?[:\\s]+(\\d[\\d,\\s]{2,6})"),
        Pattern.compile("(?i)\\bsteer\\b[:\\s]+(\\d[\\d,\\s]{2,6})"),
        Pattern.compile("(?i)1[\\s-]steer[:\\s]+(\\d[\\d,\\s]{2,6})"),
        Pattern.compile("(?i)\\bsteer(?:\\s+axle)?\\b[^\\n\\r\\d]{0,20}(\\d{4,6})"),
    };

    // ---- DRIVE ----
    private static final Pattern[] DRIVE_PATTERNS = {
        Pattern.compile("(?i)drive(?:\\s+axle)?[:\\s]+(\\d[\\d,\\s]{2,6})"),
        Pattern.compile("(?i)tandem[/\\s]*drive[:\\s]+(\\d[\\d,\\s]{2,6})"),
        Pattern.compile("(?i)\\bdrive\\b[:\\s]+(\\d[\\d,\\s]{2,6})"),
        Pattern.compile("(?i)2[\\s-]drive[:\\s]+(\\d[\\d,\\s]{2,6})"),
        Pattern.compile("(?i)\\bdrive(?:\\s+axle)?\\b[^\\n\\r\\d]{0,20}(\\d{4,6})"),
    };

    // ---- TRAILER ----
    private static final Pattern[] TRAILER_PATTERNS = {
        Pattern.compile("(?i)trailer(?:\\s+axle)?[:\\s]+(\\d[\\d,\\s]{2,6})"),
        Pattern.compile("(?i)\\btrailer\\b[:\\s]+(\\d[\\d,\\s]{2,6})"),
        Pattern.compile("(?i)3[\\s-]trailer[:\\s]+(\\d[\\d,\\s]{2,6})"),
        Pattern.compile("(?i)\\brear(?:\\s+axle)?\\b[:\\s]+(\\d[\\d,\\s]{2,6})"),
        Pattern.compile("(?i)\\b(?:trailer|rear)(?:\\s+axle)?\\b[^\\n\\r\\d]{0,20}(\\d{4,6})"),
    };

    public static ParsedTicket parseText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ParsedTicket(0, 0, 0, 0);
        }

        // Clean text: strip out warning banners
        String[] lines = text.split("\\r?\\n");
        StringBuilder cleanBuilder = new StringBuilder();
        for (String line : lines) {
            String lower = line.toLowerCase().trim();
            if (lower.contains("do not slide") || lower.contains("slide tandems")) {
                continue;
            }
            cleanBuilder.append(line).append("\n");
        }
        String normalized = cleanBuilder.toString();

        int steer   = matchFirst(normalized, STEER_PATTERNS);
        int drive   = matchFirst(normalized, DRIVE_PATTERNS);
        int trailer = matchFirst(normalized, TRAILER_PATTERNS);

        // Fallback for sequential column layout
        if (steer == 0 || drive == 0 || trailer == 0) {
            ParsedTicket columnResult = parseColumnLayout(normalized);
            if (columnResult.isValid()) {
                if (steer == 0)   steer   = columnResult.steerAxle;
                if (drive == 0)   drive   = columnResult.driveAxle;
                if (trailer == 0) trailer = columnResult.trailerAxle;
            }
        }

        // Calculate Gross strictly as sum of the 3 axle weights
        int gross = (steer > 0 || drive > 0 || trailer > 0) ? (steer + drive + trailer) : 0;

        Log.d(TAG, "Parsed text -> Steer=" + steer + " Drive=" + drive + " Trailer=" + trailer + " Gross=" + gross);
        return new ParsedTicket(steer, drive, trailer, gross);
    }

    private static int matchFirst(String text, Pattern[] patterns) {
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                String raw = m.group(1);
                if (raw != null) {
                    try {
                        int val = Integer.parseInt(raw.replaceAll("[,\\s]", "").trim());
                        if (val >= 1000 && val <= 70000) {
                            return val;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return 0;
    }

    private static ParsedTicket parseColumnLayout(String text) {
        Pattern numPattern = Pattern.compile("(?<!\\d)(\\d{1,3}[,\\s]?\\d{3}|\\d{4,5})(?!\\d)");
        Matcher m = numPattern.matcher(text);
        List<Integer> numbers = new ArrayList<>();
        while (m.find()) {
            try {
                int val = Integer.parseInt(m.group(1).replaceAll("[,\\s]", ""));
                if (val >= 4000 && val <= 70000) {
                    numbers.add(val);
                }
            } catch (NumberFormatException ignored) {}
        }

        int steer = 0, drive = 0, trailer = 0, gross = 0;

        if (numbers.size() >= 3) {
            steer   = numbers.get(0);
            drive   = numbers.get(1);
            trailer = numbers.get(2);
            gross   = steer + drive + trailer;
        }

        return new ParsedTicket(steer, drive, trailer, gross);
    }
}
