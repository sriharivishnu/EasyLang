package com.magnitudestudios.sriharivishnu.easylang;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.BaseArFragment;
import com.google.firebase.ml.common.modeldownload.FirebaseModelDownloadConditions;
import com.google.firebase.ml.naturallanguage.FirebaseNaturalLanguage;
import com.google.firebase.ml.naturallanguage.translate.FirebaseTranslateLanguage;
import com.google.firebase.ml.naturallanguage.translate.FirebaseTranslator;
import com.google.firebase.ml.naturallanguage.translate.FirebaseTranslatorOptions;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.label.FirebaseVisionCloudImageLabelerOptions;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabel;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabeler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static com.google.ar.sceneform.rendering.ViewRenderable.builder;

public class MainActivity extends AppCompatActivity {

    private ArFragment fragment;
    private Bitmap bitmap;
    private Button button;
    private FirebaseVisionImage firebaseVisionImage;
    FirebaseVisionImageLabeler labeler;
    private TextView textView;
    private ArrayList<Anchor> anchors;
    private TextToSpeech textToSpeech;
    private Session session;
    private Boolean language_available = false;
    private FirebaseTranslator englishfrench;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseVisionCloudImageLabelerOptions options = new FirebaseVisionCloudImageLabelerOptions.Builder().setConfidenceThreshold(0.7f).build();
        labeler = FirebaseVision.getInstance().getCloudImageLabeler(options);
        fragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        fragment.getPlaneDiscoveryController().hide();
        fragment.getPlaneDiscoveryController().setInstructionView(null);
        anchors = new ArrayList<>();
        textView = findViewById(R.id.textView);
        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int i) {
                if (i != TextToSpeech.ERROR) {
                    textToSpeech.setLanguage(Locale.FRENCH);
                }
            }
        });
        try {
            Session session = new Session(this);
        }
        catch (UnavailableArcoreNotInstalledException e) {
            Log.d("ERROR: ", "AR Core not installed");
        }
        catch (UnavailableApkTooOldException e) {
            Log.d("ERROR: ", "APK too old");
        }
        catch (UnavailableSdkTooOldException e) {
            Log.d("ERROR: ", "SDK too old");
        }
        catch (UnavailableDeviceNotCompatibleException e) {
            Log.d("ERROR: ", "Device not compatible");
        }

        button = findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getImage();
            }
        });

        FirebaseTranslatorOptions firebaseTranslatorOptions = new FirebaseTranslatorOptions.Builder()
                        .setSourceLanguage(FirebaseTranslateLanguage.EN)
                        .setTargetLanguage(FirebaseTranslateLanguage.FR)
                        .build();
        englishfrench = FirebaseNaturalLanguage.getInstance().getTranslator(firebaseTranslatorOptions);

        FirebaseModelDownloadConditions conditions = new FirebaseModelDownloadConditions.Builder()
                .requireWifi()
                .build();
        englishfrench.downloadModelIfNeeded(conditions).addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void v) {
                                language_available = true;
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {

                            }
                        });

    }
    public void getImage() {
        ArSceneView view = fragment.getArSceneView();
        bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        final HandlerThread handlerThread = new HandlerThread("ViewCopier");
        handlerThread.start();

        PixelCopy.request(view, bitmap, new PixelCopy.OnPixelCopyFinishedListener() {
            @Override
            public void onPixelCopyFinished(int i) {
                firebaseVisionImage = FirebaseVisionImage.fromBitmap(bitmap);
                labeler.processImage(firebaseVisionImage)
                        .addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionImageLabel>>() {
                            @Override
                            public void onSuccess(List<FirebaseVisionImageLabel> labels) {
                                // Task completed successfully
                                // ...
                                String result = getLabels(labels);
                                Anchor anchor = fragment.getArSceneView().getSession().createAnchor(fragment.getArSceneView().getArFrame().getCamera().getDisplayOrientedPose().compose(Pose.makeTranslation(0f,-0.5f,-1f)));
                                if (result != null) {
                                    addLabel(result, anchor);
                                }
                                Log.d("TAG","REACHEd");

                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                // Task failed with an exception
                                // ...
                                Log.d("TAG", "Failed");
                            }
                        }).addOnCompleteListener(new OnCompleteListener<List<FirebaseVisionImageLabel>>() {
                        @Override
                        public void onComplete(@NonNull Task<List<FirebaseVisionImageLabel>> task) {

                        }
                });
            }
        }, new Handler(handlerThread.getLooper()));
    }

    private String getLabels(List<FirebaseVisionImageLabel> labels) {
        if (labels.size() > 0) {
            textView.setText(labels.get(0).getText());
            return labels.get(0).getText();
        }
        else {
            return null;
        }
    }

    private void addLabel(String label, Anchor anchor) {

        CompletableFuture future = ViewRenderable.builder().setView(this, R.layout.label).build();

        future.thenAccept(renderable -> {
            addToScene((ViewRenderable) renderable, anchor, label);
        });
    }
    private void addToScene(ViewRenderable renderable, Anchor anchor, String label) {
        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setRenderable(renderable);
        fragment.getArSceneView().getScene().addChild(anchorNode);
        View v = renderable.getView();
        TextView title = v.findViewById(R.id.label_title);
        englishfrench.translate(label).addOnSuccessListener(new OnSuccessListener<String>() {
            @Override
            public void onSuccess(String s) {
                title.setText(s);
                textToSpeech.speak(s, TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });

    }
}