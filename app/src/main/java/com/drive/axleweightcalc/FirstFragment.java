package com.drive.axleweightcalc;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.drive.axleweightcalc.databinding.FragmentFirstBinding;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentFirstBinding.inflate(inflater, container, false);
        // Clear focus when touching outside
        binding.getRoot().setOnClickListener(v -> clearInputsFocus());
        return binding.getRoot();

    }



    private int currentHole = 5; // Default "Current" Hole
    private int proposedDelta = 0; // "What-if" relative simulation delta

    // Snap-Back Base Values (Now just "Current Scale Ticket" values)
    private int baseSteer = 11000;
    private int baseDrive = 33400;
    private int baseTrailer = 33800;
    private boolean isProgrammaticUpdate = false;
    private boolean isFullTruckScaleMode = false;
    private boolean isDisclaimerExpanded = false;
    
    // Listener as a field for "Silent Toggle" - REMOVED
    // private android.widget.CompoundButton.OnCheckedChangeListener calibrationToggleListener;

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        loadSavedValues();
        setupScanTicketResultListener();
        setupLanguageSwitcher();
        updateLanguageDisplay();
        setupInputs();
        setupFtsInputs();
        setupToggle();
        setupTandemSlider();
        setupDisclaimer();
        setupRatePrompt();
        calculateTotals();
        proposedDelta = 0;
        updateRecommendationCardUI(0);
    }

    private void setupTandemSlider() {
        // Setup Spinner
        java.util.List<String> spacingOptions = new java.util.ArrayList<>();
        spacingOptions.add(getString(R.string.spinner_option_6));
        spacingOptions.add(getString(R.string.spinner_option_4));
        
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                requireContext(), 
                R.layout.spinner_item, 
                spacingOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerHoleSpacing.setAdapter(adapter);
        
        binding.spinnerHoleSpacing.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                calculateTotals();
                updateRecommendationCardUI(proposedDelta);
                saveValues();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        
        // Setup Calculate Button
        binding.btnCalculate.setOnClickListener(v -> {
            clearInputsFocus();
            performOptimization();
        });
        
        // Setup Recommendation Adjustment Buttons (- and +)
        binding.btnRecLeft.setOnClickListener(v -> {
            if (proposedDelta > -35) { // Min -35
                proposedDelta--;
                updateRecommendationCardUI(proposedDelta);
            }
        });
        
        binding.btnRecRight.setOnClickListener(v -> {
            if (proposedDelta < 35) { // Max +35
                proposedDelta++;
                updateRecommendationCardUI(proposedDelta);
            }
        });

        // Setup Reset Button
        binding.btnRecReset.setOnClickListener(v -> {
            proposedDelta = 0;
            updateRecommendationCardUI(0);
        });
    }
    
    private void resetCalculationState() {
        proposedDelta = 0;
        updateRecommendationCardUI(0);
    }
    
    private void performOptimization() {
        calculateTotals();
        
        // Get current values
        int steer, drive, trailer;
        if (isFullTruckScaleMode) {
             steer = baseSteer;
             drive = baseDrive;
             trailer = baseTrailer;
        } else {
             steer = parseWeight(binding.editSteerWeight.getText().toString());
             drive = parseWeight(binding.editSteerDriveWeight.getText().toString());
             trailer = parseWeight(binding.editTotalWeight.getText().toString());
        }
        
        calculateRecommendation(steer, drive, trailer);
        
        // Ensure card and calculate button remain visible
        binding.cardRecommendation.setVisibility(android.view.View.VISIBLE);
        binding.btnCalculate.setVisibility(android.view.View.VISIBLE);
        
        scrollToRecommendation();
    }

    private void scrollToRecommendation() {
        if (binding == null) return;
        binding.getRoot().postDelayed(() -> {
            if (binding == null) return;
            android.view.View target = binding.cardRecommendation;
            if (target == null) return;

            if (binding.getRoot() instanceof androidx.core.widget.NestedScrollView) {
                androidx.core.widget.NestedScrollView scrollView = (androidx.core.widget.NestedScrollView) binding.getRoot();

                int cardTopY = 0;
                android.view.View v = target;
                while (v != null && v != scrollView) {
                    cardTopY += v.getTop();
                    if (v.getParent() instanceof android.view.View) {
                        v = (android.view.View) v.getParent();
                    } else {
                        break;
                    }
                }

                int cardBottomY = cardTopY + target.getHeight();
                int viewportHeight = scrollView.getHeight();
                float density = getResources().getDisplayMetrics().density;
                int bottomPadding = (int) (16 * density);

                // Align the bottom of the truck window right at the bottom of the screen
                int scrollY = Math.max(0, cardBottomY - viewportHeight + bottomPadding);
                int startY = scrollView.getScrollY();

                if (Math.abs(scrollY - startY) > 10) {
                    android.animation.ObjectAnimator animator = android.animation.ObjectAnimator.ofInt(scrollView, "scrollY", startY, scrollY);
                    animator.setDuration(550);
                    animator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
                    animator.start();
                }
            }
        }, 150);
    }

    private void loadSavedValues() {
        android.content.SharedPreferences sharedPref = requireActivity().getPreferences(android.content.Context.MODE_PRIVATE);
        
        // Defaults if nothing saved
        String steer = sharedPref.getString("saved_steer", "11000");
        String drive = sharedPref.getString("saved_drive", "33400");
        String trailer = sharedPref.getString("saved_trailer", "33800");

        binding.editSteerWeight.setText(steer);
        binding.editSteerDriveWeight.setText(drive);
        binding.editTotalWeight.setText(trailer);
        
        baseSteer = parseWeight(steer);
        baseDrive = parseWeight(drive);
        baseTrailer = parseWeight(trailer);
    }

    private void setupInputs() {
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (!isProgrammaticUpdate) {
                     // User is manually editing weights.
                     baseSteer = parseWeight(binding.editSteerWeight.getText().toString());
                     baseDrive = parseWeight(binding.editSteerDriveWeight.getText().toString());
                     baseTrailer = parseWeight(binding.editTotalWeight.getText().toString());
                     
                     resetCalculationState(); // Reset on input change
                }
                calculateTotals();
                saveValues();
            }
        };

        binding.editSteerWeight.addTextChangedListener(watcher);
        binding.editSteerDriveWeight.addTextChangedListener(watcher);
        binding.editTotalWeight.addTextChangedListener(watcher);

        // Clear focus on "Done" action
        android.widget.TextView.OnEditorActionListener actionListener = (v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT ||
                (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                clearInputsFocus();
                return true;
            }
            return false;
        };

        binding.editSteerWeight.setOnEditorActionListener(actionListener);
        binding.editSteerDriveWeight.setOnEditorActionListener(actionListener);
        binding.editTotalWeight.setOnEditorActionListener(actionListener);
    }
    
    private void clearInputsFocus() {
        // Hide keyboard FIRST using the View that currently has focus (or root)
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) 
            requireActivity().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        
        android.view.View view = requireActivity().getCurrentFocus();
        if (view == null) {
            view = binding.getRoot();
        }

        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }

        // THEN clear focus from all inputs
        binding.editSteerWeight.clearFocus();
        binding.editSteerDriveWeight.clearFocus();
        binding.editTotalWeight.clearFocus();
        binding.editFtsSteerWeight.clearFocus();
        binding.editFtsSteerDrive.clearFocus();
        binding.editFtsTotalWeight.clearFocus();
    }
    
    private void setupFtsInputs() {
        android.text.TextWatcher ftsWatcher = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (!isProgrammaticUpdate && isFullTruckScaleMode) {
                    resetCalculationState(); // Reset on input change
                    calculateTotals();
                    saveValues();
                }
            }
        };

        binding.editFtsSteerWeight.addTextChangedListener(ftsWatcher);
        binding.editFtsSteerDrive.addTextChangedListener(ftsWatcher);
        binding.editFtsTotalWeight.addTextChangedListener(ftsWatcher);
        
        // ... (Listeners same) ...
        android.widget.TextView.OnEditorActionListener ftsActionListener = (v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT ||
                (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                clearInputsFocus();
                return true;
            }
            return false;
        };

        binding.editFtsSteerWeight.setOnEditorActionListener(ftsActionListener);
        binding.editFtsSteerDrive.setOnEditorActionListener(ftsActionListener);
        binding.editFtsTotalWeight.setOnEditorActionListener(ftsActionListener);
    }


    private void saveValues() {
        android.content.SharedPreferences sharedPref = requireActivity().getPreferences(android.content.Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = sharedPref.edit();
        
        editor.putString("saved_steer", binding.editSteerWeight.getText().toString());
        editor.putString("saved_drive", binding.editSteerDriveWeight.getText().toString());
        editor.putString("saved_trailer", binding.editTotalWeight.getText().toString());
        editor.putInt("saved_hole", currentHole);
        
        // Save Tandem Logic State (Only simple ones needed)
        editor.putInt("saved_spacing_index", binding.spinnerHoleSpacing.getSelectedItemPosition());
        editor.apply();
    }


    private void setupDisclaimer() {
        binding.disclaimerHeader.setOnClickListener(v -> {
            isDisclaimerExpanded = !isDisclaimerExpanded;
            if (isDisclaimerExpanded) {
                binding.textDisclaimerContent.setVisibility(android.view.View.VISIBLE);
                binding.textDisclaimerContent.setAlpha(0f);
                binding.textDisclaimerContent.animate().alpha(1f).setDuration(200).start();
                binding.iconDisclaimerArrow.animate().rotation(180f).setDuration(200).start();
            } else {
                binding.textDisclaimerContent.animate().alpha(0f).setDuration(150)
                    .withEndAction(() -> binding.textDisclaimerContent.setVisibility(android.view.View.GONE)).start();
                binding.iconDisclaimerArrow.animate().rotation(0f).setDuration(200).start();
            }
        });
    }

    private void setupScanTicketResultListener() {
        getChildFragmentManager().setFragmentResultListener(
                ScanTicketBottomSheet.REQUEST_KEY,
                getViewLifecycleOwner(),
                (requestKey, bundle) -> {
                    android.net.Uri uri = bundle.getParcelable(ScanTicketBottomSheet.BUNDLE_KEY_URI);
                    String type = bundle.getString(ScanTicketBottomSheet.BUNDLE_KEY_TYPE);
                    if (uri != null) {
                        if ("pdf".equals(type)) {
                            parsePdfAndFill(uri);
                        } else if ("gallery".equals(type) || "camera".equals(type)) {
                            parseImageAndFill(uri);
                        }
                    }
                }
        );
    }

    private void setupToggle() {
        binding.btnFullTruckScale.setOnClickListener(v -> updateScaleMode(true));
        binding.btnAxleScale.setOnClickListener(v -> updateScaleMode(false));

        // Scan Scale Ticket button — opens the Bottom Sheet chooser
        binding.btnScanTicket.setOnClickListener(v -> {
            ScanTicketBottomSheet sheet = ScanTicketBottomSheet.newInstance();
            sheet.show(getChildFragmentManager(), "ScanTicketBottomSheet");
        });
    }

    /**
     * Runs ML Kit OCR on an image/screenshot (Gallery or Camera) on a background thread,
     * shows a progress spinner, then populates fields in Axle-by-Axle mode.
     */
    private void parseImageAndFill(android.net.Uri imageUri) {
        if (!isAdded() || getContext() == null) return;

        android.widget.ProgressBar progressBar = new android.widget.ProgressBar(requireContext());
        progressBar.setPadding(48, 48, 48, 48);

        androidx.appcompat.app.AlertDialog progress = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Scanning scale ticket…")
                .setMessage("Processing ticket with ML Kit OCR…")
                .setView(progressBar)
                .setCancelable(false)
                .create();

        try {
            progress.show();
        } catch (Exception ignored) {}

        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        android.content.Context appContext = requireContext().getApplicationContext();

        java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            ParsedTicket ticket = null;
            String errorMsg = null;
            try {
                ticket = ImageTicketParser.parse(appContext, imageUri);
            } catch (OutOfMemoryError oom) {
                errorMsg = "Out of memory while processing image. Please try taking a closer, focused shot.";
                android.util.Log.e("FirstFragment", "OOM in Image OCR", oom);
            } catch (Exception e) {
                errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                android.util.Log.e("FirstFragment", "Image OCR parse failed", e);
            }

            final ParsedTicket finalTicket = ticket;
            final String finalError = errorMsg;

            mainHandler.post(() -> {
                try {
                    if (progress.isShowing()) progress.dismiss();
                } catch (Exception ignored) {}

                if (!isAdded() || getContext() == null) return;

                if (finalError != null) {
                    showParseError(finalError);
                } else if (finalTicket == null || !finalTicket.isValid()) {
                    showParseError("Could not find weight values in this image.\n\n" +
                            "Make sure the ticket image or screenshot is clear and readable.\n" +
                            "Fields found: steer=" + (finalTicket != null ? finalTicket.steerAxle : 0) +
                            " drive=" + (finalTicket != null ? finalTicket.driveAxle : 0) +
                            " trailer=" + (finalTicket != null ? finalTicket.trailerAxle : 0));
                } else {
                    fillFromTicket(finalTicket);
                }
            });
        });

        executor.shutdown();
    }

    /**
     * Runs PdfTicketParser on a background thread, shows a spinner while
     * parsing, then fills the Axle-by-Axle fields on the main thread.
     */
    private void parsePdfAndFill(android.net.Uri pdfUri) {
        if (!isAdded() || getContext() == null) return;

        android.widget.ProgressBar progressBar = new android.widget.ProgressBar(requireContext());
        progressBar.setPadding(48, 48, 48, 48);

        androidx.appcompat.app.AlertDialog progress = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Reading scale ticket…")
                .setMessage("Extracting weights from PDF…")
                .setView(progressBar)
                .setCancelable(false)
                .create();

        try {
            progress.show();
        } catch (Exception ignored) {}

        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        android.content.Context appContext = requireContext().getApplicationContext();

        java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            ParsedTicket ticket = null;
            String errorMsg = null;
            try {
                ticket = PdfTicketParser.parse(appContext, pdfUri);
            } catch (Exception e) {
                errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                android.util.Log.e("FirstFragment", "PDF parse failed", e);
            }

            final ParsedTicket finalTicket = ticket;
            final String finalError = errorMsg;

            mainHandler.post(() -> {
                try {
                    if (progress.isShowing()) progress.dismiss();
                } catch (Exception ignored) {}

                if (!isAdded() || getContext() == null) return;

                if (finalError != null) {
                    showParseError(finalError);
                } else if (finalTicket == null || !finalTicket.isValid()) {
                    showParseError("Could not find weight values in this PDF.\n\n" +
                            "Make sure the PDF contains a text layer (not a scanned image). " +
                            "Fields found: steer=" + (finalTicket != null ? finalTicket.steerAxle : 0) +
                            " drive=" + (finalTicket != null ? finalTicket.driveAxle : 0) +
                            " trailer=" + (finalTicket != null ? finalTicket.trailerAxle : 0));
                } else {
                    fillFromTicket(finalTicket);
                }
            });
        });

        executor.shutdown();
    }

    /**
     * Switches UI to Axle-by-Axle mode, populates the three weight fields,
     * automatically runs the optimization algorithm, and displays a complete
     * summary dialog with scanned weights and target hole instructions.
     */
    private void fillFromTicket(ParsedTicket ticket) {
        // 1. Switch to Axle-by-Axle mode (if not already)
        updateScaleMode(false);

        // 2. Fill the fields (suppress TextWatcher re-calculation temporarily)
        isProgrammaticUpdate = true;
        binding.editSteerWeight.setText(String.valueOf(ticket.steerAxle));
        binding.editSteerDriveWeight.setText(String.valueOf(ticket.driveAxle));
        binding.editTotalWeight.setText(String.valueOf(ticket.trailerAxle));
        isProgrammaticUpdate = false;

        // 3. Update base values so optimization uses the ticket data
        baseSteer   = ticket.steerAxle;
        baseDrive   = ticket.driveAxle;
        baseTrailer = ticket.trailerAxle;

        // 4. Recalculate totals and save values
        resetCalculationState();
        calculateTotals();
        saveValues();

        // 5. Automatically trigger the optimization calculation
        performOptimization();

        // 6. Calculate recommendation parameters for the dialog
        boolean isSixInch = binding.spinnerHoleSpacing.getSelectedItemPosition() == 0;
        int weightPerHole = isSixInch ? 400 : 250;
        int deltaHoles = proposedDelta;
        
        final int STEER_LEGAL_LIMIT = 12000;
        final int LBS_PER_5TH_WHEEL_HOLE = 300;
        
        int fifthWheelHoles = 0;
        int projectedSteer = ticket.steerAxle;
        int fifthWheelShiftWeight = 0;
        
        if (ticket.steerAxle > STEER_LEGAL_LIMIT) {
            int steerOverweight = ticket.steerAxle - STEER_LEGAL_LIMIT;
            fifthWheelHoles = (int) Math.ceil((double) steerOverweight / LBS_PER_5TH_WHEEL_HOLE);
            fifthWheelShiftWeight = fifthWheelHoles * LBS_PER_5TH_WHEEL_HOLE;
            projectedSteer = ticket.steerAxle - fifthWheelShiftWeight;
        }

        int tandemDriveShift = deltaHoles * weightPerHole;
        int projectedDrive = ticket.driveAxle + tandemDriveShift + fifthWheelShiftWeight;
        int projectedTrailer = ticket.trailerAxle - tandemDriveShift;

        String actionStr;
        if (deltaHoles > 0) {
            actionStr = "Move " + deltaHoles + " holes (+)";
        } else if (deltaHoles < 0) {
            actionStr = "Move " + Math.abs(deltaHoles) + " holes (-)";
        } else {
            actionStr = "Optimal position (No move needed)";
        }

        // 7. Build dialog text
        StringBuilder summary = new StringBuilder();
        summary.append("✓ Scale Ticket Loaded & Calculated!\n\n");

        summary.append("SCANNED WEIGHTS:\n");
        summary.append("• Steer:   ").append(String.format(java.util.Locale.US, "%,d", ticket.steerAxle)).append(" lbs\n");
        summary.append("• Drive:   ").append(String.format(java.util.Locale.US, "%,d", ticket.driveAxle)).append(" lbs\n");
        summary.append("• Trailer: ").append(String.format(java.util.Locale.US, "%,d", ticket.trailerAxle)).append(" lbs\n");
        int calculatedGross = ticket.steerAxle + ticket.driveAxle + ticket.trailerAxle;
        summary.append("• Gross:   ").append(String.format(java.util.Locale.US, "%,d", calculatedGross)).append(" lbs\n");

        summary.append("\nRECOMMENDED ADJUSTMENT:\n");
        summary.append("• Trailer: ").append(actionStr).append("\n");
        if (fifthWheelHoles > 0) {
            summary.append("• 5th Wheel: Slide ").append(fifthWheelHoles).append(" hole").append(fifthWheelHoles > 1 ? "s" : "").append(" back (away from cab)\n");
            summary.append("  Note: Slides ~300 lbs per hole from Steer to Drive\n");
        }
        summary.append("\n");

        summary.append("PROJECTED WEIGHTS AFTER SHIFT:\n");
        summary.append("• After Steer:   ").append(String.format(java.util.Locale.US, "%,d", projectedSteer)).append(" lbs\n");
        summary.append("• After Drive:   ").append(String.format(java.util.Locale.US, "%,d", projectedDrive)).append(" lbs\n");
        summary.append("• After Trailer: ").append(String.format(java.util.Locale.US, "%,d", projectedTrailer)).append(" lbs");

        if (projectedDrive > 34000) {
            summary.append("\n\n⚠️ Warning: Moving weight from Steer will overload Drives (")
                   .append(String.format(java.util.Locale.US, "%,d", projectedDrive))
                   .append(" lbs > 34,000 lbs). Load needs redistribution.");
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Ticket Loaded & Optimized")
                .setMessage(summary.toString())
                .setPositiveButton("OK", (dialog, which) -> scrollToRecommendation())
                .setOnDismissListener(dialog -> scrollToRecommendation())
                .show();
    }

    /** Shows a user-friendly error dialog and Toast when parsing fails or values aren't found. */
    private void showParseError(String detail) {
        android.widget.Toast.makeText(requireContext(),
                "Could not parse ticket automatically. Please enter weights manually.",
                android.widget.Toast.LENGTH_LONG).show();

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Scanning Failed")
                .setMessage(detail + "\n\nPlease enter the values manually into the axle weight fields.")
                .setPositiveButton("Enter Manually", (dialog, which) -> {
                    updateScaleMode(false); // Switch to Axle-by-Axle mode
                    binding.editSteerWeight.requestFocus();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    private void updateScaleMode(boolean isFullTruck) {
        isFullTruckScaleMode = isFullTruck;
        // ... (Keep existing UI toggle logic) ...
        // Replicating concise version derived from previous file content to avoid losing functionality
        if (isFullTruck) {
            binding.btnFullTruckScale.setBackgroundResource(R.drawable.bg_mode_segment_active);
            binding.textFullTruckLabel.setTextColor(android.graphics.Color.WHITE);
            binding.btnAxleScale.setBackgroundResource(R.drawable.bg_mode_segment_inactive);
            binding.textAxleScaleLabel.setTextColor(android.graphics.Color.parseColor("#455A64"));
            animateCheckmark(binding.iconFullTruckCheck, true);
            animateCheckmark(binding.iconAxleCheck, false);
            animateCardVisibility(binding.cardFullTruckScale, true);
            binding.editSteerWeight.setEnabled(false);
            binding.editSteerDriveWeight.setEnabled(false);
            binding.editTotalWeight.setEnabled(false);
            binding.editSteerWeight.setFocusable(false);
            binding.editSteerDriveWeight.setFocusable(false);
            binding.editTotalWeight.setFocusable(false);
            binding.editSteerWeight.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            binding.editSteerDriveWeight.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            binding.editTotalWeight.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            int displayColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_secondary);
            binding.editSteerWeight.setTextColor(displayColor);
            binding.editSteerDriveWeight.setTextColor(displayColor);
            binding.editTotalWeight.setTextColor(displayColor);
        } else {
            binding.btnFullTruckScale.setBackgroundResource(R.drawable.bg_mode_segment_inactive);
            binding.textFullTruckLabel.setTextColor(android.graphics.Color.parseColor("#455A64"));
            binding.btnAxleScale.setBackgroundResource(R.drawable.bg_mode_segment_active);
            binding.textAxleScaleLabel.setTextColor(android.graphics.Color.WHITE);
            animateCheckmark(binding.iconAxleCheck, true);
            animateCheckmark(binding.iconFullTruckCheck, false);
            animateCardVisibility(binding.cardFullTruckScale, false);
            binding.editSteerWeight.setEnabled(true);
            binding.editSteerDriveWeight.setEnabled(true);
            binding.editTotalWeight.setEnabled(true);
            binding.editSteerWeight.setFocusableInTouchMode(true);
            binding.editSteerDriveWeight.setFocusableInTouchMode(true);
            binding.editTotalWeight.setFocusableInTouchMode(true);
            int bgColor = android.graphics.Color.parseColor("#F0F4FC");
            binding.editSteerWeight.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bgColor));
            binding.editSteerDriveWeight.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bgColor));
            binding.editTotalWeight.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bgColor));
        }
        calculateTotals();
    }
    
    private void animateCheckmark(android.view.View checkmark, boolean show) {
        if (show) {
            checkmark.setVisibility(android.view.View.VISIBLE);
            checkmark.setAlpha(0f);
            checkmark.setScaleX(0.5f);
            checkmark.setScaleY(0.5f);
            checkmark.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(new android.view.animation.OvershootInterpolator()).start();
        } else {
            checkmark.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(150).withEndAction(() -> checkmark.setVisibility(android.view.View.GONE)).start();
        }
    }
    
    private void animateCardVisibility(android.view.View card, boolean show) {
        if (show) {
            card.setVisibility(android.view.View.VISIBLE);
            card.setAlpha(0f);
            card.setTranslationY(-30f);
            card.animate().alpha(1f).translationY(0f).setDuration(250).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        } else {
            card.animate().alpha(0f).translationY(-30f).setDuration(200).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(() -> card.setVisibility(android.view.View.GONE)).start();
        }
    }

    private void calculateTotals() {
        int steer, drive, trailer;
        
        if (isFullTruckScaleMode) {
            int ftsSteerWeight = parseWeight(binding.editFtsSteerWeight.getText().toString());
            int ftsSteerDrive = parseWeight(binding.editFtsSteerDrive.getText().toString());
            int ftsTotalWeight = parseWeight(binding.editFtsTotalWeight.getText().toString());
            steer = ftsSteerWeight;
            drive = ftsSteerDrive - ftsSteerWeight;
            trailer = ftsTotalWeight - ftsSteerDrive;
            baseSteer = steer;
            baseDrive = drive;
            baseTrailer = trailer;
            
            // Sync visible simple inputs too
             if (!isProgrammaticUpdate) {
                isProgrammaticUpdate = true;
                binding.editSteerWeight.setText(String.valueOf(baseSteer));
                binding.editSteerDriveWeight.setText(String.valueOf(baseDrive));
                binding.editTotalWeight.setText(String.valueOf(baseTrailer));
                isProgrammaticUpdate = false;
             }
        } else {
            steer = parseWeight(binding.editSteerWeight.getText().toString());
            drive = parseWeight(binding.editSteerDriveWeight.getText().toString());
            trailer = parseWeight(binding.editTotalWeight.getText().toString());
            baseSteer = steer;
            baseDrive = drive;
            baseTrailer = trailer;
        }

        int total = steer + drive + trailer;
        int maxWeight = 80000;
        int remaining = maxWeight - total;

        binding.textTotalGross.setText(getString(R.string.label_gross_weight, total));
        binding.textRemaining.setText(getString(R.string.label_remaining_weight, remaining));
        binding.progressBarWeight.setMax(maxWeight);
        binding.progressBarWeight.setProgress(total);

        // --- Recommendation Logic ---
        // Removed: Now triggered only by button press
        // calculateRecommendation(steer, drive, trailer);
        
        // --- Overweight Visualization (Input Colors) ---
        int colorSafe = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.brand_dark_blue);
        int colorOverweight = androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark);
        
        binding.editSteerWeight.setTextColor(steer > 12000 ? colorOverweight : colorSafe);
        binding.editSteerDriveWeight.setTextColor(drive > 34000 ? colorOverweight : colorSafe);
        binding.editTotalWeight.setTextColor(trailer > 34000 ? colorOverweight : colorSafe);
    }
    
    private void calculateRecommendation(int steer, int currentDrive, int currentTrailer) {
        boolean isSixInch = binding.spinnerHoleSpacing.getSelectedItemPosition() == 0;
        int weightPerHole = isSixInch ? 400 : 250;
        int safetyLimit = 33700; // 34000 - 300 buffer
        int legalLimit = 34000;
        
        final int STEER_LEGAL_LIMIT = 12000;
        final int LBS_PER_5TH_WHEEL_HOLE = 300;
        int fifthWheelShiftWeight = 0;
        if (steer > STEER_LEGAL_LIMIT) {
            int steerOverweight = steer - STEER_LEGAL_LIMIT;
            int fifthWheelHoles = (int) Math.ceil((double) steerOverweight / LBS_PER_5TH_WHEEL_HOLE);
            fifthWheelShiftWeight = fifthWheelHoles * LBS_PER_5TH_WHEEL_HOLE;
        }

        // Effective Drive weight includes weight transferred from Steer via 5th wheel shift
        int effectiveDrive = currentDrive + fifthWheelShiftWeight;

        int bestSafeDelta = Integer.MAX_VALUE;      // Both <= 33,700
        int minSafeDiff = Integer.MAX_VALUE;
        
        int bestLegalDelta = Integer.MAX_VALUE;     // Both <= 34,000
        int minLegalDiff = Integer.MAX_VALUE;
        
        int bestBurnOffDelta = Integer.MAX_VALUE;   // Trailer <= 34,000 (Drive has slight excess that burns off with fuel)
        int minBurnOffDrive = Integer.MAX_VALUE;
        
        int bestRiskDelta = Integer.MAX_VALUE;      // Minimax fallback
        int minMaxWeight = Integer.MAX_VALUE;
        
        // Iteration Range: -35 to +35 relative holes
        for (int delta = -35; delta <= 35; delta++) {
            
            // Physics Calculation:
            // +Shift (Towards Rear) -> Drive +, Trailer -
            // -Shift (Towards Front) -> Drive -, Trailer +
            int absoluteShift = delta * weightPerHole;
            int candidateDrive = effectiveDrive + absoluteShift;
            int candidateTrailer = currentTrailer - absoluteShift;
            
            int diff = Math.abs(candidateDrive - candidateTrailer);
            
            // 1. Both under safety buffer (<= 33,700)
            if (candidateDrive <= safetyLimit && candidateTrailer <= safetyLimit) {
                if (diff < minSafeDiff || (diff == minSafeDiff && Math.abs(delta) < Math.abs(bestSafeDelta))) {
                    minSafeDiff = diff;
                    bestSafeDelta = delta;
                }
            }
            
            // 2. Both strictly legal (<= 34,000)
            if (candidateDrive <= legalLimit && candidateTrailer <= legalLimit) {
                if (diff < minLegalDiff || (diff == minLegalDiff && Math.abs(delta) < Math.abs(bestLegalDelta))) {
                    minLegalDiff = diff;
                    bestLegalDelta = delta;
                }
            }
            
            // 3. Fuel Burn-off Strategy (Trucker physics):
            // Trailer MUST be legal (<= 34,000) because trailer weight cannot decrease during the trip.
            // Putting the slight excess on the Drive axle is preferred because burning diesel
            // fuel directly unloads the tractor/drive axles as you drive.
            if (candidateTrailer <= legalLimit && candidateDrive > legalLimit) {
                if (candidateDrive < minBurnOffDrive) {
                    minBurnOffDrive = candidateDrive;
                    bestBurnOffDelta = delta;
                }
            }
            
            // 4. Minimax fallback
            int currentMaxWeight = Math.max(candidateDrive, candidateTrailer);
            if (currentMaxWeight < minMaxWeight) {
                minMaxWeight = currentMaxWeight;
                bestRiskDelta = delta;
            }
        }
        
        int finalBestDelta;
        if (bestSafeDelta != Integer.MAX_VALUE) {
            finalBestDelta = bestSafeDelta;
        } else if (bestLegalDelta != Integer.MAX_VALUE) {
            finalBestDelta = bestLegalDelta;
        } else if (bestBurnOffDelta != Integer.MAX_VALUE) {
            finalBestDelta = bestBurnOffDelta;
        } else {
            finalBestDelta = (bestRiskDelta != Integer.MAX_VALUE) ? bestRiskDelta : 0;
        }
        
        // Initial Proposed Delta is the Optimization Result
        proposedDelta = finalBestDelta;
        updateRecommendationCardUI(proposedDelta);
    }
    
    private void updateRecommendationCardUI(int deltaHoles) {
        int steer, currentDrive, currentTrailer;
        if (isFullTruckScaleMode) {
             steer = baseSteer;
             currentDrive = baseDrive;
             currentTrailer = baseTrailer;
        } else {
             steer = parseWeight(binding.editSteerWeight.getText().toString());
             currentDrive = parseWeight(binding.editSteerDriveWeight.getText().toString());
             currentTrailer = parseWeight(binding.editTotalWeight.getText().toString());
        }
        
        final int STEER_LEGAL_LIMIT = 12000;
        final int LBS_PER_5TH_WHEEL_HOLE = 300;
        
        int fifthWheelHoles = 0;
        int fifthWheelShiftWeight = 0;
        if (steer > STEER_LEGAL_LIMIT) {
            int steerOverweight = steer - STEER_LEGAL_LIMIT;
            fifthWheelHoles = (int) Math.ceil((double) steerOverweight / LBS_PER_5TH_WHEEL_HOLE);
            fifthWheelShiftWeight = fifthWheelHoles * LBS_PER_5TH_WHEEL_HOLE;
        }

        boolean isSixInch = binding.spinnerHoleSpacing.getSelectedItemPosition() == 0;
        int weightPerHole = isSixInch ? 400 : 250;
        
        int absoluteShift = deltaHoles * weightPerHole;
        
        int newDrive = currentDrive + absoluteShift + fifthWheelShiftWeight;
        int newTrailer = currentTrailer - absoluteShift;
        
        // Update Current Stats UI
        if (currentDrive == 0 && currentTrailer == 0) {
            binding.textRecCurrentDrive.setText("—");
            binding.textRecCurrentTrailer.setText("—");
            binding.textRecNewDrive.setText("—");
            binding.textRecNewTrailer.setText("—");
        } else {
            binding.textRecCurrentDrive.setText(String.format(java.util.Locale.US, "%,d", currentDrive));
            binding.textRecCurrentTrailer.setText(String.format(java.util.Locale.US, "%,d", currentTrailer));
            binding.textRecNewDrive.setText(String.format(java.util.Locale.US, "%,d", newDrive));
            binding.textRecNewTrailer.setText(String.format(java.util.Locale.US, "%,d", newTrailer));
        }
        
        // UI Colors
        int colorGreen = android.graphics.Color.parseColor("#2E7D32");
        int colorRed = android.graphics.Color.parseColor("#C62828");
        int colorBlue = android.graphics.Color.parseColor("#1565C0");
        int colorOrange = android.graphics.Color.parseColor("#EF6C00");
        
        int bgGreen = android.graphics.Color.parseColor("#E8F5E9");
        int bgBlue = android.graphics.Color.parseColor("#E3F2FD");
        int bgRed = android.graphics.Color.parseColor("#FFEBEE");
        
        // Check Safety (33700) and Legal (34000)
        boolean hasWeights = currentDrive > 0 || currentTrailer > 0;
        boolean isIllegal = hasWeights && (newDrive > 34000 || newTrailer > 34000);
        boolean isUnsafe = hasWeights && (newDrive > 33700 || newTrailer > 33700);
        
        android.view.View dividerWarning = binding.getRoot().findViewById(R.id.divider_warning_bottom);
        android.widget.TextView textWarning = binding.getRoot().findViewById(R.id.text_warning_bottom);

        // Card Background
        if (isIllegal) {
             binding.cardRecommendation.setCardBackgroundColor(bgRed);
        } else if (isUnsafe) {
             binding.cardRecommendation.setCardBackgroundColor(bgBlue);
        } else if (deltaHoles == 0 && fifthWheelHoles == 0) {
             binding.cardRecommendation.setCardBackgroundColor(bgGreen);
        } else {
             binding.cardRecommendation.setCardBackgroundColor(bgBlue);
        }
        
        // 5th Wheel Banner (under moving truck animation)
        if (fifthWheelHoles > 0) {
            binding.layoutFifthWheelRec.setVisibility(android.view.View.VISIBLE);
            binding.textFifthWheelTitle.setText(getString(R.string.rec_fifth_wheel_title, fifthWheelHoles));
            binding.textFifthWheelSubtitle.setText(getString(R.string.rec_fifth_wheel_subtitle));
        } else {
            binding.layoutFifthWheelRec.setVisibility(android.view.View.GONE);
        }

        // Bottom Warning Visibility
        if (isIllegal) {
             if (dividerWarning != null && textWarning != null) {
                 dividerWarning.setVisibility(android.view.View.VISIBLE);
                 textWarning.setVisibility(android.view.View.VISIBLE);
                 dividerWarning.setBackgroundColor(colorRed);
                 textWarning.setTextColor(colorRed);
                 
                 if (fifthWheelHoles > 0 && newDrive > 34000) {
                     textWarning.setText(getString(R.string.warning_steer_overload_drives));
                 } else {
                     String title = getString(R.string.rec_title_overweight);
                     String subtitle = getString(R.string.rec_subtitle_overweight);
                     textWarning.setText(title + "\n" + subtitle);
                 }
             }
        } else if (isUnsafe) {
             if (dividerWarning != null && textWarning != null) {
                 dividerWarning.setVisibility(android.view.View.VISIBLE);
                 textWarning.setVisibility(android.view.View.VISIBLE);
                 dividerWarning.setBackgroundColor(colorOrange);
                 textWarning.setTextColor(colorOrange);
                 
                 String title = getString(R.string.rec_title_caution);
                 String subtitle = getString(R.string.rec_subtitle_caution);
                 textWarning.setText(title + "\n" + subtitle);
             }
        } else {
             if (dividerWarning != null) dividerWarning.setVisibility(android.view.View.GONE);
             if (textWarning != null) textWarning.setVisibility(android.view.View.GONE);
        }

        // Title & Color
        if (deltaHoles == 0) {
             if (isIllegal) {
                 binding.textRecommendationTitle.setTextColor(colorRed);
                 binding.textRecommendationTitle.setText("No move (0 holes)");
                 binding.textRecommendationSubtitle.setVisibility(android.view.View.VISIBLE);
                 binding.textRecommendationSubtitle.setText(getString(R.string.rec_subtitle_overweight));
             } else if (isUnsafe) {
                 binding.textRecommendationTitle.setTextColor(colorOrange);
                 binding.textRecommendationTitle.setText("No move (0 holes)");
                 binding.textRecommendationSubtitle.setVisibility(android.view.View.VISIBLE);
                 binding.textRecommendationSubtitle.setText(getString(R.string.rec_subtitle_caution));
             } else {
                 // Perfect / Neutral
                 binding.textRecommendationTitle.setTextColor(colorGreen);
                 binding.textRecommendationTitle.setText(getString(R.string.rec_title_perfect));
                 binding.textRecommendationSubtitle.setVisibility(android.view.View.VISIBLE);
                 binding.textRecommendationSubtitle.setText(getString(R.string.rec_manual_hint));
             }
        } else {
             int titleColor = isIllegal ? colorRed : (isUnsafe ? colorOrange : colorBlue);
             binding.textRecommendationTitle.setTextColor(titleColor);
             
             String direction = deltaHoles > 0 ? getString(R.string.rec_move_forward) : getString(R.string.rec_move_backward);
             binding.textRecommendationTitle.setText(getString(R.string.rec_subtitle_move, Math.abs(deltaHoles), direction));
             binding.textRecommendationSubtitle.setVisibility(android.view.View.VISIBLE);
             binding.textRecommendationSubtitle.setText(getString(R.string.rec_manual_hint));
        }

        // Reset Button Visibility
        binding.btnRecReset.setVisibility(deltaHoles != 0 ? android.view.View.VISIBLE : android.view.View.GONE);

        // Always show navigation buttons so the driver can explore moving more or fewer holes
        binding.btnRecLeft.setVisibility(android.view.View.VISIBLE);
        binding.btnRecRight.setVisibility(android.view.View.VISIBLE);
        
        // Always show stats for "What-if"
        binding.layoutRecStats.setVisibility(android.view.View.VISIBLE);
        binding.dividerRecommendation.setVisibility(android.view.View.VISIBLE);
        
        // Colorize New Stats
        int driveColor = colorGreen;
        if (newDrive > 34000) driveColor = colorRed;
        else if (newDrive > 33700) driveColor = colorOrange;
        
        int trailerColor = colorGreen;
        if (newTrailer > 34000) trailerColor = colorRed;
        else if (newTrailer > 33700) trailerColor = colorOrange;

        binding.textRecNewDrive.setTextColor(driveColor);
        binding.textRecNewTrailer.setTextColor(trailerColor);
        
        // Enable/Disable Buttons
        binding.btnRecLeft.setEnabled(deltaHoles > -35);
        binding.btnRecLeft.setAlpha(deltaHoles > -35 ? 1.0f : 0.3f);
        
        binding.btnRecRight.setEnabled(deltaHoles < 35);
        binding.btnRecRight.setAlpha(deltaHoles < 35 ? 1.0f : 0.3f);

        // Tandem Movement Animation (Continuous translation, road parallax, glowing direction chevrons)
        if (binding.tandemMotionView != null) {
            binding.tandemMotionView.setMotionDirection(deltaHoles);
        }
    }

    private int parseWeight(String text) {
        try {
            return Integer.parseInt(text.replaceAll(",", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private void setupLanguageSwitcher() {
        android.view.View switcher = binding.getRoot().findViewById(R.id.btn_language_switcher);
        if (switcher != null) {
            switcher.setOnClickListener(v -> showLanguageDialog());
        }
    }

    private void updateLanguageDisplay() {
        android.widget.TextView langText = binding.getRoot().findViewById(R.id.text_current_language);
        if (langText != null && isAdded()) {
            String current = LanguageManager.getCurrentLanguage(requireContext());
            String display = "EN";
            if (current.equalsIgnoreCase("es")) display = "ES";
            else if (current.equalsIgnoreCase("ru")) display = "RU";
            else if (current.equalsIgnoreCase("fr")) display = "FR";
            else if (current.equalsIgnoreCase("pa")) display = "PA";
            else if (current.equalsIgnoreCase("uk")) display = "UK";
            else if (current.equalsIgnoreCase("uz")) display = "UZ";
            else if (current.equalsIgnoreCase("pl")) display = "PL";
            else if (current.equalsIgnoreCase("vi")) display = "VI";
            else if (current.equalsIgnoreCase("fil") || current.equalsIgnoreCase("tl")) display = "FIL";
            langText.setText(display);
        }
    }
    
    private void showLanguageDialog() {
        final String[] languages = {
                getString(R.string.lang_english),
                getString(R.string.lang_spanish),
                getString(R.string.lang_russian),
                getString(R.string.lang_french),
                getString(R.string.lang_punjabi),
                getString(R.string.lang_ukrainian),
                getString(R.string.lang_uzbek),
                getString(R.string.lang_polish),
                getString(R.string.lang_vietnamese),
                getString(R.string.lang_tagalog)
        };
        final String[] codes = {"en", "es", "ru", "fr", "pa", "uk", "uz", "pl", "vi", "fil"};
        
        int checkedItem = 0; // English default
        String current = LanguageManager.getCurrentLanguage(requireContext());
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equalsIgnoreCase(current) || (codes[i].equals("fil") && current.equalsIgnoreCase("tl"))) {
                checkedItem = i;
                break;
            }
        }
        
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_language_title)
            .setSingleChoiceItems(languages, checkedItem, null)
            .setPositiveButton(R.string.btn_save, (dialog, which) -> {
                int selectedPosition = ((androidx.appcompat.app.AlertDialog)dialog).getListView().getCheckedItemPosition();
                if (selectedPosition >= 0 && selectedPosition < codes.length) {
                    if (getActivity() != null) {
                        LanguageManager.applyLocaleSmoothly(getActivity(), codes[selectedPosition]);
                    }
                }
            })
            .show();
    }

    // Removed onDestroyView override as it's not strictly necessary unless cleaning up specific things not handled by OS, 
    // and helps avoid partial file matching issues if I messed up line counts.
    // Actually, good practice to keep it null out binding. 


    private void setupRatePrompt() {
        if (binding == null || binding.cardRatePrompt == null) return;

        android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE);
        boolean hasRated = prefs.getBoolean("has_rated_app", false);
        int launchCount = prefs.getInt("rate_app_launch_count", 0);
        long dismissedTime = prefs.getLong("rate_banner_dismissed_time", 0);
        
        // Reappears only after 3 days (3 * 24 * 60 * 60 * 1000L ms)
        long threeDaysMillis = 3L * 24L * 60L * 60L * 1000L;
        boolean dismissedWaitOver = (dismissedTime == 0) || ((System.currentTimeMillis() - dismissedTime) >= threeDaysMillis);

        boolean shouldShow = !hasRated && (launchCount >= 3) && dismissedWaitOver;

        if (shouldShow) {
            binding.cardRatePrompt.setVisibility(android.view.View.VISIBLE);

            binding.btnRateAction.setOnClickListener(v -> {
                prefs.edit().putBoolean("has_rated_app", true).apply();
                binding.cardRatePrompt.animate().alpha(0f).translationY(-20f).setDuration(250)
                        .withEndAction(() -> {
                            if (binding != null && binding.cardRatePrompt != null) {
                                binding.cardRatePrompt.setVisibility(android.view.View.GONE);
                            }
                        }).start();
                openPlayStoreRating();
            });

            binding.btnRateClose.setOnClickListener(v -> {
                // Save timestamp: reappears only after 3 days
                prefs.edit()
                        .putLong("rate_banner_dismissed_time", System.currentTimeMillis())
                        .apply();
                binding.cardRatePrompt.animate().alpha(0f).translationY(-20f).setDuration(250)
                        .withEndAction(() -> {
                            if (binding != null && binding.cardRatePrompt != null) {
                                binding.cardRatePrompt.setVisibility(android.view.View.GONE);
                            }
                        }).start();
            });
        } else {
            binding.cardRatePrompt.setVisibility(android.view.View.GONE);
        }
    }

    private void openPlayStoreRating() {
        String packageName = requireContext().getPackageName();
        try {
            startActivity(new android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("market://details?id=" + packageName)));
        } catch (android.content.ActivityNotFoundException e) {
            startActivity(new android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://play.google.com/store/apps/details?id=" + packageName)));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}