package com.drive.axleweightcalc;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the four key weight values from a scale-ticket PDF:
 *   • Steer Axle
 *   • Drive Axle
 *   • Trailer Axle
 *   • Gross Weight
 *
 * Works by stripping the PDF's text layer with PDFBox-Android and then
 * running a cascade of regex patterns against the raw text.
 *
 * Typical ticket formats handled:
 *
 *   Steer: 11,680          Steer Axle   11680
 *   Drive: 35,280          Drive Axle   35280
 *   Trailer: 32,520        Trailer Axle 32520
 *   Gross: 79,480          Gross        79480
 *
 *   STEER       11680      1-STEER       11,680
 *   DRIVE       35280      2-DRIVE       35,280
 *   TRAILER     32520      3-TRAILER     32,520
 *   GROSS       79480      TOTAL/GROSS   79,480
 */
public class PdfTicketParser {

    private static final String TAG = "PdfTicketParser";

    /** Callback interface — always delivered on the calling thread (use Handler for UI updates). */
    public interface Callback {
        void onSuccess(ParsedTicket ticket);
        void onError(String message);
    }

    // -------------------------------------------------------------------------
    // Regex patterns for each field
    // Each pattern has exactly one capture group: the raw digit string (may include commas).
    // The array is tried in order; the first match wins.
    // -------------------------------------------------------------------------

    // ---- STEER ----
    // Handles: "Steer:", "Steer Axle", "STEER", "1-STEER", "STEER AXLE", "Steer  11680"
    private static final Pattern[] STEER_PATTERNS = {
        Pattern.compile("(?i)steer\\s+axle[:\\s]+([\\d,]+)"),
        Pattern.compile("(?i)\\bsteer\\b[:\\s]+([\\d,]+)"),
        Pattern.compile("(?i)1[\\s-]steer[:\\s]+([\\d,]+)"),
        Pattern.compile("(?i)\\bsteer\\b.*?([\\d]{4,6})"),
    };

    // ---- DRIVE ----
    // Handles: "Drive:", "Drive Axle", "DRIVE", "2-DRIVE", "Tandem/Drive"
    private static final Pattern[] DRIVE_PATTERNS = {
        Pattern.compile("(?i)drive\\s+axle[:\\s]+([\\d,]+)"),
        Pattern.compile("(?i)tandem[/\\s]*drive[:\\s]+([\\d,]+)"),
        Pattern.compile("(?i)\\bdrive\\b[:\\s]+([\\d,]+)"),
        Pattern.compile("(?i)2[\\s-]drive[:\\s]+([\\d,]+)"),
        Pattern.compile("(?i)\\bdrive\\b.*?([\\d]{4,6})"),
    };

    // ---- TRAILER ----
    // Handles: "Trailer:", "Trailer Axle", "TRAILER", "3-TRAILER", "Rear"
    private static final Pattern[] TRAILER_PATTERNS = {
        Pattern.compile("(?i)trailer\\s+axle[:\\s]+([\\d,]+)"),
        Pattern.compile("(?i)\\btrailer\\b[:\\s]+([\\d,]+)"),
        Pattern.compile("(?i)3[\\s-]trailer[:\\s]+([\\d,]+)"),
        Pattern.compile("(?i)\\brear\\s+axle\\b[:\\s]+([\\d,]+)"),
        Pattern.compile("(?i)\\btrailer\\b.*?([\\d]{4,6})"),
    };

    // ---- GROSS ----
    // Handles: "Gross:", "Gross Weight", "GROSS", "Total", "GVW"
    private static final Pattern[] GROSS_PATTERNS = {
        Pattern.compile("(?i)gross\\s+weight[:\\s]+([\\d,]+)"),
        Pattern.compile("(?i)\\bgross\\b[:\\s]+([\\d,]+)"),
        Pattern.compile("(?i)\\bgvw\\b[:\\s]+([\\d,]+)"),
        Pattern.compile("(?i)\\btotal\\s+weight\\b[:\\s]+([\\d,]+)"),
        Pattern.compile("(?i)\\btotal\\b[:\\s]+([\\d,]+)"),
        Pattern.compile("(?i)\\bgross\\b.*?([\\d]{4,6})"),
    };

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Parse a PDF at the given URI.
     * Must be called on a background thread — PDFBox performs I/O and CPU work.
     *
     * @param context application or fragment context
     * @param uri     the PDF content URI (from file picker)
     * @return ParsedTicket with whatever values were found (check isValid())
     * @throws Exception if the PDF cannot be opened or read
     */
    public static ParsedTicket parse(Context context, Uri uri) throws Exception {
        // Initialise PDFBox font resources (safe to call multiple times)
        PDFBoxResourceLoader.init(context.getApplicationContext());

        String text;
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             PDDocument document = PDDocument.load(inputStream)) {

            PDFTextStripper stripper = new PDFTextStripper();
            // Extract all pages
            stripper.setSortByPosition(true);
            text = stripper.getText(document);
        }

        Log.d(TAG, "Extracted text:\n" + text);

        if (text == null || text.isEmpty()) {
            throw new Exception("PDF has no text layer. Try a scanned ticket with 'Camera' or 'Gallery' instead.");
        }

        return TicketTextParser.parseText(text);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Tries each pattern in order against the full text.
     * Returns the first matched integer, or 0 if none matched.
     */
    private static int matchFirst(String text, Pattern[] patterns) {
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                String raw = m.group(1);
                if (raw != null) {
                    try {
                        return Integer.parseInt(raw.replaceAll(",", "").trim());
                    } catch (NumberFormatException ignored) {
                        // try next pattern
                    }
                }
            }
        }
        return 0;
    }
}
