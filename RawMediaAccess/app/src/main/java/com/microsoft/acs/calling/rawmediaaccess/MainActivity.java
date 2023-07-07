package com.microsoft.acs.calling.rawmediaaccess;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;

import com.azure.android.communication.calling.Call;
import com.azure.android.communication.calling.CallAgent;
import com.azure.android.communication.calling.CallAgentOptions;
import com.azure.android.communication.calling.CallClient;
import com.azure.android.communication.calling.CallVideoStream;
import com.azure.android.communication.calling.CallingCommunicationException;
import com.azure.android.communication.calling.HangUpOptions;
import com.azure.android.communication.calling.IncomingVideoOptions;
import com.azure.android.communication.calling.IncomingVideoStream;
import com.azure.android.communication.calling.JoinCallOptions;
import com.azure.android.communication.calling.JoinMeetingLocator;
import com.azure.android.communication.calling.OutgoingVideoOptions;
import com.azure.android.communication.calling.OutgoingVideoStream;
import com.azure.android.communication.calling.ParticipantsUpdatedEvent;
import com.azure.android.communication.calling.RawIncomingVideoStream;
import com.azure.android.communication.calling.RawOutgoingVideoStream;
import com.azure.android.communication.calling.RawOutgoingVideoStreamOptions;
import com.azure.android.communication.calling.RawVideoFrame;
import com.azure.android.communication.calling.RawVideoFrameBuffer;
import com.azure.android.communication.calling.RawVideoFrameReceivedEvent;
import com.azure.android.communication.calling.RemoteParticipant;
import com.azure.android.communication.calling.ScreenShareOutgoingVideoStream;
import com.azure.android.communication.calling.TeamsMeetingLinkLocator;
import com.azure.android.communication.calling.VideoStreamFormat;
import com.azure.android.communication.calling.VideoStreamFormatChangedEvent;
import com.azure.android.communication.calling.VideoStreamPixelFormat;
import com.azure.android.communication.calling.VideoStreamResolution;
import com.azure.android.communication.calling.VideoStreamStateChangedEvent;
import com.azure.android.communication.calling.VideoStreamType;
import com.azure.android.communication.calling.VirtualOutgoingVideoStream;
import com.azure.android.communication.common.CommunicationTokenCredential;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity
{
    private Map<Integer, IncomingVideoStream> incomingVideoStreamMap;
    private List<VideoStreamType> outgoingVideoStreamTypeList;
    private EditText tokenEditText;
    private EditText meetingUrlEditText;
    private CallClient callClient;
    private CallAgent callAgent;
    private Call call;
    private RawOutgoingVideoStream rawOutgoingVideoStream;
    private ScreenCaptureService screenCaptureService;
    private VideoStreamType outgoingVideoStreamType;
    private VideoFrameSender videoFrameSender;
    private ConstraintLayout videoContainer;
    private int w = 0;
    private int h = 0;
    private int frameRate = 0;
    private double maxWidth = 1920.0;
    private double maxHeight = 1080.0;
    private boolean callInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        InitializeUIVariables();

        tokenEditText.setText("eyJhbGciOiJSUzI1NiIsImtpZCI6IjVFODQ4MjE0Qzc3MDczQUU1QzJCREU1Q0NENTQ0ODlEREYyQzRDODQiLCJ4NXQiOiJYb1NDRk1kd2M2NWNLOTVjelZSSW5kOHNUSVEiLCJ0eXAiOiJKV1QifQ.eyJza3lwZWlkIjoiYWNzOmRmZjdhMmZmLWYwNmQtNDA1Mi04NWRlLTE3ZGUxYjg2OTEyNF8wMDAwMDAxOS1jZGVlLTkyYzItOTliZi1hNDNhMGQwMDgyZmMiLCJzY3AiOjE3OTIsImNzaSI6IjE2ODg2OTQ3NDciLCJleHAiOjE2ODg3ODExNDcsInJnbiI6ImFtZXIiLCJhY3NTY29wZSI6InZvaXAiLCJyZXNvdXJjZUlkIjoiZGZmN2EyZmYtZjA2ZC00MDUyLTg1ZGUtMTdkZTFiODY5MTI0IiwicmVzb3VyY2VMb2NhdGlvbiI6InVuaXRlZHN0YXRlcyIsImlhdCI6MTY4ODY5NDc0N30.UnYDr9EuvqZ7_i0Zv0-FYFOGsksHpJoAI_gJQlr8g3B3W45KR7cajYz2dBy6gQl2F7YPYJvZk9kX0ngp8R3kwFhBEEU8PWDNKeJT0iVPNkW_P-6XENbvoDSGIz64xqhzr2ITbe0fyCULB9hTRWOBGM-v3EEnbNlt1_EI8XBKnLmN7IpmEVBnoeis5BjqQlcJcs94s2f0gNk136qxAMX5lehs-n2haHqu-zH4X-M0-DuQixQdD0Lw85bVGEG5RsdplcDkhqLXe-vyhiFkRmJrcIcCHIU9R8sm3Waiwx-pCSu-RVGTLHEy4CwQOrfUAxs3TP1XlJetsTweVznX6XdHxQ");
        meetingUrlEditText.setText("https://teams.microsoft.com/l/meetup-join/19%3ameeting_ZTNlM2M4ZDUtOGI1Zi00YmQ3LWJkMGUtM2E2OTY3ZTdmZjYx%40thread.v2/0?context=%7b%22Tid%22%3a%2272f988bf-86f1-41af-91ab-2d7cd011db47%22%2c%22Oid%22%3a%22744e8f01-fbf6-40b3-b594-00792ff4276e%22%7d");

        incomingVideoStreamMap = new HashMap<>();

        GetAllPermissions();

        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
    }

    private void InitializeUIVariables()
    {
        tokenEditText = findViewById(R.id.TokenEditText);
        meetingUrlEditText = findViewById(R.id.MeetingUrlEditText);

        outgoingVideoStreamTypeList = Arrays.asList(
                VideoStreamType.VIRTUAL_OUTGOING,
                VideoStreamType.SCREEN_SHARE_OUTGOING
        );

        Spinner outgoingVideoStreamTypePicker = findViewById(R.id.outgoingVideoStreamTypePicker);
        outgoingVideoStreamTypePicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l)
            {
                outgoingVideoStreamType = outgoingVideoStreamTypeList.get(i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) { }
        });

        ArrayAdapter<String> outgoingVideoStreamTypeArrayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                Arrays.asList("Virtual", "ScreenShare"));

        outgoingVideoStreamTypeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        outgoingVideoStreamTypePicker.setAdapter(outgoingVideoStreamTypeArrayAdapter);
    }

    private void GetAllPermissions()
    {
        String[] requiredPermissions = new String[]
        {
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_PHONE_STATE
        };

        ArrayList<String> permissionsToAskFor = new ArrayList<>();
        for (String permission : requiredPermissions)
        {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
            {
                permissionsToAskFor.add(permission);
            }
        }

        if (!permissionsToAskFor.isEmpty())
        {
            ActivityCompat.requestPermissions(this, permissionsToAskFor.toArray(new String[0]), 1);
        }
    }

    public void GetScreenSharePermissions()
    {
        try
        {
            MediaProjectionManager mediaProjectionManager =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(),
                    Constants.SCREEN_SHARE_REQUEST_INTENT_REQ_CODE);
        }
        catch (Exception e)
        {
            Toast.makeText(getApplicationContext(),
                            "Could not start screen share due to failure to startActivityForResult for mediaProjectionManager screenCaptureIntent",
                            Toast.LENGTH_SHORT)
                    .show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == Constants.SCREEN_SHARE_REQUEST_INTENT_REQ_CODE)
        {
            if (resultCode == Activity.RESULT_OK && data != null)
            {
                screenCaptureService = new ScreenCaptureService(this,
                        rawOutgoingVideoStream,
                        w,
                        h,
                        frameRate,
                        resultCode,
                        data);
                screenCaptureService.Start();
            }
            else
            {
                Toast.makeText(getApplicationContext(),
                                "User cancelled, did not give permission to capture screen",
                                Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }

    private void CreateCallAgent()
    {
        String token = tokenEditText.getText().toString();

        try
        {
            CommunicationTokenCredential credential = new CommunicationTokenCredential(token);
            callClient = new CallClient();

            CallAgentOptions callAgentOptions = new CallAgentOptions();
            callAgentOptions.setDisplayName("Android Quickstart User");

            callAgent = callClient.createCallAgent(getApplicationContext(), credential, callAgentOptions).get();
        }
        catch (Exception ex)
        {
            Toast.makeText(getApplicationContext(),
                    "Failed to create call agent",
                    Toast.LENGTH_SHORT)
                    .show();
        }
    }

    public void StartCall(View view)
    {
        if (callInProgress)
        {
            return;
        }

        callInProgress = true;
        if (callClient == null)
        {
            CreateCallAgent();
        }

        IncomingVideoOptions incomingVideoOptions = new IncomingVideoOptions()
                .setStreamType(VideoStreamType.RAW_INCOMING);

        OutgoingVideoOptions outgoingVideoOptions = CreateOutgoingVideoOptions();

        JoinCallOptions joinCallOptions = new JoinCallOptions()
                .setIncomingVideoOptions(incomingVideoOptions)
                .setOutgoingVideoOptions(outgoingVideoOptions);

        String meetingLink = meetingUrlEditText.getText().toString();
        JoinMeetingLocator locator = new TeamsMeetingLinkLocator(meetingLink);

        try
        {
            call = callAgent.join(getApplicationContext(), locator, joinCallOptions);
            callInProgress = true;
        }
        catch (CallingCommunicationException ex)
        {
            runOnUiThread(() ->
                    Toast.makeText(getApplicationContext(),
                            "Unexpected error while starting the call",
                            Toast.LENGTH_LONG)
                            .show());
        }

        if (call != null)
        {
            AddRemoteParticipantList(call.getRemoteParticipants());

            call.addOnRemoteParticipantsUpdatedListener(this::OnRemoteParticipantsUpdated);
        }

        Toast.makeText(getApplicationContext(),
                "Started",
                Toast.LENGTH_LONG)
                .show();
    }

    private void OnRemoteParticipantsUpdated(ParticipantsUpdatedEvent event)
    {
        AddRemoteParticipantList(event.getAddedParticipants());

        for (RemoteParticipant remoteParticipant : event.getRemovedParticipants())
        {
            remoteParticipant.removeOnVideoStreamStateChangedListener(this::OnVideoStreamStateChanged);
        }
    }

    private void AddRemoteParticipantList(List<RemoteParticipant> remoteParticipantList)
    {
        for (RemoteParticipant remoteParticipant : remoteParticipantList)
        {
            for (IncomingVideoStream incomingVideoStream : remoteParticipant.getIncomingVideoStreams())
            {
                OnIncomingVideoStreamStateChanged(incomingVideoStream);
            }

            remoteParticipant.addOnVideoStreamStateChangedListener(this::OnVideoStreamStateChanged);
        }
    }

    private VideoStreamFormat CreateVideoStreamFormat()
    {
        w = 640;
        h = 360;
        frameRate = 15;

        VideoStreamFormat videoStreamFormat = new VideoStreamFormat();
        videoStreamFormat.setPixelFormat(VideoStreamPixelFormat.RGBA);
        videoStreamFormat.setFramesPerSecond(frameRate);

        switch (outgoingVideoStreamType)
        {
            case VIRTUAL_OUTGOING:
                videoStreamFormat.setResolution(VideoStreamResolution.P360);
                break;
            case SCREEN_SHARE_OUTGOING:
                GetDisplaySize();
                videoStreamFormat.setWidth(w);
                videoStreamFormat.setHeight(h);
                break;
        }

        videoStreamFormat.setStride1(w * 4);

        return videoStreamFormat;
    }

    private OutgoingVideoOptions CreateOutgoingVideoOptions()
    {
        VideoStreamFormat videoFormat = CreateVideoStreamFormat();

        RawOutgoingVideoStreamOptions rawOutgoingVideoStreamOptions = new RawOutgoingVideoStreamOptions();
        rawOutgoingVideoStreamOptions.setFormats(Arrays.asList(videoFormat));

        switch (outgoingVideoStreamType)
        {
            case VIRTUAL_OUTGOING:
                VirtualOutgoingVideoStream virtualOutgoingVideoStream =
                        new VirtualOutgoingVideoStream(rawOutgoingVideoStreamOptions);
                virtualOutgoingVideoStream.addOnStateChangedListener(this::OnVideoStreamStateChanged);
                virtualOutgoingVideoStream.addOnFormatChangedListener(this::OnVideoStreamFormatChanged);
                rawOutgoingVideoStream = virtualOutgoingVideoStream;

                break;
            case SCREEN_SHARE_OUTGOING:
                ScreenShareOutgoingVideoStream screenShareOutgoingVideoStream =
                        new ScreenShareOutgoingVideoStream(rawOutgoingVideoStreamOptions);
                screenShareOutgoingVideoStream.addOnStateChangedListener(this::OnVideoStreamStateChanged);
                screenShareOutgoingVideoStream.addOnFormatChangedListener(this::OnVideoStreamFormatChanged);
                rawOutgoingVideoStream = screenShareOutgoingVideoStream;

                break;
        }

        return new OutgoingVideoOptions()
                .setOutgoingVideoStreams(Arrays.asList(rawOutgoingVideoStream));
    }

    private void OnVideoStreamStateChanged(VideoStreamStateChangedEvent event)
    {
        CallVideoStream callVideoStream = event.getStream();

        switch (callVideoStream.getDirection())
        {
            case OUTGOING:
                OnOutgoingVideoStreamStateChanged((OutgoingVideoStream)callVideoStream);
                break;
            case INCOMING:
                OnIncomingVideoStreamStateChanged((IncomingVideoStream)callVideoStream);
                break;
        }
    }

    private void OnOutgoingVideoStreamStateChanged(OutgoingVideoStream outgoingVideoStream)
    {
        switch (outgoingVideoStream.getState())
        {
            case STARTED:
                switch (outgoingVideoStream.getType())
                {
                    case VIRTUAL_OUTGOING:
                        if (videoFrameSender == null)
                        {
                            videoFrameSender = new VideoFrameSender(this, rawOutgoingVideoStream);
                        }

                        videoFrameSender.Start();
                        break;
                    case SCREEN_SHARE_OUTGOING:
                        if (screenCaptureService == null)
                        {
                            GetScreenSharePermissions();
                        }
                        else
                        {
                            screenCaptureService.Start();
                        }

                        break;
                }

                break;
            case STOPPED:
                switch (outgoingVideoStream.getType())
                {
                    case VIRTUAL_OUTGOING:
                        if (videoFrameSender != null)
                        {
                            videoFrameSender.Stop();
                        }

                        break;
                    case SCREEN_SHARE_OUTGOING:
                        if (screenCaptureService != null)
                        {
                            screenCaptureService.Stop();
                        }

                        break;
                }

                break;
        }
    }

    private void OnIncomingVideoStreamStateChanged(IncomingVideoStream incomingVideoStream)
    {
        switch (incomingVideoStream.getState())
        {
            case AVAILABLE:
            {
                if (!incomingVideoStreamMap.containsKey(incomingVideoStream.getId()))
                {
                    RawIncomingVideoStream rawIncomingVideoStream = (RawIncomingVideoStream) incomingVideoStream;
                    rawIncomingVideoStream.start();

                    incomingVideoStreamMap.put(incomingVideoStream.getId(), incomingVideoStream);
                }

                break;
            }
            case NOT_AVAILABLE:
                if (incomingVideoStreamMap.containsKey(incomingVideoStream.getId()))
                {
                    incomingVideoStreamMap.remove(incomingVideoStream.getId());
                }

                break;
        }
    }

    private void OnVideoStreamFormatChanged(VideoStreamFormatChangedEvent event)
    {
        VideoStreamFormat videoFormat = event.getFormat();
    }

    public void EndCall(View view)
    {
        if (!callInProgress)
        {
            return;
        }

        Executors.newCachedThreadPool().submit(() -> {
            try
            {
                if (call != null)
                {
                    if (videoFrameSender != null)
                    {
                        videoFrameSender.Stop();
                        videoFrameSender = null;
                    }

                    if (screenCaptureService != null)
                    {
                        screenCaptureService.Stop();
                        screenCaptureService = null;
                    }

                    call.removeOnRemoteParticipantsUpdatedListener(this::OnRemoteParticipantsUpdated);

                    if (rawOutgoingVideoStream != null)
                    {
                        call.stopVideo(this, rawOutgoingVideoStream).get();
                    }

                    call.hangUp(new HangUpOptions()).get();
                    call = null;
                }

                incomingVideoStreamMap.clear();
                callInProgress = false;
            }
            catch (Exception ex)
            {
                Toast.makeText(getApplicationContext(),
                                "Unexpected error while ending the call",
                                Toast.LENGTH_LONG)
                        .show();
            }

            Toast.makeText(getApplicationContext(),
                            "Stopped",
                            Toast.LENGTH_LONG)
                    .show();
        });
    }

    private void GetDisplaySize()
    {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        w = displayMetrics.widthPixels;
        h = displayMetrics.heightPixels;

        if (h > maxHeight)
        {
            double percentage = Math.abs((maxHeight / h) - 1);
            w = (int)Math.ceil((w * percentage));
            h = (int)maxHeight;
        }

        if (w > maxWidth)
        {
            double percentage = Math.abs((maxWidth / w) - 1);
            h = (int)Math.ceil((h * percentage));
            w = (int)maxWidth;
        }
    }
}