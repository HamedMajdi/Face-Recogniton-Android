/*
 * Copyright 2016 - 2017 Anton Tananaev (anton.tananaev@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tananaev.passportreader;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.ThumbnailUtils;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.tananaev.passportreader.ml.Siamese;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DecimalFormat;

public class ResultActivity extends AppCompatActivity {

    public static final String KEY_FIRST_NAME = "firstName";
    public static final String KEY_LAST_NAME = "lastName";
    public static final String KEY_GENDER = "gender";
    public static final String KEY_STATE = "state";
    public static final String KEY_NATIONALITY = "nationality";
    public static final String KEY_PHOTO = "photo";
    public static final String KEY_PHOTO_BASE64 = "photoBase64";
    public static final String KEY_PASSIVE_AUTH = "passiveAuth";
    public static final String KEY_CHIP_AUTH = "chipAuth";

    Button cameraButton;
    ImageView userImageView;

    int imageSize = 105;
    Bitmap passport_image;
    TextView tvResult;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        cameraButton = findViewById(R.id.btn_open_camera);
        userImageView = findViewById(R.id.iv_camera_photo);
        tvResult = findViewById(R.id.tv_res);

        cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    startActivityForResult(cameraIntent, 3);
                } else {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
                }
            }
        });

        ((TextView) findViewById(R.id.output_first_name)).setText(getIntent().getStringExtra(KEY_FIRST_NAME));
        ((TextView) findViewById(R.id.output_last_name)).setText(getIntent().getStringExtra(KEY_LAST_NAME));
        ((TextView) findViewById(R.id.output_gender)).setText(getIntent().getStringExtra(KEY_GENDER));
        ((TextView) findViewById(R.id.output_state)).setText(getIntent().getStringExtra(KEY_STATE));
        ((TextView) findViewById(R.id.output_nationality)).setText(getIntent().getStringExtra(KEY_NATIONALITY));
        ((TextView) findViewById(R.id.output_passive_auth)).setText(getIntent().getStringExtra(KEY_PASSIVE_AUTH));
        ((TextView) findViewById(R.id.output_chip_auth)).setText(getIntent().getStringExtra(KEY_CHIP_AUTH));

        if (getIntent().hasExtra(KEY_PHOTO)) {
//            ((ImageView) findViewById(R.id.view_photo)).setImageBitmap((Bitmap) getIntent().getParcelableExtra(KEY_PHOTO));
//            Bitmap image = (Bitmap) data.getExtras().get("data");
            passport_image = getIntent().getParcelableExtra(KEY_PHOTO);
            ((ImageView) findViewById(R.id.view_photo)).setImageBitmap(passport_image);

        }
    }

    public void classifyImage(Bitmap image_passport, Bitmap image_user){
        try {
            Siamese model = Siamese.newInstance(getApplicationContext());

            // Creates inputs for reference.
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 105, 105, 3}, DataType.FLOAT32);
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3);
            byteBuffer.order(ByteOrder.nativeOrder());

            int[] intValues = new int[imageSize * imageSize];
            image_passport.getPixels(intValues, 0, image_passport.getWidth(), 0, 0, image_passport.getWidth(), image_passport.getHeight());
            int pixel = 0;
            //iterate over each pixel and extract R, G, and B values. Add those values individually to the byte buffer.
            for(int i = 0; i < imageSize; i ++){
                for(int j = 0; j < imageSize; j++){
                    int val = intValues[pixel++]; // RGB
                    byteBuffer.putFloat(((val >> 16) & 0xFF) * (1.f / 255));
                    byteBuffer.putFloat(((val >> 8) & 0xFF) * (1.f / 255));
                    byteBuffer.putFloat((val & 0xFF) * (1.f / 255));
                }
            }
            inputFeature0.loadBuffer(byteBuffer);


            TensorBuffer inputFeature1 = TensorBuffer.createFixedSize(new int[]{1, 105, 105, 3}, DataType.FLOAT32);
            byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3);
            byteBuffer.order(ByteOrder.nativeOrder());

            int[] intValues_user = new int[imageSize * imageSize];
            image_user.getPixels(intValues_user, 0, image_user.getWidth(), 0, 0, image_user.getWidth(), image_user.getHeight());
            pixel = 0;
            //iterate over each pixel and extract R, G, and B values. Add those values individually to the byte buffer.
            for(int i = 0; i < imageSize; i ++){
                for(int j = 0; j < imageSize; j++){
                    int val = intValues_user[pixel++]; // RGB
                    byteBuffer.putFloat(((val >> 16) & 0xFF) * (1.f / 255));
                    byteBuffer.putFloat(((val >> 8) & 0xFF) * (1.f / 255));
                    byteBuffer.putFloat((val & 0xFF) * (1.f / 255));
                }
            }

            inputFeature1.loadBuffer(byteBuffer);


            // Runs model inference and gets result.
            Siamese.Outputs outputs = model.process(inputFeature0, inputFeature1);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

            float[] confidences = outputFeature0.getFloatArray();
            // find the index of the class with the biggest confidence.
            DecimalFormat decimalFormat = new DecimalFormat("#.###");
            if (confidences[0] >= 0.5){
                tvResult.setText("SAME, with the probability of:" + decimalFormat.format(confidences[0]));
                tvResult.setTextColor(Color.parseColor("#22bb22"));
                tvResult.setVisibility(View.VISIBLE);
            } else{
                tvResult.setText("DIFFERENT, with the probability of:" + decimalFormat.format(confidences[0]));
                tvResult.setTextColor(Color.parseColor("#bb2222"));
                tvResult.setVisibility(View.VISIBLE);
            }

//            int maxPos = 0;
//            float maxConfidence = 0;
//            for (int i = 0; i < confidences.length; i++) {
//                if (confidences[i] > maxConfidence) {
//                    maxConfidence = confidences[i];
//                    maxPos = i;
//                }
//            }
//            String[] classes = {"Apple", "Banana", "Orange"};
//            result.setText(classes[maxPos]);

            // Releases model resources if no longer used.
            model.close();
        } catch (IOException e) {
            // TODO Handle the exception
            Toast.makeText(getApplicationContext(), "ridi", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if(resultCode == RESULT_OK){
            if(requestCode == 3){
                Bitmap image = (Bitmap) data.getExtras().get("data");
                userImageView.setImageBitmap(image);
                int dimension = Math.min(image.getWidth(), image.getHeight());
                image = ThumbnailUtils.extractThumbnail(image, dimension, dimension);

                image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false);

                dimension = Math.min(passport_image.getWidth(), passport_image.getHeight());
                passport_image = ThumbnailUtils.extractThumbnail(passport_image, dimension, dimension);
                passport_image = Bitmap.createScaledBitmap(passport_image, imageSize, imageSize, false);
                classifyImage(passport_image,image);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

}
