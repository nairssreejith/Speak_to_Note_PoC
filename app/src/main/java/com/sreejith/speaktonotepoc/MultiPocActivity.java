package com.sreejith.speaktonotepoc;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

public class MultiPocActivity extends AppCompatActivity
        implements TextToSpeech.OnInitListener {


    private static final int   REQUEST_RECORD_AUDIO_PERMISSION = 1;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private boolean isListening = false;
    private View currentMicView;
    private EditText[]         editTexts;
    private ImageButton[]      micButtons;
    private FloatingActionButton fabMic;

    // New fields
    private TextToSpeech tts;
    private ConstraintLayout ttsContainer, translateContainer;
    private Button btnSpeak, btnTranslate;
    private TextInputEditText etTranslateInput;
    private RadioGroup rgLanguages;
    private TextView tvTranslated;
    private Translator translator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_poc);
        requestAudioPermission();
        bindViews();
        setupSpeechRecognizer();
        setupKeyboardAwareFab();

        // Initialize TTS
        tts = new TextToSpeech(this, this);

        // Initialize ML Kit translator (no-target yet)
        // Actual model will be configured on translate button click
        ttsContainer     = findViewById(R.id.ttsContainer);
        translateContainer = findViewById(R.id.translateContainer);
        btnTranslate     = findViewById(R.id.btnTranslate);
        etTranslateInput = findViewById(R.id.etTranslateInput);
        rgLanguages      = findViewById(R.id.rgLanguages);
        tvTranslated     = findViewById(R.id.tvTranslated);



        // Speak current focused text
       /* btnSpeak.setOnClickListener(v -> {
            View focused = getCurrentFocus();
            if (focused instanceof EditText) {
                String text = ((EditText) focused).getText().toString();
                tts.setLanguage(determineLocale()); // picks EN/FR/ES based on system or prefs
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "UTT_ID");
            }
        });*/

        // Translate on demand
        btnTranslate.setOnClickListener(v -> {
            String input = Objects.requireNonNull(etTranslateInput.getText()).toString().trim();
            if (input.isEmpty()) {
                tvTranslated.setText("Please enter text to translate");
                return;
            }

            // Show translating placeholder immediately
            tvTranslated.setText("Translating...");

            // Determine target language
            String  targetLangCode;
            Locale targetLocale = getSelectedLocale();
            if (targetLocale.equals(Locale.FRENCH)) {
                targetLangCode = TranslateLanguage.FRENCH;
            } else if (targetLocale.equals(new Locale("es", "ES"))) {
                targetLangCode = TranslateLanguage.SPANISH;
            } else {
                targetLangCode = TranslateLanguage.ENGLISH;
            }

            // Configure the translator
            TranslatorOptions options = new TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH)
                    .setTargetLanguage(targetLangCode)
                    .build();
            Translator translator = Translation.getClient(options);

            // Ensure model is downloaded, then translate
            translator.downloadModelIfNeeded()
                    .addOnSuccessListener(aVoid ->
                            translator.translate(input)
                                    .addOnSuccessListener(
                                            translatedText -> tvTranslated.setText(translatedText)
                                    )
                                    .addOnFailureListener(e ->
                                            tvTranslated.setText("Error: " + e.getMessage())
                                    )
                    )
                    .addOnFailureListener(e ->
                            tvTranslated.setText("Model download failed: " + e.getMessage())
                    );
        });
    }

    @Override
    public void onInit(int status) {
        // TTS engine initialized
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.getDefault());
        }
    }

    private void requestAudioPermission() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{ Manifest.permission.RECORD_AUDIO },
                    REQUEST_RECORD_AUDIO_PERMISSION);
        }
    }

    private void bindViews() {
        editTexts = new EditText[]{
                findViewById(R.id.etInput1),
                findViewById(R.id.etInput2),
                findViewById(R.id.etInput3)
        };

        micButtons = new ImageButton[]{
                findViewById(R.id.btnMic1),
                findViewById(R.id.btnMic2),
                findViewById(R.id.btnMic3)
        };

        fabMic        = findViewById(R.id.fabMic);
        ToggleButton toggleMicMode = findViewById(R.id.toggleMicMode);

        // Individual mic buttons: tap sets focus and toggles listening
        for (int i = 0; i < micButtons.length; i++) {
            final int idx = i;
            micButtons[i].setOnClickListener(v -> {
                // Force-focus the corresponding EditText
                editTexts[idx].requestFocus();
                currentMicView = v;
                toggleListening();
            });
        }

        // Floating mic: uses whatever View currently has focus
        fabMic.setOnClickListener(v -> {
            View focused = getCurrentFocus();
            if (!(focused instanceof EditText)) {
                Toast.makeText(this,
                        "Please tap into a text field first",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            currentMicView = v;
            toggleListening();
        });

        // Toggle mode switch
        toggleMicMode.setOnCheckedChangeListener((btn, isOn) -> {
            fabMic.setVisibility(isOn ? View.VISIBLE : View.GONE);
            for (ImageButton mic : micButtons) {
                mic.setVisibility(isOn ? View.GONE : View.VISIBLE);
            }
        });
    }

    private void setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault());

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}

            @Override
            public void onError(int error) {
                isListening = false;
                switch (error) {
                    case SpeechRecognizer.ERROR_NO_MATCH:
                        Toast.makeText(MultiPocActivity.this,
                                "Didn't catch that. Please speak clearly.",
                                Toast.LENGTH_SHORT).show();
                        break;
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                        Toast.makeText(MultiPocActivity.this,
                                "Recording permission missing.",
                                Toast.LENGTH_SHORT).show();
                        break;
                    case SpeechRecognizer.ERROR_NETWORK:
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                        Toast.makeText(MultiPocActivity.this,
                                "Network error. Check your connection.",
                                Toast.LENGTH_SHORT).show();
                        break;
                    case SpeechRecognizer.ERROR_AUDIO:
                    case SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT:
                    case SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS:
                    case SpeechRecognizer.ERROR_CLIENT:
                    case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:
                    case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    case SpeechRecognizer.ERROR_SERVER:
                    case SpeechRecognizer.ERROR_SERVER_DISCONNECTED:
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    case SpeechRecognizer.ERROR_TOO_MANY_REQUESTS:
                        break;
                }
                if (currentMicView instanceof ImageButton) {
                    ((ImageButton) currentMicView)
                            .setImageResource(android.R.drawable.ic_btn_speak_now);
                }
            }

            @Override
            public void onResults(Bundle results) {
                // Append final text, move cursor, then reset icon
                ArrayList<String> matches =
                        results.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION);

                View focused = getCurrentFocus();
                if (matches != null && !matches.isEmpty()
                        && focused instanceof EditText) {

                    EditText et = (EditText) focused;
                    String before = et.getText().toString();
                    String appended = before +
                            (before.isEmpty() ? "" : " ") +
                            matches.get(0);
                    et.setText(appended);
                    et.setSelection(appended.length());
                }
                stopListening();
            }

            @Override public void onPartialResults(Bundle partial) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    /** Toggles listening ON/OFF and updates only the last‑tapped mic’s icon */
    private void toggleListening() {
        if (isListening) {
            stopListening();
        } else {
            startListening();
        }
    }

    private void startListening() {
        isListening = true;
        // Show “active” icon only on the mic the user tapped:
        if (currentMicView instanceof ImageButton) {
            ((ImageButton) currentMicView)
                    .setImageResource(android.R.drawable.presence_audio_online);
        }
        speechRecognizer.startListening(recognizerIntent);
    }

    private void stopListening() {
        // reset flag & stop the recognizer
        isListening = false;
        speechRecognizer.stopListening();

        // reset only the last‑tapped mic’s icon:
        if (currentMicView instanceof ImageButton) {
            ((ImageButton) currentMicView)
                    .setImageResource(android.R.drawable.ic_btn_speak_now);
        }
    }

    private void setupKeyboardAwareFab() {
        final View root = findViewById(R.id.rootLayout);
        root.getViewTreeObserver()
                .addOnGlobalLayoutListener(() -> {
                    Rect r = new Rect();
                    root.getWindowVisibleDisplayFrame(r);
                    int screenHeight = root.getRootView().getHeight();
                    int keypadHeight = screenHeight - r.bottom;

                    if (keypadHeight > screenHeight * 0.15) {
                        fabMic.setTranslationY(-keypadHeight - dpToPx());
                    } else {
                        fabMic.setTranslationY(0);
                    }
                });
    }

    private int dpToPx() {
        return Math.round(16 * getResources()
                .getDisplayMetrics().density);
    }

    @Override
    public void onRequestPermissionsResult(int req,
                                           @NonNull String[] perms,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQUEST_RECORD_AUDIO_PERMISSION
                && (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(this,
                    "Audio permission is required",
                    Toast.LENGTH_LONG).show();
        }
    }

    private Locale getSelectedLocale() {
        int id = rgLanguages.getCheckedRadioButtonId();
        if (id == R.id.rbFrench)  return Locale.FRENCH;
        if (id == R.id.rbSpanish) return new Locale("es", "ES");
        return Locale.ENGLISH;
    }

    private Locale determineLocale() {
        return Locale.getDefault();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        speechRecognizer.destroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }


}