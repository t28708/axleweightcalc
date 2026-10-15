package com.drive.axleweightcalc;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Bottom Sheet Dialog Fragment for choosing how to provide a scale ticket.
 * Handles process death and low-memory kills during camera launch via setFragmentResult
 * and persistent URI state.
 */
public class ScanTicketBottomSheet extends BottomSheetDialogFragment {

    public static final String REQUEST_KEY = "scan_ticket_result";
    public static final String BUNDLE_KEY_URI = "ticket_uri";
    public static final String BUNDLE_KEY_TYPE = "ticket_type";

    private static final String PREF_CAMERA_URI = "pending_camera_photo_uri";
    private static final String STATE_CAMERA_URI = "state_camera_photo_uri";

    public interface OnTicketSelectedListener {
        void onTicketSelected(Uri uri, String type);
    }

    private OnTicketSelectedListener listener;
    private Uri cameraPhotoUri;

    // -------------------------------------------------------------------------
    // Activity Result Launchers
    // -------------------------------------------------------------------------

    /** Launcher for ML Kit Document Scanner API */
    private final ActivityResultLauncher<IntentSenderRequest> docScannerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    GmsDocumentScanningResult scanResult =
                            GmsDocumentScanningResult.fromActivityResultIntent(result.getData());
                    if (scanResult != null && scanResult.getPages() != null && !scanResult.getPages().isEmpty()) {
                        Uri croppedUri = scanResult.getPages().get(0).getImageUri();
                        notifyAndDismiss(croppedUri, "camera");
                    }
                }
            });

    /** Launcher for requesting CAMERA permission */
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    launchCamera();
                } else if (getContext() != null) {
                    Toast.makeText(requireContext(),
                            getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show();
                }
            });

    /** Fallback launcher for standard camera intent */
    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Uri targetUri = cameraPhotoUri != null ? cameraPhotoUri : getSavedCameraUri();
                    if (targetUri != null) {
                        notifyAndDismiss(targetUri, "camera");
                    }
                }
            });

    /** Launcher for gallery image picker (image/*) */
    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    notifyAndDismiss(uri, "gallery");
                }
            });

    /** Launcher for PDF file picker (application/pdf) */
    private final ActivityResultLauncher<String[]> pdfLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null && getContext() != null) {
                    try {
                        requireContext().getContentResolver()
                                .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {}
                    notifyAndDismiss(uri, "pdf");
                }
            });

    public static ScanTicketBottomSheet newInstance() {
        return new ScanTicketBottomSheet();
    }

    public void setOnTicketSelectedListener(OnTicketSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            String uriStr = savedInstanceState.getString(STATE_CAMERA_URI);
            if (uriStr != null) {
                cameraPhotoUri = Uri.parse(uriStr);
            }
        }
        if (cameraPhotoUri == null) {
            cameraPhotoUri = getSavedCameraUri();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (cameraPhotoUri != null) {
            outState.putString(STATE_CAMERA_URI, cameraPhotoUri.toString());
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_scan_ticket, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Camera / Document Scanner option
        view.findViewById(R.id.option_camera).setOnClickListener(v -> {
            if (getContext() == null) return;
            boolean hasCameraPermission = ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;

            if (hasCameraPermission) {
                launchCamera();
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            }
        });

        // Gallery option
        view.findViewById(R.id.option_gallery).setOnClickListener(v ->
                galleryLauncher.launch("image/*")
        );

        // PDF option
        view.findViewById(R.id.option_pdf).setOnClickListener(v ->
                pdfLauncher.launch(new String[]{"application/pdf"})
        );
    }

    private void launchCamera() {
        if (getActivity() == null) return;
        try {
            GmsDocumentScannerOptions options = new GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setPageLimit(1)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build();

            GmsDocumentScanner scanner = GmsDocumentScanning.getClient(options);

            scanner.getStartScanIntent(requireActivity())
                    .addOnSuccessListener(intentSender -> {
                        IntentSenderRequest request = new IntentSenderRequest.Builder(intentSender).build();
                        docScannerLauncher.launch(request);
                    })
                    .addOnFailureListener(e -> launchStandardCamera());
        } catch (Exception e) {
            launchStandardCamera();
        }
    }

    private void launchStandardCamera() {
        if (getContext() == null) return;
        File photoFile;
        try {
            photoFile = createImageFile();
        } catch (IOException e) {
            Toast.makeText(requireContext(), getString(R.string.error_create_image_file), Toast.LENGTH_SHORT).show();
            return;
        }

        cameraPhotoUri = FileProvider.getUriForFile(
                requireContext(),
                requireContext().getPackageName() + ".fileprovider",
                photoFile);

        saveCameraUri(cameraPhotoUri);

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
        cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        cameraLauncher.launch(cameraIntent);
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "TICKET_" + timeStamp;
        File storageDir = requireContext().getExternalCacheDir();
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void saveCameraUri(Uri uri) {
        if (getContext() == null || uri == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_CAMERA_URI, uri.toString()).apply();
    }

    private Uri getSavedCameraUri() {
        if (getContext() == null) return null;
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        String uriStr = prefs.getString(PREF_CAMERA_URI, null);
        return uriStr != null ? Uri.parse(uriStr) : null;
    }

    private void clearSavedCameraUri() {
        if (getContext() == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        prefs.edit().remove(PREF_CAMERA_URI).apply();
    }

    /** Delivers the result via setFragmentResult & listener, then closes the sheet */
    private void notifyAndDismiss(Uri uri, String type) {
        clearSavedCameraUri();

        // 1. Notify via Fragment Result API (survives LMK / process death)
        Bundle result = new Bundle();
        result.putParcelable(BUNDLE_KEY_URI, uri);
        result.putString(BUNDLE_KEY_TYPE, type);
        getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);

        // 2. Direct listener callback if available
        if (listener != null) {
            listener.onTicketSelected(uri, type);
        }

        dismissAllowingStateLoss();
    }
}
