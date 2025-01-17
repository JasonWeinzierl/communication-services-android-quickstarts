package com.example.videocallingquickstart;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.RadioButton;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.content.Context;

import com.azure.android.communication.calling.CallAgentOptions;
import com.azure.android.communication.calling.CallState;
import com.azure.android.communication.calling.CallingCommunicationException;
import com.azure.android.communication.calling.GroupCallLocator;
import com.azure.android.communication.calling.IncomingAudioOptions;
import com.azure.android.communication.calling.IncomingVideoOptions;
import com.azure.android.communication.calling.JoinCallOptions;
import com.azure.android.communication.calling.OutgoingAudioOptions;
import com.azure.android.communication.calling.OutgoingVideoOptions;
import com.azure.android.communication.calling.ParticipantsUpdatedListener;
import com.azure.android.communication.calling.PropertyChangedEvent;
import com.azure.android.communication.calling.PropertyChangedListener;
import com.azure.android.communication.calling.StartCallOptions;
import com.azure.android.communication.calling.StartTeamsCallOptions;
import com.azure.android.communication.calling.TeamsCallAgentOptions;
import com.azure.android.communication.calling.VideoDeviceInfo;
import com.azure.android.communication.calling.VideoStreamType;
import com.azure.android.communication.common.CommunicationCloudEnvironment;
import com.azure.android.communication.common.CommunicationIdentifier;
import com.azure.android.communication.common.CommunicationTokenCredential;
import com.azure.android.communication.calling.CallAgent;
import com.azure.android.communication.calling.TeamsCallAgent;
import com.azure.android.communication.calling.CallClient;
import com.azure.android.communication.calling.DeviceManager;
import com.azure.android.communication.calling.VideoOptions;
import com.azure.android.communication.calling.LocalVideoStream;
import com.azure.android.communication.calling.VideoStreamRenderer;
import com.azure.android.communication.calling.VideoStreamRendererView;
import com.azure.android.communication.calling.CreateViewOptions;
import com.azure.android.communication.calling.ScalingMode;
import com.azure.android.communication.calling.IncomingCall;
import com.azure.android.communication.calling.TeamsIncomingCall;
import com.azure.android.communication.calling.Call;
import com.azure.android.communication.calling.TeamsCall;
import com.azure.android.communication.calling.AcceptCallOptions;
import com.azure.android.communication.calling.ParticipantsUpdatedEvent;
import com.azure.android.communication.calling.RemoteParticipant;
import com.azure.android.communication.calling.RemoteVideoStream;
import com.azure.android.communication.calling.RemoteVideoStreamsEvent;
import com.azure.android.communication.calling.RendererListener;
import com.azure.android.communication.common.CommunicationUserIdentifier;
import com.azure.android.communication.common.MicrosoftTeamsUserIdentifier;
import com.azure.android.communication.common.PhoneNumberIdentifier;
import com.azure.android.communication.common.UnknownIdentifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "MainActivity";

    private CallClient callClient = new CallClient();
    private CallAgent callAgent;
    private TeamsCallAgent teamsCallAgent;
    private VideoDeviceInfo currentCamera;
    private LocalVideoStream currentVideoStream;
    private DeviceManager deviceManager;
    private IncomingCall incomingCall;
    private TeamsIncomingCall teamsIncomingCall;
    private Call call;
    private TeamsCall teamsCall;
    VideoStreamRenderer previewRenderer;
    VideoStreamRendererView preview;
    final Map<Integer, StreamData> streamData = new HashMap<>();
    private boolean renderRemoteVideo = true;
    private ParticipantsUpdatedListener remoteParticipantUpdatedListener;
    private PropertyChangedListener onStateChangedListener;

    final HashSet<String> joinedParticipants = new HashSet<>();

    Button switchSourceButton;
    RadioButton acsCall, cteCall, oneToOneCall, groupCall;
    private boolean isCte = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button restartSdkButton = findViewById(R.id.restart_sdk);
        restartSdkButton.setOnClickListener(l -> restartSdk());

        getAllPermissions();
        startSdk();

        switchSourceButton = findViewById(R.id.switch_source);
        switchSourceButton.setOnClickListener(l -> switchSource());

        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        acsCall = findViewById(R.id.acs_call);
        acsCall.setOnClickListener(this::onCallTypeSelected);
        acsCall.setChecked(true);
        cteCall = findViewById(R.id.cte_call);
        cteCall.setOnClickListener(this::onCallTypeSelected);

        Button hangupButton = findViewById(R.id.hang_up);
        hangupButton.setOnClickListener(l -> hangUp());
        Button startVideo = findViewById(R.id.show_preview);
        startVideo.setOnClickListener(l -> turnOnLocalVideo());
        Button stopVideo = findViewById(R.id.hide_preview);
        stopVideo.setOnClickListener(l -> turnOffLocalVideo());

        oneToOneCall = findViewById(R.id.one_to_one_call);
        oneToOneCall.setOnClickListener(this::onCallTypeSelected);
        oneToOneCall.setChecked(true);
        groupCall = findViewById(R.id.group_call);
        groupCall.setOnClickListener(this::onCallTypeSelected);

    }

    private void restartSdk() {
        Log.d(TAG, "Restarting ACS SDK");
        if (callAgent != null) {
            Log.d(TAG, "Disposing CallAgent");
            callAgent.dispose();
            callAgent = null;
        }
        if (teamsCallAgent != null) {
            Log.d(TAG, "Disposing TeamsCallAgent");
            teamsCallAgent.dispose();
            teamsCallAgent = null;
        }
        if (preview != null) {
            preview.dispose();
            preview = null;
        }
        if (previewRenderer != null) {
            previewRenderer.dispose();
            previewRenderer = null;
        }
        currentVideoStream = null;
        currentCamera = null;
        deviceManager = null;
        Log.d(TAG, "Disposing CallClient");
        callClient.dispose();
        Log.d(TAG, "Creating new CallClient");
        callClient = new CallClient();

        startSdk();
    }

    private void startSdk() {
        Log.d(TAG, "Starting ACS SDK");
        setupAgent();
        setDeviceManager();
        Log.d(TAG, "Finished starting ACS SDK");
    }

    private void getAllPermissions() {
        String[] requiredPermissions = new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_PHONE_STATE};
        ArrayList<String> permissionsToAskFor = new ArrayList<>();
        for (String permission : requiredPermissions) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToAskFor.add(permission);
            }
        }
        if (!permissionsToAskFor.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToAskFor.toArray(new String[0]), 1);
        }
    }

    private void setDeviceManager(){
        Context context = this.getApplicationContext();
        try {
            Log.d(TAG, "Getting DeviceManager");
            deviceManager = callClient.getDeviceManager(context).get();
            currentCamera = getNextAvailableCamera(null);
            currentVideoStream = new LocalVideoStream(currentCamera, this);
        }catch (Exception ex){
            Toast.makeText(context, "Failed to set device manager.", Toast.LENGTH_SHORT).show();
        }
    }

    private void createAgent() {
        Context context = this.getApplicationContext();
        String userToken = "<USER_ACCESS_TOKEN>";
        try {
            CommunicationTokenCredential credential = new CommunicationTokenCredential(userToken);
            CallAgentOptions callAgentOptions = new CallAgentOptions();
            Log.d(TAG, "Creating CallAgent");
            callAgent = callClient.createCallAgent(getApplicationContext(), credential, callAgentOptions).get();
        } catch (Exception ex) {
            Toast.makeText(context, "Failed to create call agent.", Toast.LENGTH_SHORT).show();
        }
    }

    private void createTeamsAgent() {
        Context context = this.getApplicationContext();
        String userToken = "<USER_ACCESS_TOKEN>";
        try {
            CommunicationTokenCredential credential = new CommunicationTokenCredential(userToken);
            TeamsCallAgentOptions teamsCallAgentOptions = new TeamsCallAgentOptions();
            Log.d(TAG, "Creating TeamsCallAgent");
            teamsCallAgent = callClient.createTeamsCallAgent(getApplicationContext(), credential, teamsCallAgentOptions).get();
        } catch (Exception ex) {
            Toast.makeText(context, "Failed to create teams call agent.", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupAgent(){
        if(isCte){
            if (callAgent != null) {
                callAgent.dispose();
                callAgent = null;
            }
            createTeamsAgent();
            handleTeamsIncomingCall();
        }else{
            if (teamsCallAgent != null) {
                teamsCallAgent.dispose();
                teamsCallAgent = null;
            }
            createAgent();
            handleIncomingCall();
        }
        setupButtonListener();
    }

    private void handleIncomingCall() {
        callAgent.addOnIncomingCallListener((incomingCall) -> {
            this.incomingCall = incomingCall;
            Executors.newCachedThreadPool().submit(this::answerIncomingCall);
        });
        callAgent.addOnCallsUpdatedListener((x) -> {
            if (x.getRemovedCalls().size() > 0) {
                call = null;
                teamsCall = null;
            }
        });
    }

    private void handleTeamsIncomingCall() {
        teamsCallAgent.addOnIncomingCallListener((incomingCall) -> {
            this.teamsIncomingCall = incomingCall;
            Executors.newCachedThreadPool().submit(this::answerTeamsIncomingCall);
        });
    }

    private void startCall() {
        Context context = this.getApplicationContext();
        EditText callIdView = findViewById(R.id.call_id);
        String callId = callIdView.getText().toString();
        ArrayList<CommunicationIdentifier> participants = new ArrayList<CommunicationIdentifier>();


        if(oneToOneCall.isChecked()){
            StartCallOptions options = new StartCallOptions();
            IncomingVideoOptions incomingVideoOptions = new IncomingVideoOptions();
            OutgoingVideoOptions outgoingVideoOptions = new OutgoingVideoOptions();
            OutgoingAudioOptions outgoingAudioOptions = new OutgoingAudioOptions();
            if(currentVideoStream != null) {
                LocalVideoStream[] videoStreams = new LocalVideoStream[1];
                videoStreams[0] = currentVideoStream;
                incomingVideoOptions.setStreamType(VideoStreamType.REMOTE_INCOMING);
                outgoingVideoOptions.setOutgoingVideoStreams(Arrays.asList(videoStreams[0]));
                outgoingAudioOptions.setMuted(false);
                showPreview();
            }
            participants.add(new CommunicationUserIdentifier(callId));

            options.setIncomingVideoOptions(incomingVideoOptions);
            options.setOutgoingVideoOptions(outgoingVideoOptions);
            options.setOutgoingAudioOptions(outgoingAudioOptions);

            call = callAgent.startCall(
                    context,
                    participants,
                    options);
        }
        else{

            JoinCallOptions options = new JoinCallOptions();
            if(currentVideoStream != null) {
                LocalVideoStream[] videoStreams = new LocalVideoStream[1];
                videoStreams[0] = currentVideoStream;
                VideoOptions videoOptions = new VideoOptions(videoStreams);
                options.setVideoOptions(videoOptions);
                showPreview();
            }
            GroupCallLocator groupCallLocator = new GroupCallLocator(UUID.fromString(callId));

            call = callAgent.join(
                    context,
                    groupCallLocator,
                    options);
        }
        remoteParticipantUpdatedListener = this::handleRemoteParticipantsUpdate;
        onStateChangedListener = this::handleCallOnStateChanged;
        call.addOnRemoteParticipantsUpdatedListener(remoteParticipantUpdatedListener);
        call.addOnStateChangedListener(onStateChangedListener);
    }

    private void startTeamsCall() {
        Context context = this.getApplicationContext();
        EditText callIdView = findViewById(R.id.call_id);
        String callId = callIdView.getText().toString();

        MicrosoftTeamsUserIdentifier participant;
        if (callId.startsWith("8:orgid:")){
            participant = new MicrosoftTeamsUserIdentifier(callId.substring("8:orgid:".length())).setCloudEnvironment(CommunicationCloudEnvironment.PUBLIC);
        } else if (callId.startsWith("8:dod:")) {
            participant = new MicrosoftTeamsUserIdentifier(callId.substring("8:dod:".length())).setCloudEnvironment(CommunicationCloudEnvironment.DOD);
        } else if (callId.startsWith("8:gcch:")) {
            participant = new MicrosoftTeamsUserIdentifier(callId.substring("8:gcch:".length())).setCloudEnvironment(CommunicationCloudEnvironment.GCCH);
        } else {
            participant = new MicrosoftTeamsUserIdentifier(callId).setCloudEnvironment(CommunicationCloudEnvironment.PUBLIC);
        }

        if(oneToOneCall.isChecked()){
            StartTeamsCallOptions options = new StartTeamsCallOptions();
            IncomingVideoOptions incomingVideoOptions = new IncomingVideoOptions();
            OutgoingVideoOptions outgoingVideoOptions = new OutgoingVideoOptions();
            OutgoingAudioOptions outgoingAudioOptions = new OutgoingAudioOptions();
            if(currentVideoStream != null) {
                LocalVideoStream[] videoStreams = new LocalVideoStream[1];
                videoStreams[0] = currentVideoStream;
                incomingVideoOptions.setStreamType(VideoStreamType.REMOTE_INCOMING);
                outgoingVideoOptions.setOutgoingVideoStreams(Arrays.asList(videoStreams[0]));
                outgoingAudioOptions.setMuted(false);
                showPreview();
            }

            options.setIncomingVideoOptions(incomingVideoOptions);
            options.setOutgoingVideoOptions(outgoingVideoOptions);
            options.setOutgoingAudioOptions(outgoingAudioOptions);

            teamsCall = teamsCallAgent.startCall(
                    context,
                    participant,
                    options);
        }
        else{
            Toast.makeText(context, "Teams user cannot join a group call", Toast.LENGTH_SHORT).show();
        }

        remoteParticipantUpdatedListener = this::handleRemoteParticipantsUpdate;
        onStateChangedListener = this::handleTeamsCallOnStateChanged;
        teamsCall.addOnRemoteParticipantsUpdatedListener(remoteParticipantUpdatedListener);
        teamsCall.addOnStateChangedListener(onStateChangedListener);
    }

    private void hangUp() {
        renderRemoteVideo = false;
        try {
            if (isCte){
                for(RemoteParticipant participant : teamsCall.getRemoteParticipants()){
                    for (RemoteVideoStream stream : participant.getVideoStreams()){
                        stopRenderingVideo(stream);
                    }
                }
                teamsCall.hangUp().get();
            }else {
                for(RemoteParticipant participant : call.getRemoteParticipants()){
                    for (RemoteVideoStream stream : participant.getVideoStreams()){
                        stopRenderingVideo(stream);
                    }
                }
                call.hangUp().get();
            }
            switchSourceButton.setVisibility(View.INVISIBLE);
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        if (previewRenderer != null) {
            previewRenderer.dispose();
        }
    }

    public void turnOnLocalVideo() {
        Log.d(TAG, "Getting cameras from device manager");
        if(currentVideoStream != null) {
            try {
                Log.d(TAG, "Creating local video stream");
                showPreview();
                if (isCte && teamsCall != null){
                    teamsCall.startVideo(this, currentVideoStream).get();
                }else if (call != null) {
                    call.startVideo(this, currentVideoStream).get();
                }
                switchSourceButton.setVisibility(View.VISIBLE);
                Log.d(TAG, "Finished turning on local video");
            } catch (CallingCommunicationException acsException) {
                acsException.printStackTrace();
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            Log.d(TAG, "No cameras are available");
        }
    }

    public void turnOffLocalVideo() {
        Log.d(TAG, "Turning off local video");
        try {
            LinearLayout container = findViewById(R.id.localvideocontainer);
            for (int i = 0; i < container.getChildCount(); ++i) {
                Object tag = container.getChildAt(i).getTag();
                if (tag != null && (int)tag == 0) {
                    container.removeViewAt(i);
                }
            }
            switchSourceButton.setVisibility(View.INVISIBLE);
            //previewRenderer.dispose();
            //previewRenderer = null;
            if(isCte && teamsCall != null){
                teamsCall.stopVideo(this, currentVideoStream).get();
            }else if (call != null) {
                call.stopVideo(this, currentVideoStream).get();
            }
        } catch (CallingCommunicationException acsException) {
            acsException.printStackTrace();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private VideoDeviceInfo getNextAvailableCamera(VideoDeviceInfo camera) {
        Log.d(TAG, "Getting cameras from DeviceManager");
        List<VideoDeviceInfo> cameras = deviceManager.getCameras();
        int currentIndex = 0;
        if (camera == null) {
            return cameras.isEmpty() ? null : cameras.get(0);
        }

        for (int i = 0; i < cameras.size(); i++) {
            if (camera.getId().equals(cameras.get(i).getId())) {
                currentIndex = i;
                break;
            }
        }
        int newIndex = (currentIndex + 1) % cameras.size();
        return cameras.get(newIndex);
    }

    private void showPreview() {
        if (previewRenderer == null) {
            Log.d(TAG, "Creating renderer");
            previewRenderer = new VideoStreamRenderer(currentVideoStream, this);
            preview = previewRenderer.createView(new CreateViewOptions(ScalingMode.FIT));
            preview.setTag(0);
        } else {
            Log.d(TAG, "Reusing existing renderer");
        }
        LinearLayout layout = findViewById(R.id.localvideocontainer);
        runOnUiThread(() -> {
            layout.addView(preview);
            switchSourceButton.setVisibility(View.VISIBLE);
        });
    }

    public void switchSource() {
        if (currentVideoStream != null) {
            try {
                currentCamera = getNextAvailableCamera(currentCamera);
                currentVideoStream.switchSource(currentCamera).get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleCallOnStateChanged(PropertyChangedEvent args) {
        if (call.getState() == CallState.CONNECTED) {
            runOnUiThread(() -> Toast.makeText(this, "Call is CONNECTED", Toast.LENGTH_SHORT).show());
            handleCallState();
        }
        if (call.getState() == CallState.DISCONNECTED) {
            runOnUiThread(() -> Toast.makeText(this, "Call is DISCONNECTED", Toast.LENGTH_SHORT).show());
            if (previewRenderer != null) {
                previewRenderer.dispose();
            }
            switchSourceButton.setVisibility(View.INVISIBLE);
        }
    }

    private void handleTeamsCallOnStateChanged(PropertyChangedEvent args) {
        if (teamsCall.getState() == CallState.CONNECTED) {
            runOnUiThread(() -> Toast.makeText(this, "Call is CONNECTED", Toast.LENGTH_SHORT).show());
            handleTeamsCallState();
        }
        if (teamsCall.getState() == CallState.DISCONNECTED) {
            runOnUiThread(() -> Toast.makeText(this, "Call is DISCONNECTED", Toast.LENGTH_SHORT).show());
            if (previewRenderer != null) {
                previewRenderer.dispose();
            }
            switchSourceButton.setVisibility(View.INVISIBLE);
        }
    }

    private void handleCallState() {
        handleAddedParticipants(call.getRemoteParticipants());
    }

    private void handleTeamsCallState() {
        handleAddedParticipants(teamsCall.getRemoteParticipants());
    }

    private void answerIncomingCall() {
        Context context = this.getApplicationContext();
        if (incomingCall == null) {
            return;
        }
        AcceptCallOptions acceptCallOptions = new AcceptCallOptions();
        if(currentVideoStream != null) {
            LocalVideoStream[] videoStreams = new LocalVideoStream[1];
            videoStreams[0] = currentVideoStream;
            VideoOptions videoOptions = new VideoOptions(videoStreams);
            acceptCallOptions.setVideoOptions(videoOptions);
            showPreview();
        }
        try {
            call = incomingCall.accept(context, acceptCallOptions).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        remoteParticipantUpdatedListener = this::handleRemoteParticipantsUpdate;
        onStateChangedListener = this::handleCallOnStateChanged;
        call.addOnRemoteParticipantsUpdatedListener(remoteParticipantUpdatedListener);
        call.addOnStateChangedListener(onStateChangedListener);
    }

    private void answerTeamsIncomingCall() {
        Context context = this.getApplicationContext();
        if (teamsIncomingCall == null) {
            return;
        }
        AcceptCallOptions acceptCallOptions = new AcceptCallOptions();
        if(currentVideoStream != null) {
            LocalVideoStream[] videoStreams = new LocalVideoStream[1];
            videoStreams[0] = currentVideoStream;
            VideoOptions videoOptions = new VideoOptions(videoStreams);
            acceptCallOptions.setVideoOptions(videoOptions);
            showPreview();
        }
        try {
            teamsCall = teamsIncomingCall.accept(context, acceptCallOptions).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        remoteParticipantUpdatedListener = this::handleRemoteParticipantsUpdate;
        onStateChangedListener = this::handleTeamsCallOnStateChanged;
        teamsCall.addOnRemoteParticipantsUpdatedListener(remoteParticipantUpdatedListener);
        teamsCall.addOnStateChangedListener(onStateChangedListener);
    }

    public void handleRemoteParticipantsUpdate(ParticipantsUpdatedEvent args) {
        handleAddedParticipants(args.getAddedParticipants());
        handleRemovedParticipants(args.getRemovedParticipants());
    }

    private void handleAddedParticipants(List<RemoteParticipant> participants) {
        for (RemoteParticipant remoteParticipant : participants) {
            if(!joinedParticipants.contains(getId(remoteParticipant))) {
                joinedParticipants.add(getId(remoteParticipant));

                if (renderRemoteVideo) {
                    for (RemoteVideoStream stream : remoteParticipant.getVideoStreams()) {
                        if (!streamData.containsKey(stream.getId())) {
                            Log.i("MainActivity", "HandleAddedParticipants => Started Rendering of Remote Video for video Id: " + stream.getId());
                            StreamData data = new StreamData(stream, null, null);
                            streamData.put(stream.getId(), data);
                            startRenderingVideo(data);
                        } else {
                            Log.w("MainActivity", "HandleAddedParticipants => Rendering of Remote Video already started for video Id: " + stream.getId());
                        }
                    }
                }
                remoteParticipant.addOnVideoStreamsUpdatedListener(videoStreamsEventArgs -> videoStreamsUpdated(videoStreamsEventArgs));
            }
        }
    }

    public String getId(final RemoteParticipant remoteParticipant) {
        final CommunicationIdentifier identifier = remoteParticipant.getIdentifier();
        if (identifier instanceof PhoneNumberIdentifier) {
            return ((PhoneNumberIdentifier) identifier).getPhoneNumber();
        } else if (identifier instanceof MicrosoftTeamsUserIdentifier) {
            return ((MicrosoftTeamsUserIdentifier) identifier).getUserId();
        } else if (identifier instanceof CommunicationUserIdentifier) {
            return ((CommunicationUserIdentifier) identifier).getId();
        } else {
            return ((UnknownIdentifier) identifier).getId();
        }
    }

    private void handleRemovedParticipants(List<RemoteParticipant> removedParticipants) {
        for (RemoteParticipant remoteParticipant : removedParticipants) {
            if(joinedParticipants.contains(getId(remoteParticipant))) {
                joinedParticipants.remove(getId(remoteParticipant));
            }
        }
    }

    private void videoStreamsUpdated(RemoteVideoStreamsEvent videoStreamsEventArgs) {
        for(RemoteVideoStream stream : videoStreamsEventArgs.getAddedRemoteVideoStreams()) {
            if (!streamData.containsKey(stream.getId())) {
                Log.i("MainActivity", "VideoStreamsUpdated => Started Rendering of Remote Video for video Id: " + stream.getId());
                StreamData data = new StreamData(stream, null, null);
                streamData.put(stream.getId(), data);
                if (renderRemoteVideo) {
                    startRenderingVideo(data);
                }
            } else {
                Log.w("MainActivity", "VideoStreamsUpdated => Rendering of Remote Video already started for video Id: " + stream.getId());
            }
        }

        for(RemoteVideoStream stream : videoStreamsEventArgs.getRemovedRemoteVideoStreams()) {
            stopRenderingVideo(stream);
        }
    }

    void startRenderingVideo(StreamData data){
        if (data.renderer != null) {
            return;
        }
        GridLayout layout = ((GridLayout)findViewById(R.id.remotevideocontainer));
        data.renderer = new VideoStreamRenderer(data.stream, this);
        data.renderer.addRendererListener(new RendererListener() {
            @Override
            public void onFirstFrameRendered() {
                String text = data.renderer.getSize().toString();
                Log.i("MainActivity", "Video rendering at: " + text);
            }

            @Override
            public void onRendererFailedToStart() {
                String text = "Video failed to render";
                Log.i("MainActivity", text);
            }
        });
        data.rendererView = data.renderer.createView(new CreateViewOptions(ScalingMode.FIT));
        data.rendererView.setTag(data.stream.getId());
        runOnUiThread(() -> {
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(layout.getLayoutParams());
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            params.height = (int)(displayMetrics.heightPixels / 2.5);
            params.width = displayMetrics.widthPixels / 2;
            layout.addView(data.rendererView, params);
        });
    }

    void stopRenderingVideo(RemoteVideoStream stream) {
        StreamData data = streamData.get(stream.getId());
        if (data == null || data.renderer == null) {
            return;
        }
        runOnUiThread(() -> {
            GridLayout layout = findViewById(R.id.remotevideocontainer);
            for(int i = 0; i < layout.getChildCount(); ++ i) {
                View childView =  layout.getChildAt(i);
                if ((int)childView.getTag() == data.stream.getId()) {
                    layout.removeViewAt(i);
                }
            }
        });
        data.rendererView = null;
        // Dispose renderer
        data.renderer.dispose();
        data.renderer = null;
    }

    static class StreamData {
        RemoteVideoStream stream;
        VideoStreamRenderer renderer;
        VideoStreamRendererView rendererView;
        StreamData(RemoteVideoStream stream, VideoStreamRenderer renderer, VideoStreamRendererView rendererView) {
            this.stream = stream;
            this.renderer = renderer;
            this.rendererView = rendererView;
        }
    }

    public void onCallTypeSelected(View view) {
        boolean checked = ((RadioButton) view).isChecked();
        EditText callIdView = findViewById(R.id.call_id);

        switch(view.getId()) {
            case R.id.acs_call:
                if(checked){
                    isCte = false;
                    setupAgent();
                }
                break;
            case R.id.cte_call:
                if(checked){
                    isCte = true;
                    setupAgent();
                }
                break;
            case R.id.one_to_one_call:
                if (checked){
                    callIdView.setHint("Callee id");
                }
                break;
            case R.id.group_call:
                if (checked){
                    callIdView.setHint("Group Call GUID");
                }
                break;
        }
    }

    private void setupButtonListener(){
        Button callButton = findViewById(R.id.call_button);
        if(isCte) {
            callButton.setOnClickListener(l -> startTeamsCall());
        }else{
            callButton.setOnClickListener(l -> startCall());
        }
    }
}
