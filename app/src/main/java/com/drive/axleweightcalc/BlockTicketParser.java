package com.drive.axleweightcalc;

import android.graphics.Rect;
import android.util.Log;

import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Direct layout parser specifically tuned to CAT Scale standards (Weigh My Truck app & paper tickets).
 * Targets "STEER AXLE", "DRIVE AXLE", and "TRAILER AXLE", extracts the corresponding weights,
 * and calculates Gross strictly as the sum of the three axles.
 */
public class BlockTicketParser {

    private static final String TAG = "BlockTicketParser";

    // Matches numbers like 12780, 12780LB, 12,780 LBS, 35320 LB, 30400
    private static final Pattern NUM_PATTERN = Pattern.compile("(?<!\\d)(\\d{1,3}[,\\s]?\\d{3}|\\d{4,5})(?!\\d)");

    public static ParsedTicket parse(Text visionText) {
        if (visionText == null) {
            return new ParsedTicket(0, 0, 0, 0);
        }

        // Collect and sort all lines from top to bottom
        List<Text.Line> allLines = new ArrayList<>();
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            allLines.addAll(block.getLines());
        }

        Collections.sort(allLines, (l1, l2) -> {
            Rect r1 = l1.getBoundingBox();
            Rect r2 = l2.getBoundingBox();
            int top1 = r1 != null ? r1.top : 0;
            int top2 = r2 != null ? r2.top : 0;
            return Integer.compare(top1, top2);
        });

        int steer = 0;
        int drive = 0;
        int trailer = 0;

        // 1. Pass 1: Block-level search for CAT Scale cards ("STEER AXLE", "DRIVE AXLE", "TRAILER AXLE")
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            String blockText = block.getText().toLowerCase().trim();
            if (blockText.contains("do not slide") || blockText.contains("slide tandems")) continue;

