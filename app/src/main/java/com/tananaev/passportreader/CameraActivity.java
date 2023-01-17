package com.tananaev.passportreader;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

public class CameraActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2{
    private static final String TAG="MainActivity";

    private Mat mRgba;
    private Mat mGray;
    private CameraBridgeViewBase mOpenCvCameraView;
    private ImageView translateButton, takePictureButton, showImageButton, currentOmage;
    private TextView textView;

    private TextRecognizer textRecognizer;

    private String cameraOrRecognizeText = "camera";
    private Bitmap bitmap = null;


    private BaseLoaderCallback mLoaderCallback =new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status){
                case LoaderCallbackInterface
                        .SUCCESS:{
                    Log.i(TAG,"OpenCv Is loaded");
                    mOpenCvCameraView.enableView();
                }
                default:
                {
                    super.onManagerConnected(status);

                }
                break;
            }
        }
    };

    public CameraActivity(){
        Log.i(TAG,"Instantiated new "+this.getClass());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        int MY_PERMISSIONS_REQUEST_CAMERA=0;
        // if camera permission is not given it will ask for it on device
        if (ContextCompat.checkSelfPermission(CameraActivity.this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(CameraActivity.this, new String[] {Manifest.permission.CAMERA}, MY_PERMISSIONS_REQUEST_CAMERA);
        }

        setContentView(R.layout.activity_camera);

        mOpenCvCameraView=(CameraBridgeViewBase) findViewById(R.id.frame_Surface);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        textView = findViewById(R.id.textview);
        textView.setVisibility(View.GONE);

        // load text recognition model
//        textRecognizer = TextRecognition.getClient(new TextRecognizerOptions.Builder().build());
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        takePictureButton = findViewById(R.id.take_picture_button);
        takePictureButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN){
                    return true;
                }

                if (event.getAction() == MotionEvent.ACTION_UP){
                    if (cameraOrRecognizeText == "camera"){
                        takePictureButton.setColorFilter(Color.DKGRAY);
                        Mat a = mRgba.t();
                        Core.flip(a, mRgba, 1);
                        a.release();
                        bitmap = Bitmap.createBitmap(mRgba.cols(), mRgba.rows(), Bitmap.Config.ARGB_8888);
                        Utils.matToBitmap(mRgba, bitmap);
                        mOpenCvCameraView.disableView();
                        cameraOrRecognizeText = "recognizeText";


                    }
                }

                return true;
            }
        });

        translateButton = findViewById(R.id.translate_button);
        translateButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN){
                    translateButton.setColorFilter(Color.DKGRAY);
                    return true;
                }

                if (event.getAction() == MotionEvent.ACTION_UP){
                    translateButton.setColorFilter(Color.WHITE);
                    if(cameraOrRecognizeText == "recognizeText"){
                        textView.setVisibility(View.VISIBLE);
                        InputImage image = InputImage.fromBitmap(bitmap, 0);
                        Task<Text> result = textRecognizer.process(image)
                        .addOnSuccessListener(new OnSuccessListener<Text>() {
                            @Override
                            public void onSuccess(Text text) {
                                textView.setText(text.getText());
                                Log.d("sihdir", text.getText().toString());
                                String stringData = text.getText().toString();
                                if (stringData.contains("P<")){
                                    int index = stringData.indexOf("P<");
                                    String specificData = stringData.substring(index);
                                    String[] rows = specificData.split("\n", 2);

                                    String passportNumber = rows[1].substring(0, 9);
                                    String birthDate = rows[1].substring(13, 19);
                                    String expirationDate = rows[1].substring(21, 27);

                                    textView.setText("Passport = " + passportNumber + "\nBirth Date: " + birthDate + "\nExpiration Date: " + expirationDate);
                                }
                                else if (stringData.contains("I<") || stringData.contains("A<") || stringData.contains("C<")){

                                    Boolean containsA = stringData.contains("A<");
                                    Boolean containsC = stringData.contains("C<");
                                    Boolean containsI = stringData.contains("I<");

                                    int index = -1;
                                    if (containsA){
                                        index = stringData.indexOf("A<");
                                    }
                                    else if (containsC){
                                        index = stringData.indexOf("C<");
                                    }
                                    else if (containsI){
                                        index = stringData.indexOf("I<");
                                    }
                                    String specificData = stringData.substring(index);
                                    String[] rows = specificData.split("\n", 3);
                                    String IDNumber = rows[0].substring(5, 14);
                                    String birthDate = rows[1].substring(0, 6);
                                    String expirationDate = rows[1].substring(8, 14);
                                    textView.setText("Passport = " + IDNumber + "\nBirth Date: " + birthDate + "\nExpiration Date: " + expirationDate);
                                    setResult(Activity.RESULT_OK,
                                            new Intent().putExtra("IDNumber", IDNumber).putExtra("expirationDate", expirationDate)
                                                    .putExtra("birthDate", birthDate));
                                    finish();
                                }

                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Toast.makeText(getApplicationContext(), "FAILED!!!", Toast.LENGTH_LONG).show();

                            }
                        });
                    }

                    return true;
                }

                return false;
            }
        });

        showImageButton = findViewById(R.id.show_image_button);
        showImageButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN){
                }

                if (event.getAction() == MotionEvent.ACTION_DOWN){

                }

                return false;
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (OpenCVLoader.initDebug()){
            //if load success
            Log.d(TAG,"Opencv initialization is done");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
        else{
            //if not loaded
            Log.d(TAG,"Opencv is not loaded. try again");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0,this,mLoaderCallback);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mOpenCvCameraView !=null){
            mOpenCvCameraView.disableView();
        }
    }

    public void onDestroy(){
        super.onDestroy();
        if(mOpenCvCameraView !=null){
            mOpenCvCameraView.disableView();
        }

    }

    public void onCameraViewStarted(int width ,int height){
        mRgba=new Mat(height,width, CvType.CV_8UC4);
        mGray =new Mat(height,width,CvType.CV_8UC1);
    }
    public void onCameraViewStopped(){
        mRgba.release();
    }
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame){
        mRgba=inputFrame.rgba();
        mGray=inputFrame.gray();

        return mRgba;

    }

}