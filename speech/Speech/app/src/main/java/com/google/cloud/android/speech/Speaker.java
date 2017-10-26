package com.google.cloud.android.speech;

/**
 * Created by wonjin on 2017. 10. 21..
 */

import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.content.Context;

import java.util.Locale;

public class Speaker extends UtteranceProgressListener implements OnInitListener {

    private final String TAG = Speaker.class.getSimpleName();
    private Locale locale;

    private TextToSpeech myTTS;

    public Speaker(Context context, Locale locale) {
        this.locale = locale;
        myTTS = new TextToSpeech(context, this);
        myTTS.setOnUtteranceProgressListener(this);
    }

    @Override
    public void onStart(String utteranceId) {
        Log.d(TAG, "onStart / utteranceID = " + utteranceId);
    }

    @Override
    public void onDone(String utteranceId) {
        Log.d(TAG, "onDone / utteranceID = " + utteranceId);
    }

    @Override
    public void onError(String utteranceId) {
        Log.d(TAG, "onError / utteranceID = " + utteranceId);
    }

    @Override
    public void onInit(int status) {
        if(status != TextToSpeech.ERROR)
            myTTS.setLanguage(locale);
    }

    public void stop() {
        myTTS.stop();
    }

    public void shutdown() {
        myTTS.shutdown();
    }

    public boolean isSpeaking() {
        return myTTS.isSpeaking();
    }

    public void speakOut(String myText) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            String utteranceId=this.hashCode() + "";
            myTTS.speak(myText, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        } else {
            myTTS.speak(myText, TextToSpeech.QUEUE_FLUSH, null);
        }
    }
}