            if (steer == 0 && isSteerLabel(blockText)) {
                steer = extractWeightFromBlockOrLines(block);
            } else if (drive == 0 && isDriveLabel(blockText)) {
                drive = extractWeightFromBlockOrLines(block);
            } else if (trailer == 0 && isTrailerLabel(blockText)) {
                trailer = extractWeightFromBlockOrLines(block);
            }
        }

        // 2. Pass 2: Spatial line-by-line search if any axle was not extracted
        if (steer == 0 || drive == 0 || trailer == 0) {
            for (Text.Line labelLine : allLines) {
                String text = labelLine.getText().toLowerCase().trim();
                if (text.contains("slide") || text.contains("do not") || text.contains("warning")) continue;

                if (steer == 0 && isSteerLabel(text)) {
                    steer = findValueForLabel(labelLine, allLines);
                } else if (drive == 0 && isDriveLabel(text)) {
                    drive = findValueForLabel(labelLine, allLines);
                } else if (trailer == 0 && isTrailerLabel(text)) {
                    trailer = findValueForLabel(labelLine, allLines);
                }
            }
        }

        // 3. Pass 3: Text regex fallback if any axle is still missing
        if (steer == 0 || drive == 0 || trailer == 0) {
            ParsedTicket regexTicket = TicketTextParser.parseText(visionText.getText());
            if (steer == 0 && regexTicket.steerAxle > 0)     steer   = regexTicket.steerAxle;
            if (drive == 0 && regexTicket.driveAxle > 0)     drive   = regexTicket.driveAxle;
            if (trailer == 0 && regexTicket.trailerAxle > 0) trailer = regexTicket.trailerAxle;
        }

        // Calculate Gross strictly by sum of the 3 axle weights
        int gross = (steer > 0 || drive > 0 || trailer > 0) ? (steer + drive + trailer) : 0;

        Log.d(TAG, "Parsed CAT Scale result -> Steer=" + steer + ", Drive=" + drive + ", Trailer=" + trailer + ", Gross=" + gross);
        return new ParsedTicket(steer, drive, trailer, gross);
    }

    private static boolean isSteerLabel(String text) {
        return text.contains("steer axle") || (text.contains("steer") && !text.contains("drive") && !text.contains("trailer"));
    }

    private static boolean isDriveLabel(String text) {
        return text.contains("drive axle") || (text.contains("drive") && !text.contains("steer") && !text.contains("trailer"));
    }

    private static boolean isTrailerLabel(String text) {
        return text.contains("trailer axle") || (text.contains("trailer") && !text.contains("steer") && !text.contains("drive"));
    }

    private static int extractWeightFromBlockOrLines(Text.TextBlock block) {
        List<Text.Line> lines = block.getLines();
        for (Text.Line line : lines) {
            int val = extractWeightNumber(line.getText());
            if (val >= 2000 && val <= 60000) {
                return val;
            }
        }
        return extractWeightNumber(block.getText());
    }

    private static int findValueForLabel(Text.Line labelLine, List<Text.Line> allLines) {
        // A. Number already on the same line (e.g. "STEER AXLE 12780 LB")
        int sameLineVal = extractWeightNumber(labelLine.getText());
        if (sameLineVal > 0) {
            return sameLineVal;
        }

        Rect labelRect = labelLine.getBoundingBox();
        if (labelRect == null) return 0;

        int labelCenterY = (labelRect.top + labelRect.bottom) / 2;
        int labelHeight = Math.max(1, labelRect.height());

        // B. Horizontal Row Match (Two-column layout, value is to the right of label)
        Text.Line bestRowCandidate = null;
        int minXDist = Integer.MAX_VALUE;

        for (Text.Line candidate : allLines) {
            if (candidate == labelLine) continue;
            Rect cRect = candidate.getBoundingBox();
            if (cRect == null) continue;

            int candidateCenterY = (cRect.top + cRect.bottom) / 2;
            int yDiff = Math.abs(candidateCenterY - labelCenterY);

            // Vertically aligned within 100% of label height
            if (yDiff <= labelHeight * 1.0f && cRect.left >= labelRect.left) {
                int xDist = cRect.left - labelRect.right;
                if (xDist >= -labelHeight && xDist < minXDist) {
                    int val = extractWeightNumber(candidate.getText());
                    if (val > 0) {
                        minXDist = xDist;
                        bestRowCandidate = candidate;
                    }
                }
            }
        }

        if (bestRowCandidate != null) {
            return extractWeightNumber(bestRowCandidate.getText());
        }

        // C. Vertical Stack Match (Value directly beneath label)
        Text.Line bestBelowCandidate = null;
        int minYDist = Integer.MAX_VALUE;

        for (Text.Line candidate : allLines) {
            if (candidate == labelLine) continue;
            Rect cRect = candidate.getBoundingBox();
            if (cRect == null) continue;

            int yDist = cRect.top - labelRect.bottom;
            if (yDist >= -5 && yDist < labelHeight * 4.0f && yDist < minYDist) {
                int overlapLeft = Math.max(labelRect.left - labelHeight * 4, cRect.left);
                int overlapRight = Math.min(labelRect.right + labelHeight * 4, cRect.right);
                if (overlapRight >= overlapLeft) {
                    int val = extractWeightNumber(candidate.getText());
                    if (val > 0) {
                        minYDist = yDist;
                        bestBelowCandidate = candidate;
                    }
                }
            }
        }

        if (bestBelowCandidate != null) {
            return extractWeightNumber(bestBelowCandidate.getText());
        }

        return 0;
    }

    private static int extractWeightNumber(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        Matcher m = NUM_PATTERN.matcher(text);
        while (m.find()) {
            String raw = m.group(1);
            if (raw != null) {
                try {
                    int val = Integer.parseInt(raw.replaceAll("[,\\s]", ""));
                    if (val >= 2000 && val <= 60000) {
                        return val;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return 0;
    }
}
