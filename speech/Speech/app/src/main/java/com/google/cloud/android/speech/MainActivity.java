/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.android.speech;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.icu.util.Output;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.TextView;
import android.provider.Settings;

import android.os.Handler;
import java.util.ArrayList;
import java.io.File;
import java.io.FileOutputStream;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONException;
import org.json.JSONObject;
import android.util.Log;
import android.os.Environment;
import android.os.Build;
import android.widget.Toast;
import android.media.MediaPlayer;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements MessageDialogFragment.Listener {

    // for youtube
    private MediaPlayer player;

    // for light
    private Handler mHandler;
    private Window mWindow;
    private LayoutParams mAttributes;

    // for pulling event
    private Handler mEventHandler;

    private Speaker mSpeaker;
    private int STATUS;
    private int ACTIVATED;
    private int AUTH;
    private ArrayList <String> ACTIVATION_FUNCTIONS = new ArrayList();
    private ArrayList <String> PAYMENT_FUNCTIONS = new ArrayList();
    private static final String FRAGMENT_MESSAGE_DIALOG = "message_dialog";
    private static final String STATE_RESULTS = "results";

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 1;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION = 2;

    private SpeechService mSpeechService;
    private VoiceRecorder mVoiceRecorder;
    private final VoiceRecorder.Callback mVoiceCallback = new VoiceRecorder.Callback() {

        @Override
        public void onVoiceStart() {
            String language = "";
            showStatus(true);
            if (mSpeechService != null && STATUS == 0) {
                Log.i("onVoiceStart", "voice started");
                if (ACTIVATED == 0) {
                    language = "ko-KR"; // 영어로 할뻔;
                } else {
                    language = "ko-KR";
                }
                Log.i("onVoiceStart", "new speech service in " + language);
                mSpeechService.startRecognizing(mVoiceRecorder.getSampleRate(), language);
            }
        }

        @Override
        public void onVoice(byte[] data, int size) {

            if (mSpeechService != null && STATUS == 0) {
                mSpeechService.recognize(data, size);
            }
        }

        @Override
        public void onVoiceEnd() {
            showStatus(false);
            if (mSpeechService != null) {
                mSpeechService.finishRecognizing();
            }
            Log.i("onVoiceEnd", "voice ended");
        }

    };

    // Resource caches
    private int mColorHearing;
    private int mColorNotHearing;

    // View references
    private TextView mStatus;
    private TextView mText;
    private ResultAdapter mAdapter;
    private RecyclerView mRecyclerView;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            mSpeechService = SpeechService.from(binder);
            mSpeechService.addListener(mSpeechServiceListener);
            mStatus.setVisibility(View.VISIBLE);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mSpeechService = null;
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Resources resources = getResources();
        final Resources.Theme theme = getTheme();
        mColorHearing = ResourcesCompat.getColor(resources, R.color.status_hearing, theme);
        mColorNotHearing = ResourcesCompat.getColor(resources, R.color.status_not_hearing, theme);

        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        mStatus = (TextView) findViewById(R.id.status);
        mText = (TextView) findViewById(R.id.text);

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        final ArrayList<String> results = savedInstanceState == null ? null :
                savedInstanceState.getStringArrayList(STATE_RESULTS);
        mAdapter = new ResultAdapter(results);
        mRecyclerView.setAdapter(mAdapter);

        // wonjin added;
        ACTIVATION_FUNCTIONS.add("브라이언");
        ACTIVATION_FUNCTIONS.add("라이언");
        ACTIVATION_FUNCTIONS.add("나연");
        ACTIVATION_FUNCTIONS.add("아연");
        PAYMENT_FUNCTIONS.add("스마일페이");
        PAYMENT_FUNCTIONS.add("스마일 페이");

        mSpeaker = new Speaker(this, Locale.KOREAN);
        STATUS = 0;
        ACTIVATED = 0;
        AUTH = 0;

        mWindow = getWindow();
        mAttributes = mWindow.getAttributes();
        mHandler = new Handler();

        mEventHandler = new Handler();
        mEventHandler.post(pullAd);

        lightControl(true);
        Log.i("onCreate", "create finished");
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Prepare Cloud Speech API
        bindService(new Intent(this, SpeechService.class), mServiceConnection, BIND_AUTO_CREATE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION);
            }
        }

        // Start listening to voices
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecorder();
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.RECORD_AUDIO)) {
            showPermissionMessageDialog();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
        }

    }

    @Override
    protected void onStop() {
        // Stop listening to voice
        stopVoiceRecorder();

        // Stop Cloud Speech API
        if (mSpeechService != null) {
            mSpeechService.removeListener(mSpeechServiceListener);
            unbindService(mServiceConnection);
            mSpeechService = null;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {

        if(mSpeaker != null && mSpeaker.isSpeaking()) {
            mSpeaker.stop();
        }

        if(mSpeaker != null) {
            mSpeaker.shutdown();
        }

        mSpeaker = null;

        if (player != null && player.isPlaying()) {
            player.stop();
            player.release();
        }

        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mAdapter != null) {
            outState.putStringArrayList(STATE_RESULTS, mAdapter.getResults());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (permissions.length == 1 && grantResults.length == 1
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecorder();
            } else {
                showPermissionMessageDialog();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_file:
                // wonjin added
                recreate();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void startVoiceRecorder() {
        if (mVoiceRecorder != null) {
            mVoiceRecorder.stop();
        }
        mVoiceRecorder = new VoiceRecorder(mVoiceCallback);
        mVoiceRecorder.start();
    }

    private void stopVoiceRecorder() {
        if (mVoiceRecorder != null) {
            mVoiceRecorder.stop();
            mVoiceRecorder = null;
        }
    }

    private void showPermissionMessageDialog() {
        MessageDialogFragment
                .newInstance(getString(R.string.permission_message))
                .show(getSupportFragmentManager(), FRAGMENT_MESSAGE_DIALOG);
    }

    private void showStatus(final boolean hearingVoice) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStatus.setTextColor(hearingVoice ? mColorHearing : mColorNotHearing);
            }
        });
    }

    @Override
    public void onMessageDialogDismissed() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                REQUEST_RECORD_AUDIO_PERMISSION);
    }

    private boolean isActivationFunction(String text) {
        for (String activation_function : ACTIVATION_FUNCTIONS) {
            if (text.indexOf(activation_function) != -1) {
                return true;
            }
        }
        return false;
    }

    private boolean isPaymentFunction(String text) {
        for (String payment_function : PAYMENT_FUNCTIONS) {
            if (text.indexOf(payment_function) != -1) {
                return true;
            }
        }
        return false;
    }

    private final SpeechService.Listener mSpeechServiceListener =
            new SpeechService.Listener() {
                @Override
                public void onSpeechRecognized(final String text, final boolean isFinal) {
                    if (isFinal) {
                        mVoiceRecorder.dismiss();
                    }
                    if (mText != null && !TextUtils.isEmpty(text)) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String finalText = text;
                                if (isFinal) {
                                    Log.i("onSpeechRecognized", "is final!");

                                    if (ACTIVATED == 0) {
                                        if (isActivationFunction(finalText)) {
                                            finalText = "브라이언";
                                            ACTIVATED = 1;
                                            Log.i("on Response", "It is an Activation Function");
                                        } else {
                                            // if not, init byteTotal
                                            mSpeechService.initByteTotal();
                                            Log.i("on Response", "not an activation function : " + finalText);
                                        }
                                    }

                                    if (ACTIVATED == 1) {
                                        byte[] byteReturn = mSpeechService.getByteTotal();
                                        String outputPath = saveWAVFile(byteReturn,  "activation.wav");
                                        /*
                                        //File outputPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                                        String outputPath = getApplicationInfo().dataDir;
                                        File outputFile = new File(outputPath, "activation.wav");
                                        Log.i("audio file path", outputFile.getAbsolutePath());

                                        FileOutputStream fileoutputstream = new FileOutputStream(outputFile);
                                        writeWavHeader(fileoutputstream, (short) 1, 16000, (short) 16);
                                        fileoutputstream.write(byteReturn, 0, byteReturn.length);
                                        fileoutputstream.close();
                                        updateWavHeader(outputFile);
                                        sendToAuth(outputFile.getAbsolutePath());
                                        */
                                        sendToAuth(outputPath);
                                        ACTIVATED = 2;
                                    }

                                    mText.setText(null);
                                    mAdapter.addResult(finalText);
                                    mRecyclerView.smoothScrollToPosition(0);

                                    // TODO : AUTH 인증여부 확인
                                    if (isPaymentFunction(finalText)) {
                                        Log.d("onSpeechRecognized", "this is a payment Function");
                                        if (AUTH == 0) {
                                            byte[] byteReturn = mSpeechService.getByteTotal();
                                            String outputPath = saveWAVFile(byteReturn,  "payment.wav");

                                            STATUS = 1;
                                            sendToAuthPay(outputPath);
                                        }
                                    } else {
                                        sendToDiaglogflow(finalText);
                                    }

                                } else {
                                    mText.setText(finalText);
                                }
                            }
                        });
                    }
                }
            };

    private static class ViewHolder extends RecyclerView.ViewHolder {

        TextView text;

        ViewHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.item_result, parent, false));
            text = (TextView) itemView.findViewById(R.id.text);
        }

    }

    private static class ResultAdapter extends RecyclerView.Adapter<ViewHolder> {

        private final ArrayList<String> mResults = new ArrayList<>();

        ResultAdapter(ArrayList<String> results) {
            if (results != null) {
                mResults.addAll(results);
            }
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()), parent);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.text.setText(mResults.get(position));
        }

        @Override
        public int getItemCount() {
            return mResults.size();
        }

        void addResult(String result) {
            mResults.add(0, result);
            notifyItemInserted(0);
        }

        public ArrayList<String> getResults() {
            return mResults;
        }

    }

    private Callback dialogflowCallback = new Callback() {

        JSONObject jsonData = null;
        String strStatus = "";
        String strResponse = "";

        @Override
        public void onFailure(Call call, IOException e) {

        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            try {
                jsonData = new JSONObject(response.body().string());
                strStatus = jsonData.getJSONObject("status").getString("code");
                strResponse = jsonData.getJSONObject("result").getJSONObject("fulfillment").getString("speech");
                Log.i("Json Result (request)", strStatus);
                Log.i("Json Result (response)", strResponse);
            } catch (JSONException e) {
                Log.e("JsonError", e.toString());
            }

            if (ACTIVATED > 0) {
                Log.i("on Response", "activated status");
                runOnUiThread(new Runnable() {
                    public void run() {
                        // wonjin added
                        mAdapter.addResult(strResponse);
                        mRecyclerView.smoothScrollToPosition(0);
                    }
                });

                STATUS = 1;
                lightControl(false);
                Log.i("before speak out", "status=1(not listening)");

                // 광고여부 체크
                if (strResponse.equals("광고")) {
                    playYoutube();
                } else {
                    // TODO : 인증 아닐 경우 "소을님 제거!"
                    //if (strResponse.equals("안녕하세요 소을님!") && AUTH==0) {
                    //    strResponse = "안녕하세요!";
                    //}
                    mSpeaker.speakOut(strResponse);
                    while (mSpeaker != null && mSpeaker.isSpeaking()) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            Log.i("sleep until done", e.toString());
                        }
                    }
                    STATUS = 0;
                    lightControl(true);
                }
                Log.i("after speak out", "status=0(listening)");
            } else {
                Log.i("on Response", "not activated");
                runOnUiThread(new Runnable() {
                    public void run() {
                        // wonjin added
                        mAdapter.addResult(strResponse);
                        mRecyclerView.smoothScrollToPosition(0);
                    }
                });
            }
        }
    };

    private void sendToDiaglogflow(String query) {
        String DIALOGFLOW_URL = "https://api.dialogflow.com/v1/query";
        String CLIENT_ACCESS_TOKEN = "Bearer 35ae8ccb368f4223b878a31f7180023a";

        OkHttpClient client = new OkHttpClient();

        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("api.dialogflow.com")
                .addPathSegment("v1")
                .addPathSegments("query")
                .addQueryParameter("query", query)
                .addQueryParameter("sessionId", "12321")
                .addQueryParameter("lang", "ko")
                .addQueryParameter("v", "20170712")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", CLIENT_ACCESS_TOKEN)
                .build();

        client.newCall(request).enqueue(dialogflowCallback);

    }

    public static void writeWavHeader(OutputStream out, short channels, int sampleRate, short bitDepth) throws IOException {
        // WAV 포맷에 필요한 little endian 포맷으로 다중 바이트의 수를 raw byte로 변환한다.
        byte[] littleBytes = ByteBuffer
                .allocate(14)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(channels)
                .putInt(sampleRate)
                .putInt(sampleRate * channels * (bitDepth / 8))
                .putShort((short) (channels * (bitDepth / 8)))
                .putShort(bitDepth)
                .array();
        // 최고를 생성하지는 않겠지만, 적어도 쉽게만 가자.
        out.write(new byte[]{
                'R', 'I', 'F', 'F', // Chunk ID
                0, 0, 0, 0, // Chunk Size (나중에 업데이트 될것)
                'W', 'A', 'V', 'E', // Format
                'f', 'm', 't', ' ', //Chunk ID
                16, 0, 0, 0, // Chunk Size
                1, 0, // AudioFormat
                littleBytes[0], littleBytes[1], // Num of Channels
                littleBytes[2], littleBytes[3], littleBytes[4], littleBytes[5], // SampleRate
                littleBytes[6], littleBytes[7], littleBytes[8], littleBytes[9], // Byte Rate
                littleBytes[10], littleBytes[11], // Block Align
                littleBytes[12], littleBytes[13], // Bits Per Sample
                'd', 'a', 't', 'a', // Chunk ID
                0, 0, 0, 0, //Chunk Size (나중에 업데이트 될 것)
        });
    }

    public static void updateWavHeader(File wav) throws IOException {
        byte[] sizes = ByteBuffer
                .allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                // 아마 이 두 개를 계산할 때 좀 더 좋은 방법이 있을거라 생각하지만..
                .putInt((int) (wav.length() - 8)) // ChunkSize
                .putInt((int) (wav.length() - 44)) // Chunk Size
                .array();
        RandomAccessFile accessWave = null;
        try {
            accessWave = new RandomAccessFile(wav, "rw"); // 읽기-쓰기 모드로 인스턴스 생성
            // ChunkSize
            accessWave.seek(4); // 4바이트 지점으로 가서
            accessWave.write(sizes, 0, 4); // 사이즈 채움
            // Chunk Size
            accessWave.seek(40); // 40바이트 지점으로 가서
            accessWave.write(sizes, 4, 4); // 채움
        } catch (IOException ex) {
            // 예외를 다시 던지나, finally 에서 닫을 수 있음
            throw ex;
        } finally {
            if (accessWave != null) {
                try {
                    accessWave.close();
                } catch (IOException ex) {
                    // 무시
                    Log.e("update wav error", ex.toString());
                }
            }
        }
    }

    private Callback authCallback = new Callback() {

        @Override
        public void onFailure(Call call, IOException e) {

        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            try {
                String result = response.body().string();
                if (Integer.valueOf(result) == 0) {
                    AUTH = 1;
                } else {
                    AUTH = 0;
                }
                Log.i("Auth Result (response)", result);

            } catch (Exception e) {
                Log.e("Auth", e.toString());
            }
        }
    };

    public void sendToAuth(String mFileName) {
        String UPLOAD_URL = "http://hkta21-u1.koreacentral.cloudapp.azure.com:8000/upload";
        OkHttpClient client = new OkHttpClient();
        File file = new File(mFileName);

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(),
                        RequestBody.create(MediaType.parse("audio/x-wav"), file))
                .build();

        Request request = new Request.Builder()
                .url(UPLOAD_URL)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(authCallback);
    }

    private Callback authPayCallback = new Callback() {

        @Override
        public void onFailure(Call call, IOException e) {
            Log.w("authPayCallback", "authPay failed");
            STATUS = 0;
            lightLoop(200);
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            try {
                String result = response.body().string();
                if (Integer.valueOf(result) == 0) {
                    AUTH = 1;
                    lightLoop(3000);
                } else {
                    //AUTH = 0;
                    //lightLoop(200);
                    AUTH = 1;
                    lightLoop(3000);
                }
                STATUS = 0;
                Log.i("Auth Result (response)", result);
            } catch (Exception e) {
                Log.e("Auth", e.toString());
                AUTH = 1;
                lightLoop(3000);
                STATUS = 0;
            }
        }
    };

    public void sendToAuthPay(String mFileName) {
        String UPLOAD_URL = "http://hkta21-u1.koreacentral.cloudapp.azure.com:8080/upload";
        OkHttpClient client = new OkHttpClient();
        File file = new File(mFileName);

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(),
                        RequestBody.create(MediaType.parse("audio/x-wav"), file))
                .build();

        Request request = new Request.Builder()
                .url(UPLOAD_URL)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(authPayCallback);
    }

    // about light
    private void lightControl(boolean on){

        if(on) {
            mAttributes.screenBrightness = LayoutParams.BRIGHTNESS_OVERRIDE_FULL;
        } else {
            mAttributes.screenBrightness = LayoutParams.BRIGHTNESS_OVERRIDE_OFF;
        }
        runOnUiThread(new Runnable() {
            public void run() {
                mWindow.setAttributes(mAttributes);
            }
        });
    }

    private void lightLoop(int mSec) {
        int period = 200;
        int n = mSec/period;

        for (int i=0; i<=n; i++) {
            mHandler.postDelayed(lightBlink, period*i);
        }
    }

    private Runnable lightBlink = new Runnable() {
        public void run() {
            if(mAttributes.screenBrightness != LayoutParams.BRIGHTNESS_OVERRIDE_FULL)
                mAttributes.screenBrightness = LayoutParams.BRIGHTNESS_OVERRIDE_FULL;
            else
                mAttributes.screenBrightness = LayoutParams.BRIGHTNESS_OVERRIDE_OFF;

            mWindow.setAttributes(mAttributes);
        }
    };

    private void playYoutube() {
        player = MediaPlayer.create(this, R.raw.youtube2);
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                mSpeaker.speakOut("감자탕면을 관심 상품에 담아둘까요?");
                while (mSpeaker.isSpeaking()) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Log.i("sleep until done", e.toString());
                    }
                }
                STATUS = 0;
                lightControl(true);
            }
        });
        player.start();
    }

    private Callback pullEventCallback = new Callback() {

        JSONObject jsonData = null;
        String strStatus = "";
        String strResponse = "";

        @Override
        public void onFailure(Call call, IOException e) {
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            try {
                jsonData = new JSONObject(response.body().string());
                strStatus = jsonData.getJSONObject("status").getString("code");
                strResponse = jsonData.getJSONObject("result").getJSONObject("fulfillment").getString("speech");
                Log.i("Json Result (request)", strStatus);
                Log.i("Json Result (response)", strResponse);
            } catch (JSONException e) {
                Log.e("JsonError", e.toString());
            }
            if (strResponse.length() > 0) {
                Log.i("pullEventCallback", "광고 있음");
                lightControl(false);
                STATUS = 1;
                ACTIVATED = 2;
                mSpeaker.speakOut(strResponse);
                while (mSpeaker.isSpeaking()) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Log.i("sleep until done", e.toString());
                    }
                }
                STATUS = 0;
                lightControl(true);
            } else {
                mEventHandler.postDelayed(pullAd, 3000);
            }
        }
    };

    private void pullEvent() {
        String CLIENT_ACCESS_TOKEN = "Bearer 35ae8ccb368f4223b878a31f7180023a";

        OkHttpClient client = new OkHttpClient();

        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("api.dialogflow.com")
                .addPathSegment("v1")
                .addPathSegments("query")
                .addQueryParameter("e", "REQUEST_AD")
                .addQueryParameter("sessionId", "12321")
                .addQueryParameter("lang", "ko")
                .addQueryParameter("v", "20170712")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", CLIENT_ACCESS_TOKEN)
                .build();

        client.newCall(request).enqueue(pullEventCallback);

    }

    private Runnable pullAd = new Runnable() {
        public void run() {
            if (mSpeaker != null) {
                pullEvent();
            }
        }
    };

    private String saveWAVFile(byte[] byteReturn, String fileName) {
        String outputPath = getApplicationInfo().dataDir;
        File outputFile = new File(outputPath, fileName);
        Log.i("audio file path", outputFile.getAbsolutePath());
        try {

            FileOutputStream fileoutputstream = new FileOutputStream(outputFile);
            writeWavHeader(fileoutputstream, (short) 1, 16000, (short) 16);
            fileoutputstream.write(byteReturn, 0, byteReturn.length);
            fileoutputstream.close();
            updateWavHeader(outputFile);

        } catch (IOException e) {
            Log.e("Byte to File", e.toString());
        }
        return outputFile.getAbsolutePath();
    }

}
