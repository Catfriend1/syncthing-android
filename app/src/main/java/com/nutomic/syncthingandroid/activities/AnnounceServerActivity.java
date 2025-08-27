package com.nutomic.syncthingandroid.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import com.nutomic.syncthingandroid.R;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;

import java.util.ArrayList;
import java.util.List;

public class AnnounceServerActivity extends SyncthingActivity {

    public static final String EXTRA_ANNOUNCE_SERVERS = "announce_servers";
    public static final String EXTRA_RESULT_ANNOUNCE_SERVERS = "result_announce_servers";

    private LinearLayout mUrlContainer;
    private List<EditText> mUrlInputs = new ArrayList<>();
    private Button mAddUrlButton;
    private Button mSaveButton;
    private Button mCancelButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_announce_server);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mUrlContainer = findViewById(R.id.url_container);
        mAddUrlButton = findViewById(R.id.add_url_button);
        mSaveButton = findViewById(R.id.save_button);
        mCancelButton = findViewById(R.id.cancel_button);

        // Get the current announce servers from the intent
        String announceServers = getIntent().getStringExtra(EXTRA_ANNOUNCE_SERVERS);
        if (announceServers != null && !announceServers.isEmpty()) {
            Splitter splitter = Splitter.on(",").trimResults().omitEmptyStrings();
            for (String url : splitter.split(announceServers)) {
                addUrlInput(url);
            }
        }

        // If no URLs were added, add an empty one
        if (mUrlInputs.isEmpty()) {
            addUrlInput("");
        }

        mAddUrlButton.setOnClickListener(v -> addUrlInput(""));
        
        mSaveButton.setOnClickListener(v -> saveAndReturn());
        
        mCancelButton.setOnClickListener(v -> finish());
    }

    private void addUrlInput(String initialValue) {
        View urlItem = LayoutInflater.from(this).inflate(R.layout.item_url_input, mUrlContainer, false);
        EditText urlEditText = urlItem.findViewById(R.id.url_input);
        Button removeButton = urlItem.findViewById(R.id.remove_url_button);

        if (initialValue != null && !initialValue.isEmpty()) {
            urlEditText.setText(initialValue);
        }

        urlEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validateUrl(urlEditText);
            }
        });

        removeButton.setOnClickListener(v -> {
            if (mUrlInputs.size() > 1) {
                mUrlContainer.removeView(urlItem);
                mUrlInputs.remove(urlEditText);
            } else {
                Toast.makeText(this, R.string.at_least_one_url_required, Toast.LENGTH_SHORT).show();
            }
        });

        mUrlInputs.add(urlEditText);
        mUrlContainer.addView(urlItem);
    }

    private void validateUrl(EditText urlEditText) {
        String url = urlEditText.getText().toString().trim();
        if (!url.isEmpty() && !isValidUrl(url)) {
            urlEditText.setError(getString(R.string.invalid_url));
        } else {
            urlEditText.setError(null);
        }
    }

    private boolean isValidUrl(String url) {
        // Simple URL validation - can be enhanced if needed
        return url.startsWith("http://") || url.startsWith("https://") || 
               url.startsWith("udp://") || url.startsWith("tcp://") ||
               url.matches("^[a-zA-Z0-9.-]+:[0-9]+$");
    }

    private void saveAndReturn() {
        List<String> urls = new ArrayList<>();
        boolean hasErrors = false;

        for (EditText urlEditText : mUrlInputs) {
            String url = urlEditText.getText().toString().trim();
            if (!url.isEmpty()) {
                if (isValidUrl(url)) {
                    urls.add(url);
                } else {
                    urlEditText.setError(getString(R.string.invalid_url));
                    hasErrors = true;
                }
            }
        }

        if (hasErrors) {
            Toast.makeText(this, R.string.please_fix_invalid_urls, Toast.LENGTH_SHORT).show();
            return;
        }

        if (urls.isEmpty()) {
            Toast.makeText(this, R.string.at_least_one_url_required, Toast.LENGTH_SHORT).show();
            return;
        }

        // Return the result
        Intent resultIntent = new Intent();
        Joiner joiner = Joiner.on(",");
        resultIntent.putExtra(EXTRA_RESULT_ANNOUNCE_SERVERS, joiner.join(urls));
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}