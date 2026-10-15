package com.drive.axleweightcalc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.InputStream;

/**
 * Parses scale ticket images (photos or screenshots from Gallery) using Google ML Kit OCR.
 * Optimizes memory consumption with safe downsampling and handles EXIF rotation to prevent OOM
 * crashes on low/mid-range devices like Moto.
 */
public class ImageTicketParser {

    private static final String TAG = "ImageTicketParser";
    private static final int MAX_IMAGE_DIMENSION = 2048;

    /**
     * Parses an image at the given URI using ML Kit OCR.
     * Must be called on a background thread.
     *
     * @param context Application or Fragment context
     * @param imageUri Image content URI from gallery picker or camera
     * @return ParsedTicket with values found
     * @throws Exception if image cannot be loaded or OCR fails
     */
    public static ParsedTicket parse(Context context, Uri imageUri) throws Exception {
        InputImage image = loadSafeInputImage(context, imageUri);

        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        // Process image synchronously on background thread
        Text visionText = Tasks.await(recognizer.process(image));
        String text = visionText != null ? visionText.getText() : null;

        Log.d(TAG, "Extracted OCR text:\n" + text);

        if (text == null || text.trim().isEmpty()) {
            throw new Exception("No text detected in the image. Please make sure the ticket image is clear and well-lit.");
        }

        return BlockTicketParser.parse(visionText);
    }

    private static InputImage loadSafeInputImage(Context context, Uri imageUri) throws Exception {
        // 1. Decode bounds only to compute sample size
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        try (InputStream is = context.getContentResolver().openInputStream(imageUri)) {
            if (is == null) throw new Exception("Unable to open image stream");
            BitmapFactory.decodeStream(is, null, options);
        }

        int origWidth = options.outWidth;
        int origHeight = options.outHeight;

        if (origWidth <= 0 || origHeight <= 0) {
            // Fallback to standard ML Kit loader if bounds could not be determined
            return InputImage.fromFilePath(context, imageUri);
        }

        // 2. Calculate sample size so max dimension is ~2048px (avoids OOM on budget phones)
        int sampleSize = 1;
        while ((origWidth / sampleSize) > MAX_IMAGE_DIMENSION || (origHeight / sampleSize) > MAX_IMAGE_DIMENSION) {
            sampleSize *= 2;
        }

        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSize;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Bitmap bitmap;
        try (InputStream is = context.getContentResolver().openInputStream(imageUri)) {
            if (is == null) throw new Exception("Unable to open image stream");
            bitmap = BitmapFactory.decodeStream(is, null, options);
        }

        if (bitmap == null) {
            return InputImage.fromFilePath(context, imageUri);
        }

        // 3. Read EXIF orientation to correct camera rotation
        int rotationDegrees = 0;
        try (InputStream is = context.getContentResolver().openInputStream(imageUri)) {
            if (is != null) {
                ExifInterface exif = new ExifInterface(is);
                int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                switch (orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        rotationDegrees = 90;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        rotationDegrees = 180;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        rotationDegrees = 270;
                        break;
                }
            }
        } catch (Exception ignored) {
        }

        return InputImage.fromBitmap(bitmap, rotationDegrees);
    }
}
