package com.drive.axleweightcalc;

import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;

import android.view.View;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.drive.axleweightcalc.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LanguageManager.wrapContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply saved locale before super.onCreate
        LanguageManager.applySavedLocale(this);
        ThemeManager.applyTheme(this);
        
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Check for first launch
        if (!LanguageManager.isLanguageSet(this)) {
            showLanguageDialog();
        }

        // Track launch count for in-app rate prompt
        android.content.SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        int launchCount = prefs.getInt("rate_app_launch_count", 0) + 1;
        prefs.edit().putInt("rate_app_launch_count", launchCount).apply();

        // Retrieve NavController safely for FragmentContainerView
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_content_main);

        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(binding.navView, navController);
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
        String current = LanguageManager.getCurrentLanguage(this);
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equalsIgnoreCase(current) || (codes[i].equals("fil") && current.equalsIgnoreCase("tl"))) {
                checkedItem = i;
                break;
            }
        }
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.dialog_language_title)
            .setSingleChoiceItems(languages, checkedItem, null) // Listener not needed, we get position later
            .setPositiveButton(R.string.btn_save, (dialog, which) -> {
                int selectedPosition = ((androidx.appcompat.app.AlertDialog)dialog).getListView().getCheckedItemPosition();
                if (selectedPosition >= 0 && selectedPosition < codes.length) {
                    LanguageManager.applyLocaleSmoothly(this, codes[selectedPosition]);
                }
            })
            .setCancelable(false)
            .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


}