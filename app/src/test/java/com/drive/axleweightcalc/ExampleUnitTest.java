package com.drive.axleweightcalc;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void testUserAxleWeightCalculation() {
        int steer = 11660;
        int currentDrive = 40040;
        int currentTrailer = 27220;
        int weightPerHole = 400;
        int safetyLimit = 33700;
        int legalLimit = 34000;

        int bestSafeDelta = Integer.MAX_VALUE;
        int minSafeDiff = Integer.MAX_VALUE;
        int bestLegalDelta = Integer.MAX_VALUE;
        int minLegalDiff = Integer.MAX_VALUE;

        for (int delta = -35; delta <= 35; delta++) {
            int shift = delta * weightPerHole;
            int drive = currentDrive + shift;
            int trailer = currentTrailer - shift;
            int diff = Math.abs(drive - trailer);

            if (drive <= safetyLimit && trailer <= safetyLimit) {
                if (diff < minSafeDiff || (diff == minSafeDiff && Math.abs(delta) < Math.abs(bestSafeDelta))) {
                    minSafeDiff = diff;
                    bestSafeDelta = delta;
                }
            }
            if (drive <= legalLimit && trailer <= legalLimit) {
                if (diff < minLegalDiff || (diff == minLegalDiff && Math.abs(delta) < Math.abs(bestLegalDelta))) {
                    minLegalDiff = diff;
                    bestLegalDelta = delta;
                }
            }
        }

        // -16 holes gives drive = 33640, trailer = 33620 (safe limit <= 33700)
        assertEquals(-16, bestSafeDelta);
        assertEquals(33640, currentDrive + (bestSafeDelta * weightPerHole));
        assertEquals(33620, currentTrailer - (bestSafeDelta * weightPerHole));
    }
}